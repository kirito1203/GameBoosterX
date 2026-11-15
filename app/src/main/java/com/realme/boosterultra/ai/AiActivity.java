package com.realme.boosterultra.ai;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JARVIS: assistente do app, com voz e controle do celular. */
public class AiActivity extends Activity {
    public static final String EXTRA_VOICE = "voice";
    public static final String EXTRA_ASK = "ask";
    private final int BG = MainActivity.BG, ACCENT = MainActivity.ACCENT, TXT = MainActivity.TXT,
            SUB = MainActivity.SUB, BAD = MainActivity.BAD, CARD = MainActivity.CARD, WARN = MainActivity.WARN;
    private static final Pattern ACTION = Pattern.compile("\\[ACAO:([^\\]]+)\\]");
    private static final int REQ_VOICE = 42;

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private Button send, mic;
    private TextView statusTv;
    private JSONArray history;
    private String aiName;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        AiClient.migrate(this);
        SharedPreferences p = ProfileManager.prefs(this);
        aiName = p.getString("ai_name", "Jarvis");
        try { history = new JSONArray(p.getString("ai_hist", "[]")); }
        catch (Exception e) { history = new JSONArray(); }

        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setSpeechRate(1.05f);
                ttsReady = true;
            }
        });

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, ins) -> {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return ins;
            });
        }

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        ImageView m = new ImageView(this);
        m.setImageResource(R.drawable.mascot);
        header.addView(m, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(dp(10), 0, 0, 0);
        ht.addView(tv(aiName.toUpperCase(Locale.ROOT), 18, ACCENT, true));
        statusTv = tv("● REC  CAM-07  ONLINE", 10, MainActivity.BAD, false);
        ht.addView(statusTv);
        header.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        Button clear = smallBtn("LIMPAR");
        clear.setOnClickListener(v -> {
            history = new JSONArray();
            save();
            list.removeAllViews();
            say("Memória da conversa apagada, senhor.", false);
        });
        header.addView(clear, new LinearLayout.LayoutParams(dp(80), dp(38)));
        root.addView(header);
        android.view.View line = new android.view.View(this);
        line.setBackgroundColor(MainActivity.a(0x55));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(10), dp(12), dp(10));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(MainActivity.BAR);
        mic = smallBtn("MIC");
        mic.setOnClickListener(v -> listen());
        bar.addView(mic, new LinearLayout.LayoutParams(dp(52), dp(48)));
        input = new EditText(this);
        input.setHint("> fale com o " + aiName + "_");
        input.setHintTextColor(SUB);
        input.setTextColor(TXT);
        input.setTextSize(15);
        input.setTypeface(Typeface.MONOSPACE);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(round(CARD, 6, MainActivity.a(0x66)));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
        ilp.leftMargin = dp(8);
        bar.addView(input, ilp);
        send = smallBtn(">");
        send.setTextColor(BG);
        send.setBackground(round(ACCENT, 6, 0));
        send.setTextSize(20);
        send.setOnClickListener(v -> sendText(input.getText().toString(), false));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(52), dp(48));
        slp.leftMargin = dp(8);
        bar.addView(send, slp);
        root.addView(bar);

        frame.addView(new ScanlineView(this), new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        for (int i = 0; i < history.length(); i++) {
            JSONObject o = history.optJSONObject(i);
            if (o == null) continue;
            boolean user = "user".equals(o.optString("role"));
            bubble((user ? "VOCÊ: " : aiName.toUpperCase(Locale.ROOT) + ": ") + o.optString("content"), user, user ? TXT : ACCENT);
        }
        if (history.length() == 0) {
            say(AiClient.hasBrain(this)
                    ? "Sistemas online, senhor. Posso conversar, dar o status do aparelho e controlar o celular. "
                    + "Diga \"bom dia\" para um resumo ou \"capacidade máxima\" para o Ultra Turbo."
                    : "Senhor, estou sem cérebro conectado. Coloque uma chave na aba JARVIS. "
                    + "Mesmo assim já respondo \"bom dia\", \"status\" e \"capacidade máxima\".", false);
        }
        if (getIntent().getBooleanExtra(EXTRA_VOICE, false)) h.postDelayed(this::listen, 400);
        String ask = getIntent().getStringExtra(EXTRA_ASK);
        if (ask != null && !ask.isEmpty()) h.postDelayed(() -> sendText(ask, false), 300);
    }

    // ------------------------------------------------------------------ voz

    private void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Pode falar, senhor");
        if (tts != null) tts.stop();
        try {
            startActivityForResult(i, REQ_VOICE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Instale ou ative o app Google para usar a voz", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_VOICE || res != RESULT_OK || data == null) return;
        ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (r != null && !r.isEmpty()) sendText(r.get(0), true);
    }

    private void speak(String s) {
        if (!ttsReady || !ProfileManager.prefs(this).getBoolean("jarvis_voice", true)) return;
        String clean = s.replaceAll("[*#_`>\\[\\]]", " ");
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis");
    }

    // ------------------------------------------------------------------ conversa

    private void sendText(String raw, boolean byVoice) {
        String text = raw.trim();
        if (text.isEmpty()) return;
        input.setText("");
        bubble("VOCÊ: " + text, true, TXT);

        String local = localCommand(text);
        if (local != null) {
            remember("user", text);
            remember("assistant", local);
            say(local, true);
            return;
        }

        remember("user", text);
        final TextView wait = bubble(aiName.toUpperCase(Locale.ROOT) + ": processando_", false, SUB);
        send.setEnabled(false);
        mic.setEnabled(false);
        new Thread(() -> {
            try {
                AiClient.Reply reply = AiClient.chat(this, history);
                List<String> actions = new ArrayList<>();
                Matcher mt = ACTION.matcher(reply.text);
                while (mt.find()) actions.add(mt.group(1));
                String clean = ACTION.matcher(reply.text).replaceAll("").trim();
                if (clean.isEmpty()) clean = "Feito, senhor.";
                final String shown = clean;
                h.post(() -> {
                    wait.setText(aiName.toUpperCase(Locale.ROOT) + ": " + shown);
                    wait.setTextColor(ACCENT);
                    statusTv.setText("● REC  CAM-07  via " + reply.from);
                    remember("assistant", shown);
                    speak(shown);
                    for (String a : actions) bubble("[SISTEMA] " + PhoneControl.run(this, a), false, WARN);
                    send.setEnabled(true);
                    mic.setEnabled(true);
                });
            } catch (Exception e) {
                final String err = e.getMessage() == null ? e.toString() : e.getMessage();
                h.post(() -> {
                    wait.setText("[ERRO] " + err);
                    wait.setTextColor(BAD);
                    send.setEnabled(true);
                    mic.setEnabled(true);
                });
            }
        }).start();
    }

    /** Comandos que o Jarvis resolve na hora, sem internet. */
    private String localCommand(String text) {
        String t = PhoneControl.norm(text);
        if (t.matches(".*\\b(bom dia|boa tarde|boa noite|resumo|status|relatorio)\\b.*") && t.length() < 40) {
            return summary(t);
        }
        if (t.contains("capacidade maxima") || t.contains("super turbo") || t.contains("ultra turbo") || t.contains("potencia maxima")
                || t.contains("turbo maximo")) {
            int ok = 0, total = 0;
            StringBuilder bad = new StringBuilder();
            for (String[] it : TurboManager.run(this)) {
                total++;
                if ("ok".equals(it[0])) ok++;
                else if (bad.length() < 160) bad.append(" ").append(it[1]).append(".");
            }
            return "Capacidade máxima ativada, senhor. " + ok + " de " + total + " sistemas no verde."
                    + (bad.length() > 0 ? " Atenção:" + bad : " Tudo pronto para jogar.");
        }
        return null;
    }

    private String summary(String t) {
        Date now = new Date();
        int hour = Integer.parseInt(new SimpleDateFormat("H", Locale.getDefault()).format(now));
        String greet = hour < 12 ? "Bom dia" : hour < 18 ? "Boa tarde" : "Boa noite";
        String date = new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR")).format(now);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
        long[] r = DeviceInfo.ram(this);
        int ru = DeviceInfo.pct(r[0] - r[1], r[0]);
        long[] s = DeviceInfo.storage();
        DeviceInfo.Battery b = DeviceInfo.battery(this);
        int th = DeviceInfo.thermalStatus(this);
        String state = (b.temp >= 42 || th >= 2) ? "O aparelho está quente, recomendo uma pausa."
                : ru > 85 ? "A memória está cheia, posso ativar a capacidade máxima se quiser."
                : "Todos os sistemas estão estáveis.";
        return greet + ", senhor. São " + time + " de " + date + ". "
                + "Bateria em " + b.level + "%" + (b.charging ? ", carregando" : "")
                + String.format(Locale.US, ", temperatura de %.0f graus. ", b.temp)
                + "Memória em " + ru + "% de uso e " + DeviceInfo.fmt(s[1]) + " livres de armazenamento. " + state;
    }

    private void say(String s, boolean voice) {
        bubble(aiName.toUpperCase(Locale.ROOT) + ": " + s, false, ACCENT);
        if (voice) speak(s);
    }

    private void remember(String role, String content) {
        try { history.put(new JSONObject().put("role", role).put("content", content)); } catch (Exception ignored) {}
        save();
    }

    private void save() {
        JSONArray keep = new JSONArray();
        int start = Math.max(0, history.length() - 60);
        for (int i = start; i < history.length(); i++) keep.put(history.opt(i));
        history = keep;
        ProfileManager.prefs(this).edit().putString("ai_hist", history.toString()).apply();
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI

    private TextView bubble(String s, boolean user, int color) {
        TextView t = tv(s, 14, color, false);
        t.setTextIsSelectable(true);
        t.setPadding(dp(12), dp(9), dp(12), dp(9));
        t.setBackground(user ? round(MainActivity.BAR, 6, 0x33FFFFFF) : round(CARD, 6, MainActivity.a(0x44)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(8);
        lp.gravity = user ? Gravity.END : Gravity.START;
        if (user) lp.leftMargin = dp(40); else lp.rightMargin = dp(40);
        list.addView(t, lp);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        return t;
    }

    private Button smallBtn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setStateListAnimator(null);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setTextColor(ACCENT);
        b.setBackground(round(0x00000000, 4, ACCENT));
        return b;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(2), 1f);
        t.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }
}
