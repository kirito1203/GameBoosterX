package com.gameboosterx.rog.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Medição real de latência. Tenta ICMP (binário ping do sistema) e,
 * se não houver, mede o tempo de conexão TCP. O app apenas MEDE —
 * não existe forma legítima de "reduzir o ping" por software no aparelho.
 */
public final class NetworkTester {

    private static final Pattern TIME = Pattern.compile("time[=<]([0-9.]+)\\s*ms");

    private NetworkTester() {}

    public static final class Result {
        public float ms = Float.NaN; // NaN = perda/timeout
        public String method = "";
    }

    public static Result ping(String host, int timeoutMs) {
        Result r = icmp(host, timeoutMs);
        if (!Float.isNaN(r.ms)) return r;
        return tcp(host, 443, timeoutMs, 53);
    }

    private static Result icmp(String host, int timeoutMs) {
        Result r = new Result();
        r.method = "ICMP";
        try {
            int w = Math.max(1, timeoutMs / 1000);
            java.lang.Process p = new ProcessBuilder("ping", "-c", "1", "-W", String.valueOf(w), host)
                    .redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }
            if (!p.waitFor(timeoutMs + 1000L, TimeUnit.MILLISECONDS)) p.destroy();
            Matcher m = TIME.matcher(sb);
            if (m.find()) r.ms = Float.parseFloat(m.group(1));
        } catch (Exception ignored) {
        }
        return r;
    }

    private static Result tcp(String host, int port, int timeoutMs, int fallbackPort) {
        Result r = new Result();
        r.method = "TCP";
        int[] ports = {port, fallbackPort};
        for (int pt : ports) {
            long t0 = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, pt), timeoutMs);
                r.ms = (System.nanoTime() - t0) / 1_000_000f;
                return r;
            } catch (Exception ignored) {
            }
        }
        return r;
    }

    /** Estatísticas de uma série de medições. */
    public static final class Stats {
        public int sent;
        public int lost;
        public float avg = Float.NaN;
        public float min = Float.NaN;
        public float max = Float.NaN;
        public float jitter = Float.NaN;

        public float lossPct() { return sent == 0 ? 0 : lost * 100f / sent; }

        /** Nota de 0 a 100 baseada em latência, oscilação e perda. */
        public int score() {
            if (sent == 0 || Float.isNaN(avg)) return 0;
            float s = 100;
            s -= Math.max(0, (avg - 30) * 0.45f);
            if (!Float.isNaN(jitter)) s -= jitter * 1.2f;
            s -= lossPct() * 4f;
            return Math.max(0, Math.min(100, Math.round(s)));
        }

        public String quality() {
            int sc = score();
            if (sent == 0 || Float.isNaN(avg)) return "Sem conexão";
            if (sc >= 85) return "Excelente";
            if (sc >= 70) return "Boa";
            if (sc >= 50) return "Regular";
            return "Ruim";
        }
    }

    public static Stats stats(List<Float> values) {
        Stats st = new Stats();
        st.sent = values.size();
        float sum = 0, prev = Float.NaN, jit = 0;
        int ok = 0, jn = 0;
        for (Float v : values) {
            if (v == null || Float.isNaN(v)) { st.lost++; continue; }
            ok++;
            sum += v;
            if (Float.isNaN(st.min) || v < st.min) st.min = v;
            if (Float.isNaN(st.max) || v > st.max) st.max = v;
            if (!Float.isNaN(prev)) { jit += Math.abs(v - prev); jn++; }
            prev = v;
        }
        if (ok > 0) st.avg = sum / ok;
        if (jn > 0) st.jitter = jit / jn;
        return st;
    }

    /** Descrição da conexão ativa (Wi-Fi/dados, força do sinal quando informada). */
    public static String connectionInfo(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return "Sem conexão";
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null) return "Conexão desconhecida";
            String type;
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = "Wi-Fi";
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type = "Dados móveis";
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) type = "Ethernet";
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) type = "VPN";
            else type = "Outra";
            StringBuilder sb = new StringBuilder(type);
            int sig = nc.getSignalStrength();
            if (sig != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED && sig < 0) {
                sb.append(String.format(Locale.US, " • sinal %d dBm", sig));
            }
            int down = nc.getLinkDownstreamBandwidthKbps();
            if (down > 0) sb.append(String.format(Locale.US, " • banda estimada %.1f Mbps", down / 1000f));
            if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) sb.append(" • rede limitada");
            return sb.toString();
        } catch (Exception e) {
            return "Indisponível";
        }
    }
}
