package com.pulsesolar.app.modules.optimizer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Palette;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Calibração de sincronia para jogos de ritmo (SOLAR RHYTHM).
 *
 * Toca batidas geradas pelo app (AudioTrack) e coleta, durante toda a reprodução, os pares
 * quadro→tempo do relógio de apresentação do áudio (AudioTrack.getTimestamp). Ao final, ajusta
 * esse relógio por regressão e compara cada toque com o instante em que a batida saiu.
 * Toda a matemática fica em {@link RhythmMath}. O resultado inclui o atraso do áudio, do toque
 * na tela e o tempo de reação/antecipação da pessoa — é o que um jogo de ritmo precisa compensar.
 */
public class RhythmCalibrationActivity extends BaseActivity {

    private static final int BPM = 80;          // 750 ms entre batidas: janela maior para atrasos altos
    private static final int BEATS = 24;
    private static final int WARMUP = 4;
    private static final long TAIL_MS = 600;    // silêncio no fim do áudio
    private static final long GRACE_MS = 500;   // espera extra para o último toque
    private static final long POLL_MS = 20;

    private TextView txtStatus, txtVerdict, txtResult, tapPad;
    private Button btnStart, btnSave;
    private AudioTrack track;
    private int sampleRate;
    private long framesPerBeat, totalFrames;

    private final List<long[]> clockPairs = new ArrayList<>();
    private long lastClockFrame = -1;
    private final List<Long> taps = new ArrayList<>();
    private boolean running = false;
    private long startUptime, finishAt = -1;

    private RhythmMath.Analysis lastAnalysis;
    private boolean lastBt;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running || track == null) return;
            sampleClock();
            long now = SystemClock.uptimeMillis();
            txtStatus.setText("Batida " + heardBeat() + " de " + BEATS + "  •  toques registrados: " + taps.size()
                    + (heardBeat() <= WARMUP ? "  (aquecimento)" : ""));
            if (finishAt < 0 && track.getPlaybackHeadPosition() >= totalFrames) finishAt = now + GRACE_MS;
            long expectedMs = totalFrames * 1000L / sampleRate;
            if ((finishAt > 0 && now >= finishAt) || now - startUptime > expectedMs + 4000) {
                finishRun();
            } else {
                handler.postDelayed(this, POLL_MS);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rhythm_calibration);
        txtStatus = findViewById(R.id.txtStatus);
        txtVerdict = findViewById(R.id.txtVerdict);
        txtResult = findViewById(R.id.txtResult);
        tapPad = findViewById(R.id.tapPad);
        btnStart = findViewById(R.id.btnStart);
        btnSave = findViewById(R.id.btnSave);

        txtStatus.setText("Pronto.");
        txtVerdict.setText("");
        if (OptimizerStore.hasRhythmOffset(this)) {
            txtResult.setText("Calibração salva: " + signed(OptimizerStore.rhythmOffset(this)) + " ms (variação ±"
                    + OptimizerStore.rhythmSd(this) + " ms)"
                    + (OptimizerStore.rhythmBluetooth(this) ? ", com Bluetooth." : "."));
        } else {
            txtResult.setText("Nenhuma calibração salva.");
        }

        btnStart.setOnClickListener(v -> startRun());
        btnSave.setOnClickListener(v -> {
            RhythmMath.Analysis a = lastAnalysis;
            if (a == null || !a.valid) { // proteção extra: o botão já fica desativado
                toast("Calibração inválida não pode ser salva");
                btnSave.setEnabled(false);
                return;
            }
            OptimizerStore.saveRhythmOffset(this, (int) Math.round(a.offsetMs), (int) Math.round(a.sdMs), lastBt);
            toast("Offset salvo no perfil SOLAR RHYTHM");
            btnSave.setEnabled(false);
        });
        tapPad.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (running) {
                    // Mesmo relógio do AudioTimestamp (CLOCK_MONOTONIC).
                    long t = Build.VERSION.SDK_INT >= 34 ? e.getEventTimeNanos() : e.getEventTime() * 1_000_000L;
                    taps.add(t);
                }
                v.setPressed(true);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onPause() {
        if (running) {
            running = false;
            stopTrack();
            txtStatus.setText("Calibração interrompida.");
            showInvalid("Calibração interrompida antes do fim.", null);
            btnStart.setEnabled(true);
        } else {
            stopTrack();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopTrack();
        super.onDestroy();
    }

    // ------------------------------------------------------------ Áudio

    private static boolean bluetoothOutput(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_HEADSET
                    || t == AudioDeviceInfo.TYPE_BLE_SPEAKER) return true;
        }
        return false;
    }

    private short[] buildClicks() {
        short[] pcm = new short[(int) totalFrames];
        int clickLen = sampleRate * 30 / 1000; // 30 ms
        for (int b = 0; b < BEATS; b++) {
            double freq = (b % 4 == 0) ? 1500.0 : 1000.0;
            int start = (int) (b * framesPerBeat);
            for (int i = 0; i < clickLen && start + i < pcm.length; i++) {
                double env = 1.0 - (double) i / clickLen;
                pcm[start + i] = (short) (Math.sin(2 * Math.PI * freq * i / sampleRate) * env * 26000);
            }
        }
        return pcm;
    }

    private void startRun() {
        stopTrack();
        taps.clear();
        clockPairs.clear();
        lastClockFrame = -1;
        finishAt = -1;
        lastAnalysis = null;
        btnSave.setEnabled(false);
        txtVerdict.setText("");

        sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        if (sampleRate <= 0) sampleRate = 48000;
        framesPerBeat = (long) sampleRate * 60 / BPM;
        totalFrames = framesPerBeat * BEATS + sampleRate * TAIL_MS / 1000;
        short[] pcm = buildClicks();
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.length * 2)
                    .build();
            track.write(pcm, 0, pcm.length);
            track.play();
        } catch (RuntimeException e) {
            toast("Não foi possível iniciar o áudio: " + e.getClass().getSimpleName());
            stopTrack();
            return;
        }
        lastBt = bluetoothOutput(this);
        startUptime = SystemClock.uptimeMillis();
        running = true;
        btnStart.setEnabled(false);
        txtResult.setTextColor(Palette.TEXT);
        txtResult.setText(lastBt
                ? "Saída Bluetooth detectada: o resultado vale só para esse fone.\nToque ouvindo o som, não olhando a tela."
                : "Toque ouvindo o som, não olhando a tela. As 4 primeiras batidas são aquecimento.");
        handler.post(poll);
    }

    /** Guarda um par quadro→tempo quando o relógio de apresentação avança. */
    private void sampleClock() {
        AudioTimestamp ts = new AudioTimestamp();
        if (track.getTimestamp(ts) && ts.framePosition > 0 && ts.framePosition > lastClockFrame) {
            lastClockFrame = ts.framePosition;
            clockPairs.add(new long[]{ts.framePosition, ts.nanoTime});
        }
    }

    /** Batida que está sendo ouvida agora (1..BEATS), estimada pelo relógio do áudio quando disponível. */
    private int heardBeat() {
        long frame;
        if (!clockPairs.isEmpty()) {
            long[] last = clockPairs.get(clockPairs.size() - 1);
            frame = last[0] + (System.nanoTime() - last[1]) * sampleRate / 1_000_000_000L;
        } else {
            frame = track != null ? track.getPlaybackHeadPosition() : 0;
        }
        if (frame < 0) return 0;
        return (int) Math.max(0, Math.min(BEATS, frame / framesPerBeat + 1));
    }

    // ------------------------------------------------------------ Resultado

    private void finishRun() {
        running = false;
        stopTrack();
        btnStart.setEnabled(true);
        txtStatus.setText("Fim: " + taps.size() + " toques registrados em " + BEATS + " batidas.");

        RhythmMath.Clock clock = RhythmMath.fitClock(clockPairs, sampleRate);
        double[] beatNs = new double[BEATS];
        if (clock.ok) {
            for (int k = 0; k < BEATS; k++) beatNs[k] = clock.nanosAt(k * framesPerBeat);
        }
        RhythmMath.Analysis a = RhythmMath.analyze(taps, beatNs, WARMUP, clock);
        lastAnalysis = a;

        if (!clock.ok) {
            // Sem relógio confiável não há referência: não mostramos atraso nem variação.
            showInvalid("Não foi possível medir: " + clock.problem + ".",
                    "Toques registrados: " + taps.size() + "\n\nTente de novo. Se continuar, este aparelho não oferece a medição precisa; ajuste o offset do FNF manualmente.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Toques registrados: ").append(a.totalTaps).append('\n');
        sb.append("  • ").append(a.warmupTaps).append(" nas ").append(WARMUP).append(" batidas de aquecimento (descartados de propósito)\n");
        if (a.earlyTaps > 0) sb.append("  • ").append(a.earlyTaps).append(" antes da primeira batida\n");
        if (a.lateTaps > 0) sb.append("  • ").append(a.lateTaps).append(" depois da última batida\n");
        if (a.duplicateTaps > 0) sb.append("  • ").append(a.duplicateTaps).append(" duplicado(s) na mesma batida (vale o primeiro)\n");
        if (a.outlierTaps > 0) sb.append("  • ").append(a.outlierTaps).append(" fora do ritmo (descartado)\n");
        sb.append("Usados no cálculo: ").append(a.usedTaps).append(" de ").append(a.beatsAnalyzed).append(" batidas analisadas\n\n");

        if (!Double.isNaN(a.offsetMs)) {
            sb.append(String.format(Locale.getDefault(), "Atraso medido: %s ms (%s)%n",
                    signed((int) Math.round(a.offsetMs)),
                    a.offsetMs >= 0 ? "você toca depois do som" : "você toca antes do som"));
        }
        if (!Double.isNaN(a.sdMs)) {
            sb.append(String.format(Locale.getDefault(), "Variação entre toques: ±%.0f ms%n", a.sdMs));
            sb.append(String.format(Locale.getDefault(), "Margem do resultado: ±%.0f ms%n", a.marginMs));
        }
        sb.append(String.format(Locale.getDefault(), "Relógio do áudio: %d leituras, erro %.1f ms%n",
                clock.points, clock.rmsMs));
        sb.append("\nO valor inclui o atraso do som, do toque na tela e o seu tempo de reação.");

        if (a.valid) {
            if (OptimizerStore.hasRhythmOffset(this)) {
                int diff = (int) Math.round(a.offsetMs) - OptimizerStore.rhythmOffset(this);
                if (Math.abs(diff) > 40) {
                    sb.append(String.format(Locale.getDefault(),
                            "%n%nAtenção: difere %d ms da calibração salva. Se trocou de fone, é esperado; senão, repita.", Math.abs(diff)));
                }
            }
            sb.append(String.format(Locale.getDefault(),
                    "%n%nNo FNF, ajuste o offset/atraso de áudio começando por %d ms. Se as notas piorarem, use o sinal oposto (cada versão usa uma convenção).",
                    Math.round(a.offsetMs)));
            txtVerdict.setText("✔ CALIBRAÇÃO VÁLIDA");
            txtVerdict.setTextColor(Palette.GREEN);
            txtResult.setTextColor(Palette.TEXT);
            txtResult.setText(sb.toString());
            btnSave.setEnabled(true);
        } else {
            showInvalid("Motivo: " + String.join("; ", a.problems) + ".", sb.toString());
        }
    }

    private void showInvalid(String reason, String details) {
        txtVerdict.setText("✖ CALIBRAÇÃO INVÁLIDA — não pode ser salva\n" + reason);
        txtVerdict.setTextColor(Palette.RED);
        txtResult.setTextColor(Palette.DIM);
        txtResult.setText(details != null ? details + "\n\nRepita tocando em todas as batidas, no ritmo do som." : "");
        btnSave.setEnabled(false);
    }

    private static String signed(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    private void stopTrack() {
        handler.removeCallbacks(poll);
        if (track != null) {
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
            }
            track.release();
            track = null;
        }
    }
}
