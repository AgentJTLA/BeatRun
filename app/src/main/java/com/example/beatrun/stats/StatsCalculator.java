package com.example.beatrun.stats;

public class StatsCalculator {
    private final double strideLengthMeters;

    public StatsCalculator(double strideLengthMeters) {
        this.strideLengthMeters = strideLengthMeters;
    }

    public double calculateDistance(int steps) {
        return (steps * strideLengthMeters)/1000.0;
    }

    public double calculateSpeed(double distanceMeters, long elapsedMillis) {
        if (elapsedMillis <= 0) return 0;
        double elapsedSeconds = elapsedMillis / 1000.0;
        return (distanceMeters/1000.0) / (elapsedSeconds/3600.0);
    }
}

