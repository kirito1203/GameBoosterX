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
 * Toca batidas geradas pelo app com AudioTrack e usa AudioTrack.getTimestamp() — o relógio
 * de apresentação do áudio do Android — para saber quando cada batida realmente saiu.
 * Compara com o horário de cada toque na tela. Resultado: atraso médio (ms) e variação.
 */
public class RhythmCalibrationActivity extends BaseActivity {

    private static final int BPM = 100;
    private static final int BEATS = 24;
    private static final int WARMUP = 4;
    private static final int MIN_TAPS = 8;

    private TextView txtStatus, txtResult, tapPad;
    private Button btnStart, btnSave;
    private AudioTrack track;
    private int sampleRate;
    private long framesPerBeat;

    // Referência do relógio de áudio: o quadro `refFrame` sai no instante `refNanos` (CLOCK_MONOTONIC)
    private volatile long refFrame = -1, refNanos = -1;
    private volatile boolean usingTimestamp = false;
    private final List<Long> taps = new ArrayList<>();
    private boolean running = false;
    private int lastOffset, lastSd;
    private boolean lastBt;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running || track == null) return;
            updateReference();
            long played = track.getPlaybackHeadPosition();
            int beat = (int) Math.min(BEATS, played / framesPerBeat);
            txtStatus.setText("Batida " + beat + " de " + BEATS + "  •  toques: " + taps.size()
                    + (usingTimestamp ? "" : "  (precisão reduzida)"));
            if (played >= framesPerBeat * BEATS) {
                finishRun();
            } else {
                handler.postDelayed(this, 50);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rhythm_calibration);
        txtStatus = findViewById(R.id.txtStatus);
        txtResult = findViewById(R.id.txtResult);
        tapPad = findViewById(R.id.tapPad);
        btnStart = findViewById(R.id.btnStart);
        btnSave = findViewById(R.id.btnSave);

        if (OptimizerStore.hasRhythmOffset(this)) {
            txtResult.setText("Offset salvo: " + OptimizerStore.rhythmOffset(this) + " ms (±"
                    + OptimizerStore.rhythmSd(this) + " ms)");
        } else {
            txtResult.setText("Nenhuma calibração salva.");
        }
        txtStatus.setText("Pronto.");

        btnStart.setOnClickListener(v -> startRun());
        btnSave.setOnClickListener(v -> {
            OptimizerStore.saveRhythmOffset(this, lastOffset, lastSd, lastBt);
            toast("Offset salvo no perfil SOLAR RHYTHM");
            btnSave.setEnabled(false);
        });
        tapPad.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (running) {
                    long t = Build.VERSION.SDK_INT >= 34 ? e.getEventTimeNanos() : e.getEventTime() * 1_000_000L;
                    taps.add(t);
                }
                v.setPressed(true);
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onPause() {
        stopTrack();
        if (running) {
            running = false;
            txtStatus.setText("Calibração interrompida.");
            btnStart.setEnabled(true);
        }
        super.onPause();
    }

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
        int total = (int) (framesPerBeat * BEATS + sampleRate / 2);
        short[] pcm = new short[total];
        int clickLen = sampleRate * 30 / 1000; // 30 ms
        for (int b = 0; b < BEATS; b++) {
            double freq = (b % 4 == 0) ? 1500.0 : 1000.0;
            int start = (int) (b * framesPerBeat);
            for (int i = 0; i < clickLen && start + i < total; i++) {
                double env = 1.0 - (double) i / clickLen;
                pcm[start + i] = (short) (Math.sin(2 * Math.PI * freq * i / sampleRate) * env * 26000);
            }
        }
        return pcm;
    }

    private void startRun() {
        stopTrack();
        taps.clear();
        refFrame = -1;
        refNanos = -1;
        usingTimestamp = false;
        btnSave.setEnabled(false);

        sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        if (sampleRate <= 0) sampleRate = 48000;
        framesPerBeat = (long) sampleRate * 60 / BPM;
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
        running = true;
        btnStart.setEnabled(false);
        txtResult.setText(lastBt ? "Saída Bluetooth detectada: o resultado vale só para esse fone." : "Toque no ritmo…");
        handler.post(poll);
    }

    /** Atualiza a referência quadro↔tempo pelo relógio de apresentação do áudio. */
    private void updateReference() {
        AudioTimestamp ts = new AudioTimestamp();
        if (track.getTimestamp(ts) && ts.framePosition > 0) {
            refFrame = ts.framePosition;
            refNanos = ts.nanoTime;
            usingTimestamp = true;
        } else if (!usingTimestamp) {
            // Alternativa sem o relógio de apresentação: posição de reprodução agora (ignora o atraso da saída).
            refFrame = track.getPlaybackHeadPosition();
            refNanos = System.nanoTime();
        }
    }

    private long beatNanos(int k) {
        return refNanos + (k * framesPerBeat - refFrame) * 1_000_000_000L / sampleRate;
    }

    private void finishRun() {
        running = false;
        stopTrack();
        btnStart.setEnabled(true);
        if (refNanos < 0) {
            txtResult.setText("O Android não informou o relógio do áudio. Tente de novo.");
            return;
        }
        long beatNs = 60_000_000_000L / BPM;
        List<Long> offsets = new ArrayList<>();
        for (long tap : taps) {
            long rel = tap - beatNanos(0);
            int k = (int) Math.round(rel / (double) beatNs);
            if (k < WARMUP || k >= BEATS) continue;
            long off = tap - beatNanos(k);
            if (Math.abs(off) < beatNs / 2) offsets.add(off);
        }
        if (offsets.size() < MIN_TAPS) {
            txtResult.setText("Poucos toques válidos (" + offsets.size() + "). Toque em todas as batidas depois das 4 primeiras e tente de novo.");
            txtResult.setTextColor(Palette.AMBER);
            return;
        }
        double sum = 0;
        for (long o : offsets) sum += o;
        double mean = sum / offsets.size();
        double var = 0;
        for (long o : offsets) var += (o - mean) * (o - mean);
        double sd = Math.sqrt(var / offsets.size());
        lastOffset = (int) Math.round(mean / 1e6);
        lastSd = (int) Math.round(sd / 1e6);

        String direction = lastOffset >= 0 ? "você toca depois do som" : "você toca antes do som";
        String quality = lastSd <= 25 ? "toques consistentes" : lastSd <= 50 ? "consistência média — repita para confirmar" : "toques irregulares — repita a calibração";
        txtResult.setTextColor(lastSd <= 50 ? Palette.GREEN : Palette.AMBER);
        txtResult.setText(String.format(Locale.getDefault(),
                "Atraso medido: %+d ms (%s)%nVariação: ±%d ms (%s)%nToques usados: %d%s%n%nNo FNF, ajuste o offset/atraso de áudio começando por %d ms. Se as notas ficarem piores, use o sinal oposto (cada versão usa uma convenção).",
                lastOffset, direction, lastSd, quality, offsets.size(),
                usingTimestamp ? "" : "\n(Precisão reduzida: o aparelho não forneceu o relógio de apresentação do áudio.)",
                lastOffset));
        btnSave.setEnabled(true);
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

    @Override
    protected void onDestroy() {
        stopTrack();
        super.onDestroy();
    }
}
