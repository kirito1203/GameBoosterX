package com.pulsesolar.app.modules.optimizer;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.storage.StorageManager;

import com.pulsesolar.app.core.Perms;
import com.pulsesolar.app.modules.fps.Session;
import com.pulsesolar.app.modules.fps.SessionStore;

import java.util.List;

/** Escolhas do usuário por perfil, calibração de ritmo e dados reais por jogo. */
public final class OptimizerStore {

    private static final String PREFS = "game_optimizer";

    private OptimizerStore() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String chosenPackage(Context c, String specId) {
        return sp(c).getString("pkg_" + specId, null);
    }

    public static void setChosenPackage(Context c, String specId, String pkg) {
        sp(c).edit().putString("pkg_" + specId, pkg).apply();
    }

    // ------------------------------------------------------------ Calibração de ritmo

    /** Versão do método de calibração; resultados de versões antigas (com defeito) são ignorados. */
    private static final int RHYTHM_METHOD_VERSION = 2;

    public static void saveRhythmOffset(Context c, int offsetMs, int sdMs, boolean bluetooth) {
        sp(c).edit().putInt("rhythm_v", RHYTHM_METHOD_VERSION).putInt("rhythm_offset", offsetMs).putInt("rhythm_sd", sdMs)
                .putBoolean("rhythm_bt", bluetooth).putLong("rhythm_when", System.currentTimeMillis()).apply();
    }

    public static boolean hasRhythmOffset(Context c) {
        return sp(c).contains("rhythm_offset") && sp(c).getInt("rhythm_v", 1) == RHYTHM_METHOD_VERSION;
    }

    public static int rhythmOffset(Context c) {
        return sp(c).getInt("rhythm_offset", 0);
    }

    public static int rhythmSd(Context c) {
        return sp(c).getInt("rhythm_sd", 0);
    }

    public static boolean rhythmBluetooth(Context c) {
        return sp(c).getBoolean("rhythm_bt", false);
    }

    // ------------------------------------------------------------ Espaço do jogo

    public static final class AppStorage {
        public long app, data, cache;

        public long total() {
            return app + data;
        }
    }

    /** Espaço real do app (exige acesso ao uso); null se indisponível. */
    public static AppStorage storageOf(Context c, String pkg) {
        if (pkg == null || !Perms.hasUsageAccess(c)) return null;
        try {
            StorageStatsManager ssm = (StorageStatsManager) c.getSystemService(Context.STORAGE_STATS_SERVICE);
            StorageStats st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle());
            AppStorage a = new AppStorage();
            a.app = st.getAppBytes();
            a.data = st.getDataBytes();
            a.cache = st.getCacheBytes();
            return a;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isInstalled(Context c, String pkg) {
        if (pkg == null) return false;
        try {
            c.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ------------------------------------------------------------ Estabilidade

    public static final class Stability {
        public int sessions;
        public long totalMs;
        public Session last;
        public float avgStability = -1f;
        public float maxTemp = Float.NaN;
        public int alerts;
    }

    /** Resume as sessões reais gravadas pelo Solar FPS Engine para este jogo. */
    public static Stability stability(Context c, String pkg) {
        Stability s = new Stability();
        if (pkg == null) return s;
        List<Session> all = SessionStore.list(c);
        float stabSum = 0;
        int stabN = 0;
        for (Session ses : all) {
            if (!pkg.equals(ses.pkg)) continue;
            s.sessions++;
            s.totalMs += ses.durationMs();
            s.alerts += ses.alerts.size();
            if (s.last == null) s.last = ses; // lista vem da mais recente para a mais antiga
            for (Session.Point p : ses.samples) {
                if (p.stability >= 0) {
                    stabSum += p.stability;
                    stabN++;
                }
                if (!Float.isNaN(p.tempC)) s.maxTemp = Float.isNaN(s.maxTemp) ? p.tempC : Math.max(s.maxTemp, p.tempC);
            }
        }
        if (stabN > 0) s.avgStability = stabSum / stabN;
        return s;
    }
}
