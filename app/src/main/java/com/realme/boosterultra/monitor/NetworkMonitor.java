package com.realme.boosterultra.monitor;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Medições de rede reais. */
public final class NetworkMonitor {
    private NetworkMonitor() {}

    /** Tempo de conexão TCP em ms, ou -1 se falhar. */
    public static int tcpPing(String host, int port, int timeout) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            long t = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(addr, port), timeout);
            }
            return (int) ((System.nanoTime() - t) / 1_000_000L);
        } catch (Exception e) {
            return -1;
        }
    }

    /** {média, mínimo, máximo, sucessos} */
    public static int[] measure(String host, int port, int tries) {
        int sum = 0, ok = 0, min = Integer.MAX_VALUE, max = 0;
        for (int i = 0; i < tries; i++) {
            int ms = tcpPing(host, port, 2000);
            if (ms >= 0) {
                sum += ms; ok++;
                min = Math.min(min, ms);
                max = Math.max(max, ms);
            }
            try { Thread.sleep(250); } catch (InterruptedException e) { break; }
        }
        if (ok == 0) return new int[]{-1, -1, -1, 0};
        return new int[]{sum / ok, min, max, ok};
    }

    /** NETWORK LATENCY AI: {média, mínimo, máximo, jitter, perda%} com n tentativas. */
    public static int[] advanced(String host, int port, int n) {
        int ok = 0, sum = 0, min = Integer.MAX_VALUE, max = 0, prev = -1, jit = 0, jn = 0;
        for (int i = 0; i < n; i++) {
            int ms = tcpPing(host, port, 1500);
            if (ms >= 0) {
                ok++; sum += ms; min = Math.min(min, ms); max = Math.max(max, ms);
                if (prev >= 0) { jit += Math.abs(ms - prev); jn++; }
                prev = ms;
            }
            try { Thread.sleep(150); } catch (InterruptedException e) { break; }
        }
        int loss = Math.round((n - ok) * 100f / n);
        if (ok == 0) return new int[]{-1, -1, -1, -1, 100};
        return new int[]{sum / ok, min, max, jn > 0 ? jit / jn : 0, loss};
    }

    public static String describe(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "Sem informação";
        Network n = cm.getActiveNetwork();
        if (n == null) return "Sem conexão";
        NetworkCapabilities nc = cm.getNetworkCapabilities(n);
        if (nc == null) return "Sem conexão";
        StringBuilder sb = new StringBuilder();
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            sb.append("Wi-Fi");
            if (Build.VERSION.SDK_INT >= 29) {
                int rssi = nc.getSignalStrength();
                if (rssi != Integer.MIN_VALUE && rssi < 0) {
                    String q = rssi > -55 ? "excelente" : rssi > -67 ? "bom" : rssi > -75 ? "regular" : "fraco";
                    sb.append(" • sinal ").append(rssi).append(" dBm (").append(q).append(")");
                }
            }
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            sb.append("Dados móveis");
        } else {
            sb.append("Outra conexão");
        }
        int down = nc.getLinkDownstreamBandwidthKbps();
        if (down > 0) sb.append("\nVelocidade estimada pelo Android: ~").append(down / 1000).append(" Mbps");
        return sb.toString();
    }
}
