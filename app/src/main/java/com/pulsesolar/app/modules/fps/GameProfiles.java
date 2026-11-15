package com.pulsesolar.app.modules.fps;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.pulsesolar.app.core.GameModeController;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Perfis por jogo com configurações seguras (apenas o que o Android permite:
 * Não Perturbe, brilho, tempo de tela, monitor de sessão e limite de temperatura).
 */
public final class GameProfiles {

    private static final String PREFS = "fps_profiles";

    public static final class Profile {
        public boolean dnd = true;
        public boolean bright = false;
        public int brightValue = 200;
        public boolean timeout = true;
        public boolean monitor = true;
        public int tempLimit = 42;

        public GameModeController.Options toOptions() {
            GameModeController.Options o = new GameModeController.Options();
            o.dnd = dnd;
            o.bright = bright;
            o.brightValue = brightValue;
            o.timeout = timeout;
            return o;
        }

        String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("dnd", dnd);
                o.put("bright", bright);
                o.put("bv", brightValue);
                o.put("timeout", timeout);
                o.put("monitor", monitor);
                o.put("limit", tempLimit);
                return o.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }

        static Profile fromJson(String s) {
            Profile p = new Profile();
            try {
                JSONObject o = new JSONObject(s);
                p.dnd = o.optBoolean("dnd", true);
                p.bright = o.optBoolean("bright", false);
                p.brightValue = o.optInt("bv", 200);
                p.timeout = o.optBoolean("timeout", true);
                p.monitor = o.optBoolean("monitor", true);
                p.tempLimit = o.optInt("limit", 42);
            } catch (JSONException ignored) {
            }
            return p;
        }
    }

    private GameProfiles() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean has(Context c, String pkg) {
        return sp(c).contains(pkg);
    }

    public static Profile get(Context c, String pkg) {
        String s = sp(c).getString(pkg, null);
        return s == null ? new Profile() : Profile.fromJson(s);
    }

    public static void save(Context c, String pkg, Profile p) {
        sp(c).edit().putString(pkg, p.toJson()).apply();
    }

    public static void delete(Context c, String pkg) {
        sp(c).edit().remove(pkg).apply();
    }

    /**
     * Aplica o perfil e abre o jogo.
     * @return mensagem para o usuário (avisos), ou null se tudo foi aplicado.
     */
    public static String play(Context c, String pkg, String label, Profile p) {
        StringBuilder warn = new StringBuilder();
        if (GameModeController.isActive(c)) GameModeController.restore(c);
        if (p.dnd || p.bright || p.timeout) {
            String err = GameModeController.activate(c, p.toOptions());
            if (err != null) warn.append("Modo Jogos não aplicado: ").append(err).append(". ");
        }
        if (p.monitor && !SessionService.running) {
            try {
                SessionService.start(c, pkg, label, p.tempLimit);
            } catch (RuntimeException e) {
                warn.append("O Android não permitiu iniciar o monitor. ");
            }
        }
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            warn.append("Não foi possível abrir o jogo.");
        } else {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(launch);
        }
        return warn.length() == 0 ? null : warn.toString().trim();
    }
}
