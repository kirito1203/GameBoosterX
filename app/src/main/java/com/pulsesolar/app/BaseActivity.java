package com.pulsesolar.app;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Toast;

/** Base das telas internas: botão voltar na barra e ajudantes de thread. */
public abstract class BaseActivity extends Activity {

    protected final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar ab = getActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Executa em segundo plano. */
    protected void runBg(Runnable r) {
        new Thread(r, "turbox-bg").start();
    }

    /** Volta para a thread principal, se a tela ainda existir. */
    protected void ui(Runnable r) {
        handler.post(() -> {
            if (!isFinishing() && !isDestroyed()) r.run();
        });
    }

    protected void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
