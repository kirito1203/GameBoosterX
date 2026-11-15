package com.pulsesolar.app.core;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

/** Verificação das permissões especiais que o usuário concede em Configurações. */
public final class Perms {

    private Perms() {
    }

    public static boolean hasUsageAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return c.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static boolean hasDndAccess(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    public static boolean canWriteSettings(Context c) {
        return Settings.System.canWrite(c);
    }
}
