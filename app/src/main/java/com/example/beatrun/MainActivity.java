package com.example.beatrun;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beatrun.controller.MusicController;
import com.example.beatrun.model.Music;
import com.example.beatrun.model.MusicAdapter;
import com.example.beatrun.service.GpsTrackingService;
import com.example.beatrun.service.StepMusicService;
import com.example.beatrun.util.DatabaseHelper;
import com.example.beatrun.util.TrackingMode;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 101;
    private static final int PICK_AUDIO_REQUEST = 102;

    private TextView tvSteps, tvSpeed, tvDistance, tvStatus;
    private ImageButton btnToggle;
    private ImageButton btnPause;
    private RecyclerView musicRecyclerView;
    private TrackingMode mode = TrackingMode.INDOOR;
    private DatabaseHelper dbHelper;
    private MusicController musicController;

    private boolean isPaused = false;

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case "STEP_UPDATE":
                    int steps = intent.getIntExtra("steps", 0);
                    double sDistance = intent.getDoubleExtra("distance", 0.0);
                    double sSpeed = intent.getDoubleExtra("speed", 0.0);
                    updateUI(steps, sDistance, sSpeed);
                    break;

                case "GPS_UPDATE":
                    double gDistance = intent.getDoubleExtra("distance", 0.0);
                    double gSpeed = intent.getDoubleExtra("speed", 0.0);
                    updateUI(-1, gDistance, gSpeed); // -1 berarti tidak update langkah
                    break;
                case "REFRESH_MUSIC_UI":
                    refreshMusicList();
                    break;

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();
        registerReceivers();
        initViews();

        dbHelper = new DatabaseHelper(this);
        musicController = new MusicController(this, findViewById(R.id.tvMusicStatus), findViewById(R.id.currentSongTitle)); // digunakan hanya untuk set musik dari UI
        setupMusicUI();


        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (hasAllPermissions()) {
                startTrackingServiceByMode(); // ⬅️ akan dipanggil HANYA saat semua permission ready
            }
        }, 500);
    }

    private void initViews() {
        tvSteps = findViewById(R.id.tvSteps);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvDistance = findViewById(R.id.tvDistance);
        tvStatus = findViewById(R.id.motivational);
        ImageButton btnUpload = findViewById(R.id.btnUpload);
        btnUpload.setOnClickListener(v -> openFilePicker());
        ImageButton btnPrevious = findViewById(R.id.btnPrevious);
        btnPrevious.setOnClickListener(v -> playPrevSong());
        ImageButton btnNext = findViewById(R.id.btnNext);
        btnNext.setOnClickListener(v ->playNextSong());
        musicRecyclerView = findViewById(R.id.musicRecyclerView);
        btnToggle = findViewById(R.id.btnToggleMode);
        btnToggle.setOnClickListener(v -> switchMode());
        btnPause = findViewById(R.id.pauseStep);
        btnPause.setOnClickListener(v -> {
            if (!isPaused) pauseStep();
            else resumeStep();
        });
        ImageButton btnReset = findViewById(R.id.resetStep);
        btnReset.setOnClickListener(v -> resetStep());
        ImageButton btnStop = findViewById(R.id.stopStep);
        btnStop.setOnClickListener(v -> stopStep());
    }

    private void setupMusicUI() {
        musicRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        refreshMusicList();
    }

    private void refreshMusicList() {
        List<Music> musics = dbHelper.getAllMusics();
        MusicAdapter adapter = new MusicAdapter(musics,
                music -> {
                    dbHelper.setSelectedMusic(music.getId());

                    // Kirim broadcast untuk update musik TANPA restart service
                    Intent intent = new Intent("ACTION_UPDATE_SELECTED_MUSIC");
                    sendBroadcast(intent);

                    musicController.setMusic(music.getFilePath(), music.getTitle(), false);

                },
                music -> {
                    EditText input = new EditText(this);
                    input.setText(music.getTitle());

                    new AlertDialog.Builder(this)
                            .setTitle("Ganti Nama")
                            .setView(input)
                            .setPositiveButton("Simpan", (dialog, which) -> {
                                String newName = input.getText().toString().trim();
                                if (!newName.isEmpty()) {
                                    dbHelper.renameMusic(music.getId(), newName);
                                    refreshMusicList();
                                }
                            })
                            .setNegativeButton("Batal", null)
                            .show();
                },
                music -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Hapus Lagu")
                            .setMessage("Yakin ingin menghapus lagu ini?")
                            .setPositiveButton("Hapus", (dialog, which) -> {
                                Music current = dbHelper.getSelectedMusic();
                                if (current != null && current.getId() == music.getId()) {
                                    // Jika lagu yang sedang diputar adalah yang dihapus
                                    musicController.stop();     // Stop playback
                                    musicController.clear();    // Hapus referensi lagu

                                    sendBroadcast(new Intent("REFRESH_MUSIC_UI").setPackage(getPackageName()));
                                }

                                dbHelper.deleteMusic(music.getId()); // Hapus dari database
                                refreshMusicList(); // Update tampilan list

                                ((TextView)findViewById(R.id.currentSongTitle)).setText("No song currently playing");
                                ((TextView)findViewById(R.id.tvMusicStatus)).setText("No song currently playing");

                                List<Music> remaining = dbHelper.getAllMusics();
                                if (!remaining.isEmpty()) {
                                    Music fallback = remaining.get(0);
                                    dbHelper.setSelectedMusic(fallback.getId());

                                    // Kirim broadcast ke StepMusicService untuk update
                                    sendBroadcast(new Intent("ACTION_UPDATE_SELECTED_MUSIC").setPackage(getPackageName()));

                                    // Tampilkan di UI
                                    musicController.setMusic(fallback.getFilePath(), fallback.getTitle(), false);
                                }
                            })
                            .setNegativeButton("Batal", null)
                            .show();
                }

        );
        musicRecyclerView.setAdapter(adapter);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }

    private void playPrevSong(){
        List<Music> musicList = dbHelper.getAllMusics();
        Music current = dbHelper.getSelectedMusic();
        if(musicList.isEmpty() || current == null) return;

        int currentIndex = -1;
        for (int i = 0; i < musicList.size(); i++) {
            if (musicList.get(i).getId() == current.getId()) {
                currentIndex = i;
                break;
            }
        }

        if(currentIndex > 0){
            Music previous = musicList.get(currentIndex - 1);
            dbHelper.setSelectedMusic(previous.getId());
            stopService(new Intent(this, StepMusicService.class));
            ContextCompat.startForegroundService(this, new Intent(this, StepMusicService.class));
            musicController.setMusic(previous.getFilePath(), previous.getTitle(), false);
        } else{
            Toast.makeText(this, "Ini adalah lagu pertama dalam daftar", Toast.LENGTH_SHORT).show();
        }

    }

    private void playNextSong(){
        List<Music> musicList = dbHelper.getAllMusics();
        Music current = dbHelper.getSelectedMusic();
        if (musicList.isEmpty() || current == null) return;

        int currentIndex = -1;
        for (int i = 0; i < musicList.size(); i++) {
            if (musicList.get(i).getId() == current.getId()) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex >= 0 && currentIndex < musicList.size() - 1) {
            Music next = musicList.get(currentIndex + 1);
            dbHelper.setSelectedMusic(next.getId());
            stopService(new Intent(this, StepMusicService.class));
            ContextCompat.startForegroundService(this, new Intent(this, StepMusicService.class));
            musicController.setMusic(next.getFilePath(), next.getTitle(), false);
        } else {
            Toast.makeText(this, "Ini adalah lagu terakhir dalam daftar", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_AUDIO_REQUEST && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            String name = uri.getLastPathSegment();
            Music music = new Music(name, uri.toString());
            dbHelper.addMusic(music); // hanya simpan, jangan auto-select
            refreshMusicList();       // biar muncul di list
        }
    }

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    private void updateUI(int steps, double distance, double speed) {
        if (steps >= 0) {
            tvSteps.setText(""+steps);
        }
        if (distance >= 0) tvDistance.setText(String.format("%.2f M", distance));
        if (speed >= 0) tvSpeed.setText(String.format("%.2f M/s", speed));
    }

    private void startTrackingServiceByMode() {
        if (mode == TrackingMode.INDOOR) startStepService();
        else startGpsService();
    }

    @SuppressLint("SetTextI18n")
    private void switchMode() {
        if (mode == TrackingMode.INDOOR) {
            stopStepService();
            startGpsService();
            mode = TrackingMode.OUTDOOR;
            btnToggle.setImageResource(R.drawable.toggle_outdoor);
        } else {
            stopGpsService();
            startStepService();
            mode = TrackingMode.INDOOR;
            btnToggle.setImageResource(R.drawable.toggle_mode);
        }
    }

    private void startStepService() {
        Intent intent = new Intent(this, StepMusicService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopStepService() {
        stopService(new Intent(this, StepMusicService.class));
    }

    private void startGpsService() {
        Intent intent = new Intent(this, GpsTrackingService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopGpsService() {
        stopService(new Intent(this, GpsTrackingService.class));
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("STEP_UPDATE");
        filter.addAction("GPS_UPDATE");
        filter.addAction("REFRESH_MUSIC_UI");
        registerReceiver(dataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void pauseStep(){
        Intent intent = new Intent("ACTION_PAUSE_STEP_SERVICE");
        sendBroadcast(intent);
        btnPause.setImageResource(R.drawable.ic_play);
        tvStatus.setText("Step Counting\nPAUSED");
        isPaused = true;
    }

    private void resumeStep(){
        Intent intent = new Intent("ACTION_RESUME_STEP_SERVICE");
        sendBroadcast(intent);
        btnPause.setImageResource(R.drawable.ic_pause);
        tvStatus.setText("Don't stop when you're tired.\nStop when you're done.");
        isPaused = false;
    }

    private void resetStep(){
        new AlertDialog.Builder(this)
                .setTitle("Reset Steps")
                .setMessage("Kembalikan perhitungan langkah ke 0?")
                .setPositiveButton("Ya", (dialog, which) -> {
                    Intent intent = new Intent("ACTION_RESET_STEP_SERVICE");
                    sendBroadcast(intent);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void stopStep(){
        new AlertDialog.Builder(this)
                .setTitle("Turn Off Step Counter")
                .setMessage("Yakin ingin keluar?")
                .setPositiveButton("Ya", (dialog, which) -> {
                    stopStepService();
                    stopGpsService();
                    musicController.stop();
                    finishAffinity();
                    System.exit(0);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void requestPermissions() {

        List<String> permissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(new String[0]),
                    REQUEST_PERMISSIONS
            );
        }
    }
    private boolean hasAllPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startTrackingServiceByMode(); // ⬅️ start service jika semua izin sudah granted
            } else {
                Toast.makeText(this, "Semua izin dibutuhkan agar aplikasi dapat berjalan", Toast.LENGTH_LONG).show();
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(dataReceiver);
    }
}
