package com.example.beatrun.controller;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.Nullable;

import java.io.IOException;

public class MusicController {
    private final MediaPlayer mediaPlayer;
    private final TextView statusView;
    private final TextView nowPlaying;
    private final Context context;
    private String currentPath;
    private String currentTitle;
    private boolean autoPlay = true;

    public MusicController(Context context, @Nullable TextView statusView, @Nullable TextView nowPlaying) {
        this.context = context;
        this.statusView = statusView;
        this.nowPlaying = nowPlaying;
        this.mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnPreparedListener(mp -> {
            if (autoPlay) {
                mp.start();
                if (statusView != null) {
                    statusView.setText("Now Playing: " + getCurrentTitle());
                    nowPlaying.setText(""+getCurrentTitle());
                }
            } else {
                if (statusView != null) {
                    statusView.setText("Ready: " + getCurrentTitle());
                    nowPlaying.setText(""+getCurrentTitle());
                }
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            if (statusView != null) {
                statusView.setText("Error playing music");
                nowPlaying.setText("No song currently playing");
            }
            Log.e("MusicController", "Error code: " + what + ", extra: " + extra);
            return true;
        });
    }

    public void setMusic(String uriString, String title, boolean autoPlay) {
        try {
            this.autoPlay = autoPlay;
            this.currentTitle = title != null ? title : extractTitleFromPath(uriString);
            mediaPlayer.reset();
            Uri uri = Uri.parse(uriString);
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.prepareAsync();
            currentPath = uriString;
        } catch (IOException e) {
            Log.e("MusicController", "Error setting data source", e);
            if (statusView != null) {
                statusView.setText("Invalid music file");
                nowPlaying.setText("No song currently playing");
            }
        }
    }

    public void play() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            if (statusView != null) {
                statusView.setText("Now Playing: " + getCurrentTitle());
                nowPlaying.setText(""+getCurrentTitle());
            }
        }
    }

    public void pause(String reason) {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (statusView != null) {
                statusView.setText("Paused: " + reason);
                nowPlaying.setText(""+getCurrentTitle());
            }
        }
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    public String getCurrentTitle() {
        return currentTitle != null ? currentTitle : "No track selected";
    }

    private String extractTitleFromPath(String path){
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    public void stop() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            if (statusView != null) {
                statusView.setText("Stopped");
                nowPlaying.setText("No song currently playing");
            }
        }
    }

    public void clear(){
        currentPath = null;
        currentTitle = null;
        mediaPlayer.reset();
        if (statusView != null) statusView.setText("No song currently playing");
        if (nowPlaying != null) nowPlaying.setText("No song currently playing");
    }

    public void release() {
        mediaPlayer.release();
    }
}
