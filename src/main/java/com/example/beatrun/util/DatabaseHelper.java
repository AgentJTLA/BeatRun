package com.example.beatrun.util;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;

import com.example.beatrun.R;
import com.example.beatrun.model.Music;

import java.util.*;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "beatrun.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_MUSIC = "musics";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_PATH = "file_path";
    private static final String COLUMN_SELECTED = "is_selected";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE = "CREATE TABLE " + TABLE_MUSIC + "(" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_TITLE + " TEXT," +
                COLUMN_PATH + " TEXT," +
                COLUMN_SELECTED + " INTEGER DEFAULT 0)";
        db.execSQL(CREATE);
    }

    public Music ensureDefaultMusicSelected(Context context) {
        Music selected = getSelectedMusic();
        if (selected != null) return selected;

        String path = "android.resource://" + context.getPackageName() + "/" + R.raw.motivator;
        String title = "Default Track";

        // Cek duplikat
        List<Music> musics = getAllMusics();
        for (Music m : musics) {
            if (m.getFilePath().equals(path)) {
                setSelectedMusic(m.getId());
                return m;
            }
        }

        // Tambah baru
        Music music = new Music(title, path);
        long id = addMusic(music);
        setSelectedMusic((int) id);
        return music;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_MUSIC +
                    " ADD COLUMN " + COLUMN_SELECTED + " INTEGER DEFAULT 0");
        }
    }

    public long addMusic(Music music) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, music.getTitle());
        values.put(COLUMN_PATH, music.getFilePath());
        return db.insert(TABLE_MUSIC, null, values);
    }

    public List<Music> getAllMusics() {
        List<Music> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_MUSIC, null);
        while (cursor.moveToNext()) {
            Music m = new Music();
            m.setId(cursor.getInt(0));
            m.setTitle(cursor.getString(1));
            m.setFilePath(cursor.getString(2));
            list.add(m);
        }
        cursor.close();
        return list;
    }

    public void setSelectedMusic(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_MUSIC + " SET " + COLUMN_SELECTED + " = 0");
        db.execSQL("UPDATE " + TABLE_MUSIC + " SET " + COLUMN_SELECTED + " = 1 WHERE " + COLUMN_ID + " = " + id);
    }

    public Music getSelectedMusic() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MUSIC, null,
                COLUMN_SELECTED + " = 1", null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            Music music = new Music();
            music.setId(cursor.getInt(0));
            music.setTitle(cursor.getString(1));
            music.setFilePath(cursor.getString(2));
            cursor.close();
            return music;
        }
        return null;
    }

    public void renameMusic(int id, String newTitle) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", newTitle);
        db.update("musics", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void deleteMusic(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("musics", "id = ?", new String[]{String.valueOf(id)});
    }

}
