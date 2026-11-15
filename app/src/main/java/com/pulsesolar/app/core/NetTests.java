package com.pulsesolar.app.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/** Testes de rede reais. Todos os métodos de teste bloqueiam: chame fora da thread principal. */
public final class NetTests {

    /** Endpoint público de teste de velocidade da Cloudflare. */
    public static final String DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=25000000";

    private NetTests() {
    }

    // ------------------------------------------------------------ Estado

    public static final class State {
        public boolean connected;
        public boolean validated;
        public boolean metered;
        public boolean wifi, cellular, ethernet, vpn;
        public int downKbps = -1, upKbps = -1;
        public String iface;
        public List<String> dns = new ArrayList<>();
        public boolean privateDns;
        public String privateDnsServer;

        public String transport() {
            if (!connected) return "Sem conexão";
            StringBuilder sb = new StringBuilder();
            if (wifi) sb.append("Wi-Fi");
            else if (cellular) sb.append("Dados móveis");
            else if (ethernet) sb.append("Cabo");
            else sb.append("Outra");
            if (vpn) sb.append(" + VPN");
            return sb.toString();
        }
    }

    public static State state(Context c) {
        State s = new State();
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return s;
        Network net = cm.getActiveNetwork();
        if (net == null) return s;
        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
        if (caps == null) return s;
        s.connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        s.validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        s.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        s.wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        s.cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        s.ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        s.vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        s.downKbps = caps.getLinkDownstreamBandwidthKbps();
        s.upKbps = caps.getLinkUpstreamBandwidthKbps();
        LinkProperties lp = cm.getLinkProperties(net);
        if (lp != null) {
            s.iface = lp.getInterfaceName();
            for (InetAddress a : lp.getDnsServers()) s.dns.add(a.getHostAddress());
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                s.privateDns = lp.isPrivateDnsActive();
                s.privateDnsServer = lp.getPrivateDnsServerName();
            }
        }
        return s;
    }

    // ------------------------------------------------------------ Latência

    public static final class Ping {
        public String name, host;
        public int port, sent, received;
        public long min = -1, max = -1;
        public double avg = -1, jitter = -1;
        public String error;

        public int lossPercent() {
            return sent == 0 ? 0 : (sent - received) * 100 / sent;
        }
    }

    /**
     * Mede o tempo real de abertura de conexão TCP (handshake) com o servidor.
     * Funciona sem root, ao contrário do ping ICMP.
     */
    public static Ping tcpPing(String name, String host, int port, int count, int timeoutMs) {
        Ping p = new Ping();
        p.name = name;
        p.host = host;
        p.port = port;
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            p.sent = count;
            p.error = "Falha ao resolver o nome (DNS)";
            return p;
        }
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            p.sent++;
            long t0 = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(addr, port), timeoutMs);
                times.add((System.nanoTime() - t0) / 1_000_000L);
            } catch (IOException ignored) {
                // conta como perda
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        p.received = times.size();
        if (!times.isEmpty()) {
            long sum = 0;
            p.min = Long.MAX_VALUE;
            p.max = Long.MIN_VALUE;
            for (long t : times) {
                sum += t;
                p.min = Math.min(p.min, t);
                p.max = Math.max(p.max, t);
            }
            p.avg = sum / (double) times.size();
            if (times.size() > 1) {
                double j = 0;
                for (int i = 1; i < times.size(); i++) j += Math.abs(times.get(i) - times.get(i - 1));
                p.jitter = j / (times.size() - 1);
            } else {
                p.jitter = 0;
            }
        } else if (p.error == null) {
            p.error = "Sem resposta";
        }
        return p;
    }

    /** Tempo de resolução DNS em ms (-1 se falhar). Pode vir do cache do sistema. */
    public static long dnsMs(String host) {
        long t0 = System.nanoTime();
        try {
            InetAddress.getAllByName(host);
            return (System.nanoTime() - t0) / 1_000_000L;
        } catch (UnknownHostException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------ Download

    public interface Progress {
        void onProgress(long bytes, long millis);
    }

    public static final class Download {
        public long bytes;
        public long millis;
        public double mbps;
        public String error;
    }

    /** Baixa de verdade até `maxMillis` ms ou até o fim do arquivo e calcula a velocidade média. */
    public static Download download(String url, long maxMillis, Progress cb) {
        Download d = new Download();
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setUseCaches(false);
            int code = con.getResponseCode();
            if (code != 200) {
                d.error = "Servidor respondeu HTTP " + code;
                return d;
            }
            byte[] buf = new byte[64 * 1024];
            long start = SystemClock.elapsedRealtime();
            long lastReport = start;
            try (InputStream in = con.getInputStream()) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    d.bytes += n;
                    long now = SystemClock.elapsedRealtime();
                    if (cb != null && now - lastReport >= 300) {
                        cb.onProgress(d.bytes, now - start);
                        lastReport = now;
                    }
                    if (now - start >= maxMillis) break;
                }
            }
            d.millis = Math.max(1, SystemClock.elapsedRealtime() - start);
            d.mbps = d.bytes * 8.0 / (d.millis * 1000.0);
        } catch (IOException e) {
            d.error = "Falha no download: " + e.getClass().getSimpleName();
        } finally {
            if (con != null) con.disconnect();
        }
        return d;
    }

    /** Classificação simples da latência para jogos online. */
    public static String gameRating(double avgMs, double jitterMs, int loss) {
        if (avgMs < 0) return "Sem conexão com o servidor";
        if (loss > 10 || avgMs > 150 || jitterMs > 40) return "Ruim para jogos online";
        if (loss > 0 || avgMs > 80 || jitterMs > 20) return "Aceitável para jogos online";
        return "Boa para jogos online";
    }
}
