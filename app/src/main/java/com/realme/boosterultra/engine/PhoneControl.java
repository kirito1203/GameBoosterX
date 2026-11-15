package com.realme.boosterultra.engine;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.provider.Settings;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Ações que a IA (e o app) podem executar no celular. */
public final class PhoneControl {
    private PhoneControl() {}

    public static String run(Activity a, String action) {
        String act = action.trim();
        String arg = "";
        int i = act.indexOf(':');
        if (i >= 0) { arg = act.substring(i + 1).trim(); act = act.substring(0, i).trim(); }
        act = act.toLowerCase(Locale.ROOT);
        try {
            switch (act) {
                case "lanterna_on": return torch(a, true) ? "lanterna ligada" : "lanterna indisponível";
                case "lanterna_off": return torch(a, false) ? "lanterna desligada" : "lanterna indisponível";
                case "perfil_jogo": return profile(a, 0);
                case "perfil_economia": return profile(a, 1);
                case "perfil_normal": return profile(a, 2);
                case "super_turbo": {
                    int ok = 0, total = 0;
                    for (String[] it : TurboManager.run(a)) { total++; if ("ok".equals(it[0])) ok++; }
                    return "SUPER TURBO ativado (" + ok + " de " + total + " itens no verde)";
                }
                case "liberar_ram": return Optimizer.killBackground(a, null) + " apps em segundo plano encerrados";
                case "brilho": {
                    if (!Settings.System.canWrite(a)) return "falta a permissão Modificar configurações";
                    int v = clamp(arg);
                    Settings.System.putInt(a.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
                    Settings.System.putInt(a.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, v * 255 / 100));
                    return "brilho em " + v + "%";
                }
                case "volume": {
                    AudioManager am = (AudioManager) a.getSystemService(Activity.AUDIO_SERVICE);
                    int v = clamp(arg);
                    int st = AudioManager.STREAM_MUSIC;
                    am.setStreamVolume(st, Math.round(am.getStreamMaxVolume(st) * v / 100f), 0);
                    return "volume em " + v + "%";
                }
                case "abrir": case "jogar": {
                    String pkg = findApp(a, arg);
                    if (pkg == null) return "não achei o app \"" + arg + "\"";
                    if (act.equals("jogar")) { ProfileManager.start(a, pkg); return "Modo Jogo + " + arg; }
                    Intent l = a.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (l == null) return "não consegui abrir " + arg;
                    a.startActivity(l);
                    return "abrindo " + arg;
                }
                case "wifi": a.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); return "abrindo Wi-Fi";
                case "bluetooth": a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return "abrindo Bluetooth";
                case "bateria": a.startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)); return "abrindo Bateria";
            }
        } catch (Exception e) {
            return "não deu: " + act + " (o sistema bloqueou)";
        }
        return "ação desconhecida: " + act;
    }

    private static int clamp(String s) {
        try { return Math.max(0, Math.min(100, Integer.parseInt(s.replaceAll("[^0-9]", "")))); }
        catch (Exception e) { return 50; }
    }

    public static boolean torch(Activity a, boolean on) {
        try {
            CameraManager cm = (CameraManager) a.getSystemService(Activity.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                if (Boolean.TRUE.equals(cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) {
                    cm.setTorchMode(id, on);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static String profile(Activity a, int which) {
        if (!Settings.System.canWrite(a)) return "falta a permissão Modificar configurações";
        android.content.ContentResolver cr = a.getContentResolver();
        if (which == 0) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 255);
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 600000);
            Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
            Settings.System.putInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, 0);
            return "perfil JOGO aplicado";
        } else if (which == 1) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 50);
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000);
            Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
            return "perfil ECONOMIA aplicado";
        }
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 1);
        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 60000);
        Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1);
        Settings.System.putInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, 1);
        return "perfil NORMAL aplicado";
    }

    public static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    public static String findApp(Activity a, String name) {
        String q = norm(name);
        if (q.isEmpty()) return null;
        PackageManager pm = a.getPackageManager();
        List<ResolveInfo> l = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        String partial = null;
        for (ResolveInfo ri : l) {
            String label = norm(String.valueOf(ri.loadLabel(pm)));
            if (label.equals(q)) return ri.activityInfo.packageName;
            if (partial == null && (label.contains(q) || (label.length() >= 3 && q.contains(label)))) partial = ri.activityInfo.packageName;
        }
        return partial;
    }
}
