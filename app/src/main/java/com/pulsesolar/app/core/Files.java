package com.pulsesolar.app.core;

import java.io.File;

/** Operações de arquivo nas pastas do próprio app. */
public final class Files {

    private Files() {
    }

    public static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long s = 0;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) s += size(c);
        return s;
    }

    /** Apaga o conteúdo da pasta (não a pasta) e retorna quantos bytes foram liberados. */
    public static long clearDir(File dir) {
        if (dir == null) return 0;
        long freed = 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        for (File c : children) {
            if (c.isDirectory()) {
                freed += clearDir(c);
                //noinspection ResultOfMethodCallIgnored
                c.delete();
            } else {
                long len = c.length();
                if (c.delete()) freed += len;
            }
        }
        return freed;
    }
}
