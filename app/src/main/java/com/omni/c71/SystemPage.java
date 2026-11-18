package com.omni.c71;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Aba Sistema: tela, som, animações, DPI, rede e atalhos. */
class SystemPage extends Page {

    static final Handler H = new Handler(Looper.getMainLooper());
    static Runnable pendingRevert;
    static boolean askedDnd = false;

    TextView dpiNote;

    SystemPage(MainActivity a) { super(a); }

    boolean canWrite() {
        return Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(a);
    }

    int getSys(String key, int def) {
        try { return Settings.System.getInt(a.getContentResolver(), key, def); } catch (Throwable t) { return def; }
    }

    float getGlobalF(String key, float def) {
        try { return Settings.Global.getFloat(a.getContentResolver(), key, def); } catch (Throwable t) { return def; }
    }

    /** Escreve em Settings.System pela permissão normal ou, se faltar, pelo Shizuku. */
    void setSys(String key, int val) {
        if (canWrite()) {
            try {
                Settings.System.putInt(a.getContentResolver(), key, val);
                return;
            } catch (Throwable ignored) { }
        }
        if (Shell.ready()) {
            Shell.run("settings put system " + key + " " + val, (ok, out) -> {
                if (!ok) Ui.toast(a, "Falhou: " + out);
            });
        } else {
            Ui.toast(a, "Libere \"Modificar configurações do sistema\" (botão no topo desta aba).");
        }
    }

    void sh(String cmd, String okMsg) {
        if (!needShizuku()) return;
        Shell.run(cmd, (ok, out) -> Ui.toast(a, ok ? okMsg : "Falhou: " + (out.isEmpty() ? "erro desconhecido" : out)));
    }

    @Override
    void fill(LinearLayout box) {
        boolean sh = Shell.ready();
        ContentResolver cr = a.getContentResolver();

        // ================= TELA
        LinearLayout k = card(box, "☀️ Tela");
        if (!canWrite() && !sh) {
            k.addView(Ui.note(a, "Para mudar brilho e tempo de tela, libere a permissão \"Modificar configurações do sistema\" para o OMNI."));
            k.addView(Ui.btn(a, "Liberar permissão", Prefs.accent(a), v -> safeStart(
                    new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + a.getPackageName())))));
        }
        boolean auto = getSys(Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1;
        k.addView(Ui.sw(a, "Brilho automático", auto, (b, on) -> setSys(Settings.System.SCREEN_BRIGHTNESS_MODE, on ? 1 : 0)));
        k.addView(Ui.label(a, "Brilho"));
        SeekBar br = Ui.seek(a, 255, getSys(Settings.System.SCREEN_BRIGHTNESS, 128));
        br.setOnSeekBarChangeListener(Ui.onRelease(null, p -> {
            setSys(Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            setSys(Settings.System.SCREEN_BRIGHTNESS, Math.max(1, p));
        }));
        k.addView(br);

        k.addView(Ui.label(a, "Desligar tela depois de"));
        final int[] tms = {15000, 30000, 60000, 120000, 300000, 600000};
        int curT = getSys(Settings.System.SCREEN_OFF_TIMEOUT, 30000);
        int sel = 0;
        for (int i = 0; i < tms.length; i++) if (Math.abs(tms[i] - curT) < Math.abs(tms[sel] - curT)) sel = i;
        k.addView(Ui.chips(a, new String[]{"15s", "30s", "1m", "2m", "5m", "10m"}, sel,
                i -> setSys(Settings.System.SCREEN_OFF_TIMEOUT, tms[i])));

        // ================= SOM
        LinearLayout s = card(box, "🔊 Som");
        AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        String[] names = {"Mídia / jogos", "Toque", "Notificações", "Alarme"};
        int[] streams = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING,
                AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM};
        for (int i = 0; i < names.length; i++) {
            final int st = streams[i];
            s.addView(Ui.label(a, names[i]));
            SeekBar vb = Ui.seek(a, am.getStreamMaxVolume(st), am.getStreamVolume(st));
            vb.setOnSeekBarChangeListener(Ui.onRelease(p -> {
                try {
                    am.setStreamVolume(st, p, 0);
                } catch (SecurityException e) {
                    askDnd();
                }
            }, null));
            s.addView(vb);
        }

        // ================= AVANÇADO (SHIZUKU)
        LinearLayout v = card(box, "🛠️ Ajustes avançados");
        if (!sh) {
            v.addView(Ui.note(a, "🔒 Animações, DPI, fonte, modo escuro, Wi‑Fi, dados e localização precisam do Shizuku ativo (sem root, sem PC)."));
            v.addView(Ui.btn(a, "Ativar Shizuku", Prefs.accent(a), x -> a.show(3)));
        } else {
            v.addView(Ui.label(a, "Velocidade das animações"));
            v.addView(Ui.note(a, "0.5x deixa o celular parecendo mais rápido. Desligado = sem animações."));
            float an = getGlobalF(Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            final String[] av = {"0", "0.5", "1", "1.5"};
            int asel = an == 0f ? 0 : an <= 0.5f ? 1 : an <= 1f ? 2 : 3;
            v.addView(Ui.chips(a, new String[]{"Desligado", "0.5x", "1x", "1.5x"}, asel, i -> sh(
                    "settings put global window_animation_scale " + av[i]
                            + "; settings put global transition_animation_scale " + av[i]
                            + "; settings put global animator_duration_scale " + av[i],
                    "Animações: " + (i == 0 ? "desligadas" : av[i] + "x"))));

            v.addView(Ui.label(a, "Tamanho da fonte"));
            float fs = a.getResources().getConfiguration().fontScale;
            final String[] fv = {"0.85", "1.0", "1.15", "1.3"};
            int fsel = fs < 0.93f ? 0 : fs < 1.08f ? 1 : fs < 1.22f ? 2 : 3;
            v.addView(Ui.chips(a, new String[]{"Pequena", "Normal", "Grande", "Enorme"}, fsel,
                    i -> sh("settings put system font_scale " + fv[i], "Fonte alterada")));

            v.addView(Ui.label(a, "Densidade da tela (DPI)"));
            dpiNote = Ui.note(a, "Lendo DPI atual…");
            v.addView(dpiNote);
            Shell.run("wm density", (ok, out) -> {
                if (dpiNote != null) dpiNote.setText(ok ? out.replace("Physical density", "Original")
                        .replace("Override density", "Atual") + "\nMenor número = mais coisas na tela." : "Não consegui ler o DPI.");
            });
            LinearLayout dr = Ui.hbox(a);
            EditText dpi = Ui.input(a, "Ex: 300");
            dpi.setInputType(InputType.TYPE_CLASS_NUMBER);
            dr.addView(dpi, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            TextView ap = Ui.btn(a, "Aplicar", Prefs.accent(a), x -> {
                int val;
                try { val = Integer.parseInt(dpi.getText().toString().trim()); } catch (Exception e) { val = 0; }
                if (val < 200 || val > 640) {
                    Ui.toast(a, "Use um valor entre 200 e 640.");
                    return;
                }
                applyDpi(val);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.setMargins(Ui.dp(a, 8), 0, 0, 0);
            dr.addView(ap, lp);
            v.addView(dr);
            v.addView(Ui.btn(a, "Voltar DPI original", Ui.CARD2, x -> sh("wm density reset", "DPI original restaurado")));

            v.addView(Ui.label(a, "Tema do sistema"));
            UiModeManager um = (UiModeManager) a.getSystemService(Context.UI_MODE_SERVICE);
            int night = um != null ? um.getNightMode() : UiModeManager.MODE_NIGHT_AUTO;
            int nsel = night == UiModeManager.MODE_NIGHT_NO ? 0 : night == UiModeManager.MODE_NIGHT_YES ? 1 : 2;
            final String[] nv = {"no", "yes", "auto"};
            v.addView(Ui.chips(a, new String[]{"Claro", "Escuro", "Automático"}, nsel,
                    i -> sh("cmd uimode night " + nv[i], "Tema alterado")));

            toggle(v, "📶 Wi‑Fi", "svc wifi enable", "svc wifi disable");
            toggle(v, "🔵 Bluetooth", "svc bluetooth enable", "svc bluetooth disable");
            toggle(v, "📱 Dados móveis", "svc data enable", "svc data disable");
            toggle(v, "📍 Localização", "cmd location set-location-enabled true", "cmd location set-location-enabled false");
            toggle(v, "🔌 Tela ligada carregando", "settings put global stay_on_while_plugged_in 7", "settings put global stay_on_while_plugged_in 0");
            toggle(v, "👆 Mostrar toques", "settings put system show_touches 1", "settings put system show_touches 0");
        }

        // ================= ATALHOS
        LinearLayout at = card(box, "🧭 Atalhos do sistema");
        at.addView(Ui.actions(a, new String[]{"Wi‑Fi", "Bluetooth", "Bateria"}, i -> {
            if (i == 0) safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS));
            else if (i == 1) safeStart(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            else safeStart(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
        }));
        at.addView(Ui.actions(a, new String[]{"Armazenam.", "Notificações", "Acessibil."}, i -> {
            if (i == 0) safeStart(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
            else if (i == 1) safeStart(new Intent("android.settings.NOTIFICATION_SETTINGS"));
            else safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }));
        at.addView(Ui.actions(a, new String[]{"Desenvolvedor", "Data/hora", "Tudo"}, i -> {
            if (i == 0) safeStart(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            else if (i == 1) safeStart(new Intent(Settings.ACTION_DATE_SETTINGS));
            else safeStart(new Intent(Settings.ACTION_SETTINGS));
        }));
    }

    void toggle(LinearLayout parent, String label, String onCmd, String offCmd) {
        LinearLayout r = Ui.hbox(a);
        r.setPadding(0, Ui.dp(a, 6), 0, 0);
        r.addView(Ui.text(a, label, 14, Ui.TEXT, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1.3f));
        LinearLayout btns = Ui.actions(a, new String[]{"Ligar", "Desligar"},
                i -> sh(i == 0 ? onCmd : offCmd, label.substring(label.indexOf(' ') + 1) + (i == 0 ? " ligado" : " desligado")));
        r.addView(btns, new LinearLayout.LayoutParams(0, Ui.WRAP, 2f));
        parent.addView(r);
    }

    void askDnd() {
        NotificationManager nm = (NotificationManager) a.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 23 && nm != null && !nm.isNotificationPolicyAccessGranted() && !askedDnd) {
            askedDnd = true;
            Ui.toast(a, "Para mudar esse volume, permita o acesso ao \"Não perturbe\" para o OMNI.");
            safeStart(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        }
    }

    /** Aplica o DPI e volta sozinho em 15s se o usuário não confirmar. */
    void applyDpi(int val) {
        if (!needShizuku()) return;
        Shell.run("wm density " + val, (ok, out) -> {
            if (!ok) {
                Ui.toast(a, "Falhou: " + out);
                return;
            }
            if (pendingRevert != null) H.removeCallbacks(pendingRevert);
            pendingRevert = () -> {
                pendingRevert = null;
                Shell.run("wm density reset", null);
            };
            H.postDelayed(pendingRevert, 15000);
            final int[] left = {15};
            AlertDialog d = Ui.dialog(a)
                    .setTitle("Manter DPI " + val + "?")
                    .setMessage("Se a tela ficou ruim, não faça nada: volta ao normal em 15s.")
                    .setCancelable(false)
                    .setPositiveButton("Manter", (x, y) -> {
                        if (pendingRevert != null) H.removeCallbacks(pendingRevert);
                        pendingRevert = null;
                        Ui.toast(a, "DPI " + val + " mantido. Reabra apps para ver tudo ajustado.");
                    })
                    .setNegativeButton("Desfazer", (x, y) -> {
                        if (pendingRevert != null) {
                            H.removeCallbacks(pendingRevert);
                            pendingRevert.run();
                        }
                    })
                    .create();
            d.show();
            H.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!d.isShowing()) return;
                    left[0]--;
                    if (left[0] <= 0 || pendingRevert == null) {
                        d.dismiss();
                        return;
                    }
                    d.setMessage("Se a tela ficou ruim, não faça nada: volta ao normal em " + left[0] + "s.");
                    H.postDelayed(this, 1000);
                }
            }, 1000);
        });
    }

    @Override
    void refresh() {
        dpiNote = null;
        super.refresh();
    }
}
