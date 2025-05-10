package com.example.beatrun.service;

import android.app.*;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.beatrun.R;

public class MusicService extends Service {
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new MusicBinder();
    private static final String CHANNEL_ID = "MusicChannel";
    private String currentPath;

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification());
    }

    public void playMusic(String filePath) {
        try {
            if (mediaPlayer != null) mediaPlayer.release();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            currentPath = filePath;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void pauseMusic() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public String getCurrentPath() {
        return currentPath;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Music Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(ch);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BeatRun Music")
                .setContentText("Pemutar musik aktif di latar belakang")
                .setSmallIcon(R.drawable.ic_music_note)
                .build();
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) mediaPlayer.release();
        super.onDestroy();
    }
}
