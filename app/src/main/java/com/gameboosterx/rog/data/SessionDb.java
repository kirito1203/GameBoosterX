package com.gameboosterx.rog.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Histórico de sessões em SQLite (framework, sem bibliotecas externas). */
public class SessionDb extends SQLiteOpenHelper {

    private static final String DB = "gbx_sessions.db";
    private static final int VERSION = 1;
    private static SessionDb instance;

    public static synchronized SessionDb get(Context c) {
        if (instance == null) instance = new SessionDb(c.getApplicationContext());
        return instance;
    }

    private SessionDb(Context c) {
        super(c, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "pkg TEXT, label TEXT, start INTEGER, end_t INTEGER,"
                + "temp_start REAL, temp_end REAL, temp_max REAL,"
                + "batt_start INTEGER, batt_end INTEGER,"
                + "fps_avg REAL, fps_min REAL, fps_samples INTEGER, drops INTEGER,"
                + "thermal_max INTEGER, samples TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS sessions");
        onCreate(db);
    }

    private static Double nn(float v) { return Float.isNaN(v) ? null : (double) v; }

    public long insert(Session s) {
        ContentValues cv = new ContentValues();
        cv.put("pkg", s.pkg);
        cv.put("label", s.label);
        cv.put("start", s.start);
        cv.put("end_t", s.end);
        cv.put("temp_start", nn(s.tempStart));
        cv.put("temp_end", nn(s.tempEnd));
        cv.put("temp_max", nn(s.tempMax));
        cv.put("batt_start", s.battStart);
        cv.put("batt_end", s.battEnd);
        cv.put("fps_avg", nn(s.fpsAvg));
        cv.put("fps_min", nn(s.fpsMin));
        cv.put("fps_samples", s.fpsSamples);
        cv.put("drops", s.drops);
        cv.put("thermal_max", s.thermalMax);
        cv.put("samples", s.samplesToJson());
        try {
            return getWritableDatabase().insert("sessions", null, cv);
        } catch (Exception e) {
            return -1;
        }
    }

    public List<Session> list(int limit, boolean withSamples) {
        List<Session> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM sessions ORDER BY start DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) out.add(read(c, withSamples));
        } catch (Exception ignored) {
        }
        return out;
    }

    public Session byId(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM sessions WHERE id=?", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) return read(c, true);
        } catch (Exception ignored) {
        }
        return null;
    }

    public void delete(long id) {
        try {
            getWritableDatabase().delete("sessions", "id=?", new String[]{String.valueOf(id)});
        } catch (Exception ignored) {
        }
    }

    public void clear() {
        try {
            getWritableDatabase().delete("sessions", null, null);
        } catch (Exception ignored) {
        }
    }

    private static float f(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return Float.NaN;
        return c.getFloat(i);
    }

    private static int in(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return -1;
        return c.getInt(i);
    }

    private static Session read(Cursor c, boolean withSamples) {
        Session s = new Session();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.pkg = c.getString(c.getColumnIndexOrThrow("pkg"));
        s.label = c.getString(c.getColumnIndexOrThrow("label"));
        s.start = c.getLong(c.getColumnIndexOrThrow("start"));
        s.end = c.getLong(c.getColumnIndexOrThrow("end_t"));
        s.tempStart = f(c, "temp_start");
        s.tempEnd = f(c, "temp_end");
        s.tempMax = f(c, "temp_max");
        s.battStart = in(c, "batt_start");
        s.battEnd = in(c, "batt_end");
        s.fpsAvg = f(c, "fps_avg");
        s.fpsMin = f(c, "fps_min");
        s.fpsSamples = Math.max(0, in(c, "fps_samples"));
        s.drops = Math.max(0, in(c, "drops"));
        s.thermalMax = in(c, "thermal_max");
        if (withSamples) s.samplesFromJson(c.getString(c.getColumnIndexOrThrow("samples")));
        return s;
    }
}
