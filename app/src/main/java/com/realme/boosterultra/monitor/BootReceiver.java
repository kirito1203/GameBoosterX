package com.realme.boosterultra.monitor;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Religa a economia inteligente depois de reiniciar o celular. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        SmartAlerts.sync(c);
        ProfileManager.prefs(c).edit().putBoolean("active", false).apply();
    }
}
