package com.gameboosterx.rog.core;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

/** Verificação e solicitação das autorizações especiais usadas pelo app. */
public final class Perms {

    private Perms() {}

    public static boolean overlay(Context c) {
        return Settings.canDrawOverlays(c);
    }

    public static boolean usageStats(Context c) {
        try {
            AppOpsManager aom = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName());
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return c.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean writeSettings(Context c) {
        return Settings.System.canWrite(c);
    }

    public static boolean dnd(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    public static boolean notifications(Context c) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean allFiles() {
        return Environment.isExternalStorageManager();
    }

    /** Concedida apenas via ADB: adb shell pm grant com.gameboosterx.rog android.permission.DUMP */
    public static boolean dump(Context c) {
        return c.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED;
    }

    // -------- Abrir telas de autorização --------
    public static void requestOverlay(Activity a) {
        open(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + a.getPackageName())));
    }

    public static void requestUsage(Activity a) {
        open(a, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    public static void requestWriteSettings(Activity a) {
        open(a, new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + a.getPackageName())));
    }

    public static void requestDnd(Activity a) {
        open(a, new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    public static void requestNotifications(Activity a) {
        if (Build.VERSION.SDK_INT >= 33) {
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
        }
    }

    public static void requestAllFiles(Activity a) {
        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + a.getPackageName()));
        if (!open(a, i)) open(a, new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
    }

    public static void openAppDetails(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        open(c, i);
    }

    /** Abre uma tela do sistema com tratamento de erro (fabricantes às vezes removem telas). */
    public static boolean open(Context c, Intent i) {
        try {
            if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(c, "Esta tela não está disponível neste aparelho.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public static boolean openSetting(Context c, String action) {
        return open(c, new Intent(action));
    }
}
