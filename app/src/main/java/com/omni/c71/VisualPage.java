package com.omni.c71;

import android.app.WallpaperManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Aba Visual: tela inicial, cores, ícones e papel de parede. */
class VisualPage extends Page {

    static final int REQ_WALL = 41;
    static final int REQ_HOME = 42;
    static final int[] COLORS = {0xFF4D8DFF, 0xFF8B5CF6, 0xFFEC4899, 0xFFFF5470,
            0xFFFF8A3D, 0xFFFFC857, 0xFF34D399, 0xFF22D3EE};

    VisualPage(MainActivity a) { super(a); }

    @Override
    void fill(LinearLayout box) {
        // ---- Tela inicial
        LinearLayout k = card(box, "🏠 Tela inicial OMNI");
        boolean def = isDefaultHome();
        k.addView(Ui.note(a, def
                ? "✅ A OMNI Home já é a sua tela inicial. Aperte o botão Home para ver."
                : "A OMNI Home substitui a tela inicial da realme. Toque em \"Definir como padrão\" e escolha OMNI Home. Dá para voltar quando quiser."));
        k.addView(Ui.actions(a, new String[]{def ? "Trocar tela inicial" : "Definir como padrão", "Ver agora"}, i -> {
            if (i == 0) askHome();
            else a.startActivity(new Intent(a, LauncherActivity.class));
        }));

        // ---- Cor de destaque
        LinearLayout c = card(box, "🎨 Cor de destaque");
        c.addView(Ui.note(a, "Muda a cor do painel, dos botões e da tela inicial."));
        LinearLayout row = Ui.hbox(a);
        int cur = Prefs.accent(a);
        for (int col : COLORS) {
            View dot = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            if (col == cur) g.setStroke(Ui.dp(a, 3), Color.WHITE);
            dot.setBackground(g);
            dot.setOnClickListener(v -> {
                Prefs.putInt(a, "accent", col);
                a.restart();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 34), 1f);
            lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
            row.addView(dot, lp);
        }
        c.addView(row);

        // ---- Ícones e grade
        LinearLayout g = card(box, "🔲 Ícones e grade");
        g.addView(Ui.label(a, "Colunas de apps"));
        int cols = Prefs.cols(a);
        g.addView(Ui.chips(a, new String[]{"3", "4", "5", "6"}, cols - 3, i -> Prefs.putInt(a, "cols", i + 3)));

        TextView sizeLbl = Ui.label(a, "Tamanho dos ícones: " + Prefs.iconDp(a) + "dp");
        g.addView(sizeLbl);
        SeekBar size = Ui.seek(a, 36, Prefs.iconDp(a) - 36);
        size.setOnSeekBarChangeListener(Ui.onRelease(
                p -> sizeLbl.setText("Tamanho dos ícones: " + (p + 36) + "dp"),
                p -> Prefs.putInt(a, "iconDp", p + 36)));
        g.addView(size);

        g.addView(Ui.sw(a, "Mostrar nome dos apps", Prefs.labels(a), (b, on) -> Prefs.putBool(a, "labels", on)));
        g.addView(Ui.sw(a, "Moldura colorida nos ícones", Prefs.frame(a), (b, on) -> Prefs.putBool(a, "frame", on)));
        g.addView(Ui.sw(a, "Relógio grande no topo", Prefs.clock(a), (b, on) -> Prefs.putBool(a, "clock", on)));

        TextView dimLbl = Ui.label(a, "Escurecer papel de parede: " + Prefs.dim(a) + "%");
        g.addView(dimLbl);
        SeekBar dim = Ui.seek(a, 80, Prefs.dim(a));
        dim.setOnSeekBarChangeListener(Ui.onRelease(
                p -> dimLbl.setText("Escurecer papel de parede: " + p + "%"),
                p -> Prefs.putInt(a, "dim", p)));
        g.addView(dim);

        // ---- Papel de parede
        LinearLayout w = card(box, "🖼️ Papel de parede");
        w.addView(Ui.note(a, "Aplica na tela inicial e na tela de bloqueio."));
        w.addView(Ui.btn(a, "Escolher imagem da galeria", Prefs.accent(a), v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            try {
                a.startActivityForResult(Intent.createChooser(i, "Escolha a imagem"), REQ_WALL);
            } catch (Throwable t) {
                Ui.toast(a, "Nenhuma galeria encontrada.");
            }
        }));
        w.addView(Ui.actions(a, new String[]{"Gradiente da cor", "Neon escuro", "Do sistema"}, i -> {
            if (i == 0) makeWallpaper(false);
            else if (i == 1) makeWallpaper(true);
            else safeStart(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Papel de parede"));
        }));

        // ---- Apps ocultos
        Set<String> hidden = Prefs.hidden(a);
        LinearLayout h = card(box, "🙈 Apps ocultos (" + hidden.size() + ")");
        if (hidden.isEmpty()) {
            h.addView(Ui.note(a, "Nenhum. Na tela inicial, segure um app e toque em \"Ocultar\"."));
        } else {
            PackageManager pm = a.getPackageManager();
            for (String key : hidden) {
                String name = key;
                ComponentName cn = ComponentName.unflattenFromString(key);
                if (cn != null) {
                    try {
                        ActivityInfo ai = pm.getActivityInfo(cn, 0);
                        name = ai.loadLabel(pm).toString();
                    } catch (Throwable ignored) { }
                }
                LinearLayout r = Ui.hbox(a);
                r.addView(Ui.text(a, name, 14, Ui.TEXT, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                TextView show = Ui.btn(a, "Mostrar", Ui.CARD2, v -> {
                    Set<String> s = Prefs.hidden(a);
                    s.remove(key);
                    Prefs.setHidden(a, s);
                    refresh();
                });
                r.addView(show, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
                h.addView(r);
            }
        }

        // ---- Atalhos visuais do sistema
        LinearLayout s = card(box, "✨ Mais personalização da realme");
        s.addView(Ui.note(a, "Telas oficiais do sistema para fontes, tela de bloqueio e temas."));
        s.addView(Ui.actions(a, new String[]{"Tela", "Bloqueio", "Temas"}, i -> {
            if (i == 0) safeStart(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
            else if (i == 1) safeStart(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            else {
                Intent t = a.getPackageManager().getLaunchIntentForPackage("com.heytap.themestore");
                if (t == null) t = a.getPackageManager().getLaunchIntentForPackage("com.oplus.themestore");
                if (t != null) safeStart(t);
                else Ui.toast(a, "Loja de temas da realme não encontrada.");
            }
        }));
    }

    boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = a.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && a.getPackageName().equals(ri.activityInfo.packageName);
    }

    void askHome() {
        if (Build.VERSION.SDK_INT >= 29 && !isDefaultHome()) {
            try {
                RoleManager rm = a.getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME);
                    return;
                }
            } catch (Throwable ignored) { }
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Throwable t) {
            safeStart(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    void makeWallpaper(boolean neon) {
        Ui.toast(a, "Criando papel de parede…");
        final int acc = Prefs.accent(a);
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        final int wpx = Math.max(dm.widthPixels, 720);
        final int hpx = Math.max(dm.heightPixels, 1280);
        new Thread(() -> {
            String msg;
            try {
                Bitmap bmp = Bitmap.createBitmap(wpx, hpx, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(bmp);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                if (!neon) {
                    int dark = Color.rgb(Color.red(acc) / 6, Color.green(acc) / 6, Color.blue(acc) / 6);
                    p.setShader(new LinearGradient(0, 0, wpx, hpx, acc, dark, Shader.TileMode.CLAMP));
                    cv.drawRect(0, 0, wpx, hpx, p);
                } else {
                    cv.drawColor(Ui.BG);
                    p.setShader(new RadialGradient(wpx * 0.2f, hpx * 0.25f, wpx * 0.9f,
                            (acc & 0x00FFFFFF) | 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
                    cv.drawRect(0, 0, wpx, hpx, p);
                    p.setShader(new RadialGradient(wpx * 0.9f, hpx * 0.85f, wpx * 0.8f,
                            0x668B5CF6, 0x00000000, Shader.TileMode.CLAMP));
                    cv.drawRect(0, 0, wpx, hpx, p);
                }
                WallpaperManager.getInstance(a).setBitmap(bmp);
                msg = "Papel de parede aplicado!";
            } catch (Throwable t) {
                msg = "Não foi possível aplicar: " + t.getMessage();
            }
            final String m = msg;
            a.runOnUiThread(() -> Ui.toast(a, m));
        }).start();
    }

    @Override
    void onResult(int req, int res, Intent data) {
        if (req == REQ_HOME) {
            refresh();
            return;
        }
        if (req != REQ_WALL || res != MainActivity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        Ui.toast(a, "Aplicando imagem…");
        new Thread(() -> {
            String msg;
            try (InputStream in = a.getContentResolver().openInputStream(uri)) {
                WallpaperManager.getInstance(a).setStream(in);
                msg = "Papel de parede aplicado!";
            } catch (Throwable t) {
                msg = "Não foi possível aplicar: " + t.getMessage();
            }
            final String m = msg;
            a.runOnUiThread(() -> Ui.toast(a, m));
        }).start();
    }
}
