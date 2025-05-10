package com.example.beatrun.model;

public class Music {
    private int id;
    private String title;
    private String filePath;

    public Music() {}

    public Music(String title, String filePath) {
        this.title = title;
        this.filePath = filePath;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    @Override
    public String toString() { return title; }
}