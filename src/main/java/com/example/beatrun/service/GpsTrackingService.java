package com.example.beatrun.service;

import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.beatrun.R;
import com.example.beatrun.controller.MusicController;
import com.example.beatrun.gps.LocationTracker;
import com.example.beatrun.model.Music;
import com.example.beatrun.util.Constants;
import com.example.beatrun.util.DatabaseHelper;

public class GpsTrackingService extends Service {

    private static final String CHANNEL_ID = "GpsTrackingChannel";
    private LocationTracker locationTracker;
    private Location lastLocation = null;
    private double totalDistance = 0.0;
    private MusicController musicController;

    private long lastUpdateTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        startForeground(3, createNotification());

        musicController = new MusicController(this,null, null);

        DatabaseHelper db = new DatabaseHelper(this);
        Music music = db.ensureDefaultMusicSelected(this);

        Intent i = new Intent("REFRESH_MUSIC_UI");
        sendBroadcast(i);

        if (music != null) musicController.setMusic(music.getFilePath(), music.getTitle(), false);

        locationTracker = new LocationTracker(this);
        locationTracker.setListener(location -> {
            if (lastLocation != null) {
                totalDistance += lastLocation.distanceTo(location);
            }
            lastLocation = location;
            lastUpdateTime = System.currentTimeMillis();

            double speedKmh = location.getSpeed() * 3.6;

            Intent intent = new Intent("GPS_UPDATE");
            intent.putExtra("distance", totalDistance);
            intent.putExtra("speed", speedKmh);
            sendBroadcast(intent);

            if (speedKmh >= 1.5 && !musicController.isPlaying()) {
                musicController.play();
            } else if (speedKmh < 1.5 && musicController.isPlaying()) {
                musicController.pause("Slow");
            }
        });

        // Delay start 500ms untuk memastikan permission sudah ready
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (hasLocationPermission()) {
                locationTracker.start();
            }
        }, 500);

        // Monitor timeout
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - lastUpdateTime;
                if (elapsed > Constants.INACTIVITY_TIMEOUT_MS) {
                    Intent i = new Intent("GPS_UPDATE");
                    i.putExtra("distance", totalDistance);
                    i.putExtra("speed", 0.0);
                    sendBroadcast(i);
                    if (musicController.isPlaying()) {
                        musicController.pause("No movement");
                    }
                }
                new Handler(Looper.getMainLooper()).postDelayed(this, Constants.INACTIVITY_TIMEOUT_MS);
            }
        }, Constants.INACTIVITY_TIMEOUT_MS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationTracker != null) locationTracker.stop();
        musicController.pause("Service stopped");
    }

    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "BeatRun GPS Tracker", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BeatRun Outdoor Mode")
                .setContentText("Tracking movement via GPS")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
