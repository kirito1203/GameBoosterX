package com.pulsesolar.app.modules.fps;

import com.pulsesolar.app.core.SystemInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê as frequências da CPU pelos arquivos públicos do kernel (cpufreq), agrupando
 * os núcleos em clusters pela frequência máxima de hardware. Se o SELinux do aparelho
 * bloquear a leitura, os valores ficam -1 e a interface informa "indisponível".
 * Somente leitura: nada aqui altera frequências.
 */
public final class CpuProbe {

    public static final class Cluster {
        /** Frequência máxima do hardware (cpuinfo_max_freq), em kHz. */
        public long hwMaxKhz;
        /** Maior frequência atual entre os núcleos do cluster (scaling_cur_freq). */
        public long curKhz = -1;
        /** Teto atual permitido pelo sistema (scaling_max_freq); cai com limitação térmica/energia. */
        public long capKhz = -1;
        public final List<Integer> cores = new ArrayList<>();

        public float capRatio() {
            return hwMaxKhz > 0 && capKhz > 0 ? capKhz / (float) hwMaxKhz : -1f;
        }

        public float loadRatio() {
            return hwMaxKhz > 0 && curKhz > 0 ? curKhz / (float) hwMaxKhz : -1f;
        }
    }

    private CpuProbe() {
    }

    public static List<Cluster> read() {
        Map<Long, Cluster> map = new LinkedHashMap<>();
        int n = SystemInfo.cores();
        for (int i = 0; i < n; i++) {
            long hw = SystemInfo.cpuFreqKhz(i, "cpuinfo_max_freq");
            if (hw <= 0) continue;
            Cluster c = map.get(hw);
            if (c == null) {
                c = new Cluster();
                c.hwMaxKhz = hw;
                map.put(hw, c);
            }
            c.cores.add(i);
            long cur = SystemInfo.cpuFreqKhz(i, "scaling_cur_freq");
            if (cur > c.curKhz) c.curKhz = cur;
            long cap = SystemInfo.cpuFreqKhz(i, "scaling_max_freq");
            if (cap > 0) c.capKhz = c.capKhz < 0 ? cap : Math.min(c.capKhz, cap);
        }
        List<Cluster> list = new ArrayList<>(map.values());
        Collections.sort(list, (a, b) -> Long.compare(a.hwMaxKhz, b.hwMaxKhz));
        return list;
    }

    /** Cluster mais rápido (núcleos de desempenho) ou null se nada for legível. */
    public static Cluster big(List<Cluster> list) {
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public static boolean procStatReadable() {
        return SystemInfo.readLine("/proc/stat") != null;
    }
}
