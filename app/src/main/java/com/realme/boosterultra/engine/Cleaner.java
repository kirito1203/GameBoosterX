package com.realme.boosterultra.engine;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Limpeza inteligente: acha lixo de verdade no armazenamento. */
public final class Cleaner {
    private Cleaner() {}

    public static final class Group {
        public String name, desc;
        public List<File> files = new ArrayList<>();
        public long size;
        public void add(File f, long s) { files.add(f); size += s; }
    }

    public static final class Result {
        public Group apks = new Group(), thumbs = new Group(), temps = new Group(), empties = new Group(), cache = new Group();
        public List<File> big = new ArrayList<>();
        public int scanned;
    }

    private static final Set<String> KEEP_DIRS = new HashSet<>(Arrays.asList(
            "dcim", "download", "pictures", "music", "movies", "documents", "alarms", "notifications",
            "ringtones", "podcasts", "audiobooks", "recordings", "android", "screenshots", "camera"));

    public static boolean hasAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static Result scan(Context c) {
        Result r = new Result();
        r.apks.name = "Instaladores APK já baixados";
        r.apks.desc = "Arquivos de instalação. O app instalado continua funcionando.";
        r.thumbs.name = "Miniaturas de fotos";
        r.thumbs.desc = "O Android recria sozinho quando precisar.";
        r.temps.name = "Arquivos temporários e logs";
        r.temps.desc = ".tmp, .log e restos de apps.";
        r.empties.name = "Pastas vazias";
        r.empties.desc = "Pastas sem nada dentro.";
        r.cache.name = "Cache do Realme Booster Ultra";
        r.cache.desc = "Arquivos temporários deste app.";

        File[] own = c.getCacheDir().listFiles();
        if (own != null) for (File f : own) r.cache.add(f, sizeOf(f));

        File root = Environment.getExternalStorageDirectory();
        walk(root, root, 0, r);
        Collections.sort(r.big, (a, b) -> Long.compare(b.length(), a.length()));
        if (r.big.size() > 10) r.big = new ArrayList<>(r.big.subList(0, 10));
        return r;
    }

    private static void walk(File root, File dir, int depth, Result r) {
        if (depth > 14) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isDirectory()) {
                if (depth == 0 && n.equals("android")) continue;
                File[] inside = f.listFiles();
                if (inside != null && inside.length == 0) {
                    if (!(depth == 0 && KEEP_DIRS.contains(n))) r.empties.add(f, 0);
                    continue;
                }
                walk(root, f, depth + 1, r);
            } else {
                r.scanned++;
                long len = f.length();
                String path = f.getAbsolutePath().toLowerCase(Locale.ROOT);
                if (n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk")) r.apks.add(f, len);
                else if (path.contains("/.thumbnails/")) r.thumbs.add(f, len);
                else if (n.endsWith(".tmp") || n.endsWith(".temp") || n.endsWith(".log") || n.startsWith("~$")) r.temps.add(f, len);
                else if (len > 150L * 1024 * 1024) r.big.add(f);
            }
        }
    }

    /** STORAGE ANALYZER: tamanho por tipo e por pasta. {nome, bytes} */
    public static final String[] CATS = {"Imagens", "Vídeos", "Áudios", "Documentos", "APKs", "Compactados", "Outros"};

    public static long[] analyze(java.util.Map<String, Long> folders) {
        long[] cat = new long[CATS.length];
        File root = Environment.getExternalStorageDirectory();
        File[] top = root.listFiles();
        if (top == null) return cat;
        for (File f : top) {
            long before = 0;
            for (long x : cat) before += x;
            if (f.isDirectory()) {
                if (f.getName().equalsIgnoreCase("Android")) continue;
                walkCat(f, 0, cat);
            } else {
                addCat(f, cat);
            }
            long after = 0;
            for (long x : cat) after += x;
            if (f.isDirectory() && after - before > 0) folders.put(f.getName(), after - before);
        }
        return cat;
    }

    private static void walkCat(File dir, int depth, long[] cat) {
        if (depth > 14) return;
        File[] l = dir.listFiles();
        if (l == null) return;
        for (File f : l) {
            if (f.isDirectory()) walkCat(f, depth + 1, cat);
            else addCat(f, cat);
        }
    }

    private static void addCat(File f, long[] cat) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        String ext = n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : "";
        int i;
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "webp": case "heic": case "gif": case "bmp": i = 0; break;
            case "mp4": case "mkv": case "3gp": case "webm": case "mov": case "avi": i = 1; break;
            case "mp3": case "m4a": case "ogg": case "wav": case "opus": case "aac": case "flac": i = 2; break;
            case "pdf": case "doc": case "docx": case "xls": case "xlsx": case "ppt": case "pptx": case "txt": case "epub": i = 3; break;
            case "apk": case "apks": case "xapk": i = 4; break;
            case "zip": case "rar": case "7z": case "tar": case "gz": case "mcpack": case "mcaddon": case "mcworld": i = 5; break;
            default: i = 6;
        }
        cat[i] += f.length();
    }

    public static long sizeOf(File f) {
        if (f.isFile()) return f.length();
        long s = 0;
        File[] l = f.listFiles();
        if (l != null) for (File x : l) s += sizeOf(x);
        return s;
    }

    public static long delete(List<File> files) {
        long freed = 0;
        for (File f : files) {
            long s = sizeOf(f);
            if (deleteRec(f)) freed += s;
        }
        return freed;
    }

    private static boolean deleteRec(File f) {
        if (f.isDirectory()) {
            File[] l = f.listFiles();
            if (l != null) for (File x : l) deleteRec(x);
        }
        return f.delete();
    }
}
