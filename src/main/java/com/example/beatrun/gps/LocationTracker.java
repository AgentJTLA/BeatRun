package com.example.beatrun.gps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.*;

public class LocationTracker {

    public interface OnLocationUpdateListener {
        void onLocationUpdate(Location location);
    }

    private final Context context;
    private final FusedLocationProviderClient fusedClient;
    private final LocationRequest locationRequest;
    private OnLocationUpdateListener listener;

    public LocationTracker(Context context) {
        this.context = context;
        this.fusedClient = LocationServices.getFusedLocationProviderClient(context);

        this.locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .build();

    }

    public void setListener(OnLocationUpdateListener listener) {
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public void start() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } else {
            Log.w("LocationTracker", "Permission ACCESS_FINE_LOCATION not granted.");
        }
    }

    public void stop() {
        fusedClient.removeLocationUpdates(locationCallback);
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            if (listener != null && result != null && result.getLastLocation() != null) {
                Log.d("LocationTracker", "Location received: " + result.getLastLocation());
                listener.onLocationUpdate(result.getLastLocation());
            }
        }
    };
}
