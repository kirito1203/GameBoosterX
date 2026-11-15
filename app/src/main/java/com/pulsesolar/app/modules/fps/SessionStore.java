package com.pulsesolar.app.modules.fps;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Guarda os relatórios de sessão em um arquivo JSON privado do app (últimos 20). */
public final class SessionStore {

    private static final String FILE = "fps_sessions.json";
    private static final int MAX = 20;

    private SessionStore() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    public static synchronized List<Session> list(Context c) {
        List<Session> out = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return out;
        try (InputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            JSONArray arr = new JSONArray(new String(data, 0, off, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                if (arr.optJSONObject(i) != null) out.add(Session.fromJson(arr.getJSONObject(i)));
            }
        } catch (IOException | JSONException ignored) {
            // arquivo corrompido: começa vazio
        }
        return out;
    }

    public static synchronized Session get(Context c, long id) {
        for (Session s : list(c)) if (s.id == id) return s;
        return null;
    }

    public static synchronized void add(Context c, Session s) {
        List<Session> all = list(c);
        all.add(0, s);
        while (all.size() > MAX) all.remove(all.size() - 1);
        save(c, all);
    }

    public static synchronized void delete(Context c, long id) {
        List<Session> all = list(c);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id == id) all.remove(i);
        save(c, all);
    }

    public static synchronized void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }

    private static void save(Context c, List<Session> all) {
        try {
            JSONArray arr = new JSONArray();
            for (Session s : all) arr.put(s.toJson());
            File tmp = new File(c.getFilesDir(), FILE + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(file(c));
        } catch (IOException | JSONException ignored) {
        }
    }
}
