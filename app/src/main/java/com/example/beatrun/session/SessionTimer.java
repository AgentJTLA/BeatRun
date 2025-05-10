package com.example.beatrun.session;

import android.os.SystemClock;

public class SessionTimer {

    private long startTime;

    public void start() {
        startTime = SystemClock.elapsedRealtime();
    }

    public long getElapsedMillis() {
        return SystemClock.elapsedRealtime() - startTime;
    }

    public void reset() {
        start();
    }
}
