package com.realme.boosterultra;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.app.Application;

/** Carrega o tema antes de qualquer tela abrir. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        MainActivity.loadTheme(this);
    }
}
