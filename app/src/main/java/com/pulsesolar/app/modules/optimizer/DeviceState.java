package com.pulsesolar.app.modules.optimizer;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.PowerManager;
import android.view.Display;

import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.NetTests;
import com.pulsesolar.app.modules.fps.CpuProbe;
import com.pulsesolar.app.modules.fps.Session;

import java.util.List;

/**
 * Estado real do aparelho usado pelos perfis do Game Optimizer.
 * A "classe de desempenho" é uma estimativa baseada em RAM total e frequência máxima
 * dos núcleos rápidos (quando legível); a regra usada é mostrada ao usuário.
 */
public final class DeviceState {

    public static final int LOW = 0, MID = 1, HIGH = 2;

    public Session.Point point;
    public int tier;
    public String tierReason;
    public float ramTotalGb;
    public long bigHwKhz = -1;
    public int currentHz, maxHz;
    public boolean hot, limited;
    public boolean btAudio, wiredAudio, lowLatencyAudio, proAudio;
    public NetTests.State net;

    private DeviceState() {
    }

    @SuppressWarnings("deprecation")
    public static DeviceState capture(Activity a) {
        DeviceState d = new DeviceState();
        d.point = Session.Point.capture(a, true);
        d.ramTotalGb = d.point.ramTotal / (1024f * 1024f * 1024f);

        List<CpuProbe.Cluster> cl = CpuProbe.read();
        CpuProbe.Cluster big = CpuProbe.big(cl);
        if (big != null) d.bigHwKhz = big.hwMaxKhz;

        String cpuTxt = d.bigHwKhz > 0 ? "núcleos rápidos até " + Format.ghz(d.bigHwKhz) : "frequência da CPU não legível";
        if (d.ramTotalGb < 3.5f || (d.bigHwKhz > 0 && d.bigHwKhz < 1_800_000L)) {
            d.tier = LOW;
        } else if (d.ramTotalGb < 6f || d.bigHwKhz <= 0 || d.bigHwKhz < 2_200_000L) {
            d.tier = MID;
        } else {
            d.tier = HIGH;
        }
        d.tierReason = "Base: " + Format.one(d.ramTotalGb) + " GB de RAM e " + cpuTxt
                + (d.bigHwKhz <= 0 ? " (limitado a Intermediária por falta de dados)" : "") + ".";

        d.hot = d.point.thermal >= PowerManager.THERMAL_STATUS_MODERATE
                || (!Float.isNaN(d.point.tempC) && d.point.tempC >= 40f);
        d.limited = d.hot || d.point.powerSave;

        Display disp = a.getWindowManager().getDefaultDisplay();
        d.currentHz = Math.round(disp.getRefreshRate());
        for (Display.Mode m : disp.getSupportedModes()) d.maxHz = Math.max(d.maxHz, Math.round(m.getRefreshRate()));

        AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        for (AudioDeviceInfo dev : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = dev.getType();
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_HEADSET
                    || t == AudioDeviceInfo.TYPE_BLE_SPEAKER) d.btAudio = true;
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || t == AudioDeviceInfo.TYPE_USB_HEADSET) d.wiredAudio = true;
        }
        PackageManager pm = a.getPackageManager();
        d.lowLatencyAudio = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY);
        d.proAudio = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO);

        d.net = NetTests.state(a);
        return d;
    }

    /** Classe usada nas recomendações: um nível abaixo se o aparelho está quente ou em economia. */
    public int effectiveTier() {
        return limited ? Math.max(LOW, tier - 1) : tier;
    }

    public static String tierName(int t) {
        switch (t) {
            case LOW: return "Básica";
            case HIGH: return "Alta";
            default: return "Intermediária";
        }
    }

    public float ramAvailGb() {
        return point.ramAvail / (1024f * 1024f * 1024f);
    }
}
