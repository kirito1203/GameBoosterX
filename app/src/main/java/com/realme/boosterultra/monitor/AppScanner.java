package com.realme.boosterultra.monitor;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** APP PERMISSION SCANNER e BACKGROUND MONITOR. */
public final class AppScanner {
    private AppScanner() {}

    public static final class AppPerm {
        public String pkg, label;
        public List<String> granted = new ArrayList<>();
        public int risk;
    }

    /** {permissão, nome amigável, peso} */
    private static final String[][] SENSITIVE = {
            {"android.permission.CAMERA", "Câmera", "3"},
            {"android.permission.RECORD_AUDIO", "Microfone", "3"},
            {"android.permission.ACCESS_FINE_LOCATION", "Localização exata", "3"},
            {"android.permission.ACCESS_BACKGROUND_LOCATION", "Localização em 2º plano", "4"},
            {"android.permission.READ_CONTACTS", "Contatos", "2"},
            {"android.permission.READ_SMS", "SMS", "4"},
            {"android.permission.READ_CALL_LOG", "Registro de chamadas", "4"},
            {"android.permission.READ_MEDIA_IMAGES", "Fotos", "2"},
            {"android.permission.READ_MEDIA_VIDEO", "Vídeos", "2"},
            {"android.permission.READ_EXTERNAL_STORAGE", "Arquivos", "2"},
            {"android.permission.BODY_SENSORS", "Sensores corporais", "2"},
            {"android.permission.READ_PHONE_STATE", "Estado do telefone", "1"},
            {"android.permission.POST_NOTIFICATIONS", "Notificações", "1"}};

    public static List<AppPerm> scanPermissions(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> pkgs = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            pkgs.add(ri.activityInfo.packageName);
        }
        Map<String, String[]> sens = new HashMap<>();
        for (String[] s : SENSITIVE) sens.put(s[0], s);
        List<AppPerm> out = new ArrayList<>();
        for (String p : pkgs) {
            if (p.equals(c.getPackageName())) continue;
            try {
                PackageInfo pi = pm.getPackageInfo(p, PackageManager.GET_PERMISSIONS);
                if (pi.requestedPermissions == null) continue;
                AppPerm ap = new AppPerm();
                ap.pkg = p;
                ap.label = String.valueOf(pm.getApplicationLabel(pi.applicationInfo));
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    String[] s = sens.get(pi.requestedPermissions[i]);
                    boolean granted = pi.requestedPermissionsFlags != null
                            && (pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                    if (s != null && granted) { ap.granted.add(s[1]); ap.risk += Integer.parseInt(s[2]); }
                }
                if (ap.risk > 0) out.add(ap);
            } catch (Exception ignored) {}
        }
        Collections.sort(out, (a, b) -> b.risk - a.risk);
        return out;
    }

    /** GameManager que rodaram serviços em segundo plano nas últimas 3 horas: {pacote -> vezes}. */
    public static List<String[]> background(Context c) {
        List<String[]> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 29) return out;
        android.app.usage.UsageStatsManager um = c.getSystemService(android.app.usage.UsageStatsManager.class);
        if (um == null) return out;
        long now = System.currentTimeMillis();
        android.app.usage.UsageEvents ev = um.queryEvents(now - 3 * 3600_000L, now);
        android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
        Map<String, Integer> count = new HashMap<>();
        while (ev != null && ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == android.app.usage.UsageEvents.Event.FOREGROUND_SERVICE_START) {
                String p = e.getPackageName();
                if (p.equals(c.getPackageName())) continue;
                Integer v = count.get(p);
                count.put(p, v == null ? 1 : v + 1);
            }
        }
        PackageManager pm = c.getPackageManager();
        for (Map.Entry<String, Integer> en : count.entrySet()) {
            String label = en.getKey();
            try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(label, 0))); } catch (Exception ignored) {}
            out.add(new String[]{en.getKey(), label, String.valueOf(en.getValue())});
        }
        Collections.sort(out, (a, b) -> Integer.parseInt(b[2]) - Integer.parseInt(a[2]));
        return out;
    }
}
