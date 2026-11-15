package com.realme.boosterultra.engine;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Encerra processos de apps em segundo plano (API oficial, sem root). */
public final class Optimizer {
    private Optimizer() {}

    public static int killBackground(Context c, String keep) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        Set<String> done = new HashSet<>();
        int n = 0;
        for (ResolveInfo ri : list) {
            String p = ri.activityInfo.packageName;
            if (p.equals(c.getPackageName()) || p.equals(keep) || !done.add(p)) continue;
            try {
                am.killBackgroundProcesses(p);
                n++;
            } catch (Exception ignored) {}
        }
        return n;
    }
}
