package com.omni.c71;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Aba Shizuku: status, passo a passo sem PC e terminal ADB. */
class ShizukuPage extends Page {

    static String lastCmd = "";
    static String lastOut = "";

    ShizukuPage(MainActivity a) { super(a); }

    @Override
    void fill(LinearLayout box) {
        boolean inst = installed(Shell.SHIZUKU_PKG);
        boolean run = Shell.running();
        boolean ok = Shell.ready();

        // ---- Status
        LinearLayout st = card(box, "⚡ Status do Shizuku");
        st.addView(line(inst, "App Shizuku instalado"));
        st.addView(line(run, "Serviço rodando" + (run ? " (modo " + Shell.mode() + ")" : "")));
        st.addView(line(ok, "OMNI com permissão"));
        if (!inst) {
            st.addView(Ui.btn(a, "Baixar Shizuku na Play Store", Prefs.accent(a), v -> {
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Shell.SHIZUKU_PKG)));
                } catch (Throwable t) {
                    safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + Shell.SHIZUKU_PKG)));
                }
            }));
        } else if (!run) {
            st.addView(Ui.btn(a, "Abrir Shizuku para iniciar", Prefs.accent(a), v -> {
                Intent i = a.getPackageManager().getLaunchIntentForPackage(Shell.SHIZUKU_PKG);
                if (i != null) safeStart(i);
            }));
        } else if (!ok) {
            st.addView(Ui.btn(a, "Liberar OMNI no Shizuku", Ui.YELLOW, v -> Shell.request(7)));
        } else {
            st.addView(Ui.note(a, "Tudo pronto! As funções avançadas das abas Sistema e Apps estão liberadas."));
        }
        st.addView(Ui.btn(a, "Atualizar status", Ui.CARD2, v -> a.refreshAll()));

        // ---- Passo a passo
        LinearLayout how = card(box, "📖 Como ativar (sem PC, sem root)");
        how.addView(Ui.note(a,
                "1. Instale o Shizuku (botão acima).\n\n"
                        + "2. Ative o modo desenvolvedor: Configurações › Sobre o dispositivo › Versão › toque 7 vezes em \"Número da versão\".\n\n"
                        + "3. Conecte em qualquer Wi‑Fi.\n\n"
                        + "4. Abra o Shizuku › \"Iniciar via depuração sem fio\" › \"Parear\". Ele manda uma notificação.\n\n"
                        + "5. Em Opções do desenvolvedor › Depuração por Wi‑Fi › \"Parear dispositivo com código\". Digite o código na notificação do Shizuku.\n\n"
                        + "6. Volte ao Shizuku e toque em \"Iniciar\". Depois volte aqui e toque em \"Liberar OMNI\".\n\n"
                        + "⚠️ Quando o celular reiniciar, é só repetir o passo 6."));
        how.addView(Ui.actions(a, new String[]{"Sobre o celular", "Desenvolvedor"}, i -> {
            if (i == 0) safeStart(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
            else safeStart(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        }));

        // ---- Terminal
        LinearLayout t = card(box, "💻 Terminal ADB");
        if (!ok) {
            t.addView(Ui.note(a, "🔒 Disponível quando o Shizuku estiver ativo."));
            return;
        }
        t.addView(Ui.note(a, "Roda comandos com permissão ADB. Só use comandos que você entende."));
        EditText cmd = Ui.input(a, "Ex: wm density");
        cmd.setTypeface(Typeface.MONOSPACE);
        cmd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        cmd.setText(lastCmd);
        t.addView(cmd);

        final String[][] presets = {
                {"DPI", "wm density"}, {"Resolução", "wm size"}, {"Bateria", "dumpsys battery"},
                {"Congelados", "pm list packages -d"}, {"Memória", "head -n 5 /proc/meminfo"},
                {"Temperatura", "dumpsys thermalservice | grep -i 'temperature{' | head -n 12"}};
        t.addView(Ui.actions(a, new String[]{presets[0][0], presets[1][0], presets[2][0]}, i -> cmd.setText(presets[i][1])));
        t.addView(Ui.actions(a, new String[]{presets[3][0], presets[4][0], presets[5][0]}, i -> cmd.setText(presets[i + 3][1])));

        TextView out = Ui.text(a, lastOut.isEmpty() ? "A saída aparece aqui." : lastOut, 12, 0xFFB8F5D0, false);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        out.setBackground(Ui.round(0xFF05080E, Ui.dp(a, 12)));
        int p = Ui.dp(a, 12);
        out.setPadding(p, p, p, p);

        t.addView(Ui.actions(a, new String[]{"▶ Executar", "📋 Copiar saída"}, i -> {
            if (i == 1) {
                ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("OMNI", out.getText()));
                Ui.toast(a, "Copiado");
                return;
            }
            String c = cmd.getText().toString().trim();
            if (c.isEmpty()) return;
            lastCmd = c;
            out.setText("Executando…");
            Shell.run(c, (good, res) -> {
                lastOut = (good ? "" : "[erro]\n") + (res.isEmpty() ? "(sem saída)" : res);
                out.setText(lastOut);
            });
        }));
        t.addView(out);
    }

    TextView line(boolean on, String s) {
        TextView t = Ui.text(a, (on ? "✅  " : "❌  ") + s, 14, on ? Ui.TEXT : Ui.SUB, false);
        t.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 3));
        return t;
    }
}
