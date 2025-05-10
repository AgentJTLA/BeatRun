package com.example.beatrun.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class StepTracker {
    private int totalSteps = 0;
    private int stepBuffer = 0;
    private boolean hasStarted = false;
    private long bufferStartTime = 0;
    public void processStep(SensorEvent event) {
        if (!hasStarted) {
            hasStarted = true;
            return; // abaikan langkah pertama karena bukan langkah user, tapi offset awal dari sensor
        }
        if (stepBuffer == 0) {
            bufferStartTime = System.currentTimeMillis(); //  Awal akumulasi langkah
        }


        totalSteps++;
        stepBuffer++;

    }

    public int getStepBuffer() {
        return stepBuffer;
    }

    public void resetBuffer() {
        stepBuffer = 0;
        bufferStartTime = 0;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void resetSteps() {
        totalSteps = 0;
        resetBuffer();
        hasStarted = false;
    }
    public long getBufferElapsedMillis() {
        if (bufferStartTime == 0) return 0;
        return System.currentTimeMillis() - bufferStartTime;
    }

}
