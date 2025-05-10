package com.example.beatrun.service;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.beatrun.R;
import com.example.beatrun.controller.MusicController;
import com.example.beatrun.model.Music;
import com.example.beatrun.sensor.StepTracker;
import com.example.beatrun.stats.StatsCalculator;
import com.example.beatrun.util.Constants;
import com.example.beatrun.util.DatabaseHelper;

public class StepMusicService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private static StepTracker stepTracker;
    private StatsCalculator statsCalculator;
    private MusicController musicController;
    private PowerManager.WakeLock wakeLock;
    private long lastStepTime = -1;


    private Handler intervalHandler;
    private Runnable intervalRunnable;

    private static final String CHANNEL_ID = "StepMusicChannel";

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate(){
        super.onCreate();
        acquireWakeLock();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        stepTracker = new StepTracker();
        statsCalculator = new StatsCalculator(Constants.DEFAULT_STRIDE_LENGTH_METERS);
        musicController = new MusicController(this,null, null);


        // 1. Aktifkan sensor terlebih dahulu
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
            //  Tambahan: paksa ulang registrasi setelah 1 detik
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                sensorManager.unregisterListener(this);
                sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
            }, 1000);
        }


// 2. Tunda pengecekan musik & pemutaran default
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            DatabaseHelper db = new DatabaseHelper(this);
            Music music = db.ensureDefaultMusicSelected(this);
            if (music != null) {
                musicController.setMusic(music.getFilePath(), music.getTitle(), false);
            }

            // Trigger refresh UI setelah default music dijamin ada
            sendBroadcast(new Intent("REFRESH_MUSIC_UI"));

        }, 1000);  // tunda 1 detik agar DB dan permission benar-benar siap


        createNotificationChannel();
        startForeground(2, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BeatRun Step Service")
                .setContentText("Tracking steps")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        );

        // Interval logic from original MainActivity
        intervalHandler = new Handler(Looper.getMainLooper());
        intervalRunnable = () -> {
            int buffer = stepTracker.getStepBuffer();
            long bufferElapsed = stepTracker.getBufferElapsedMillis(); // ⬅️ Gunakan ini

            int totalSteps = stepTracker.getTotalSteps();
            double distance = statsCalculator.calculateDistance(totalSteps);

            double speed;
            if (buffer > 0 && bufferElapsed > 0) {
                double intervalDistance = statsCalculator.calculateDistance(buffer);
                speed = statsCalculator.calculateSpeed(intervalDistance, bufferElapsed);
            } else {
                speed = 0.0;
            }

            boolean isPlaying = musicController.isPlaying();
            if (speed < 1.5 && isPlaying) {
                musicController.pause("Idle");
            } else if (speed >= 1.5 && !isPlaying) {
                musicController.play();
            }

            // Broadcast update
            Intent intent = new Intent("STEP_UPDATE");
            intent.putExtra("steps", totalSteps);
            intent.putExtra("distance", distance);
            intent.putExtra("speed", speed);
            sendBroadcast(intent);

            stepTracker.resetBuffer(); // reset buffer + waktu
            intervalHandler.postDelayed(this.intervalRunnable, Constants.INACTIVITY_TIMEOUT_MS);
        };



        intervalHandler.postDelayed(intervalRunnable, Constants.INACTIVITY_TIMEOUT_MS + 3000);

        IntentFilter filter = new IntentFilter();
        filter.addAction("ACTION_PAUSE_STEP_SERVICE");
        filter.addAction("ACTION_RESUME_STEP_SERVICE");
        filter.addAction("ACTION_RESET_STEP_SERVICE");
        filter.addAction("ACTION_UPDATE_SELECTED_MUSIC");

        registerReceiver(controlReceiver, filter);



    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (intervalHandler != null) intervalHandler.removeCallbacks(intervalRunnable);
        sensorManager.unregisterListener(this);
        musicController.pause("Stopped");

        unregisterReceiver(controlReceiver);
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeatRun::StepLock");
            wakeLock.acquire();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "BeatRun Steps", NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            stepTracker.processStep(event);
            long currentTime = System.currentTimeMillis();

            // Hitung real-time speed
            double speed = 0.0;
            double distance = 0.0;
            if (lastStepTime > 0) {
                long deltaTime = currentTime - lastStepTime;
                if (deltaTime > 0) {
                    distance = statsCalculator.calculateDistance(1);
                    speed = statsCalculator.calculateSpeed(distance, deltaTime); // km/h
                }
            }
            lastStepTime = currentTime;

            // Musik logic
            boolean isPlaying = musicController.isPlaying();
            if (speed >= 1.5  && !isPlaying) {
                musicController.play();
            } else if (speed < 1.5 && isPlaying) {
                musicController.pause("Slow movement");
            }

            // Real-time broadcast
            Intent intent = new Intent("STEP_UPDATE");
            intent.putExtra("steps", stepTracker.getTotalSteps());
            intent.putExtra("distance", statsCalculator.calculateDistance(stepTracker.getTotalSteps()));
            intent.putExtra("speed", speed);
            sendBroadcast(intent);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case "ACTION_PAUSE_STEP_SERVICE":
                    sensorManager.unregisterListener(StepMusicService.this);  // Pause tracking
                    musicController.pause("Paused by user");
                    break;

                case "ACTION_RESUME_STEP_SERVICE":
                    if (stepSensor != null) {
                        sensorManager.registerListener(StepMusicService.this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
                        musicController.play();
                    }
                    break;

                case "ACTION_RESET_STEP_SERVICE":
                    stepTracker.resetSteps();
                    sendBroadcast(new Intent("STEP_UPDATE"));
                    break;
                case "ACTION_UPDATE_SELECTED_MUSIC":
                    Music updated = new DatabaseHelper(context).getSelectedMusic();
                    if (updated != null) {
                        musicController.setMusic(updated.getFilePath(), updated.getTitle(), false);

                        // PASTIKAN sensor aktif agar step mulai dihitung setelah pilih lagu
                        if (stepSensor != null) {
                            sensorManager.unregisterListener(StepMusicService.this); // Hindari duplikat listener
                            sensorManager.registerListener(StepMusicService.this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
                        }
                    }
                    break;

            }
        }
    };
}
