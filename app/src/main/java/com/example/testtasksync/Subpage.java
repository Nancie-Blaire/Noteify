package com.example.testtasksync;

public class Subpage {
    private String id;
    private String title;
    private String content;
    private String parentNoteId;
    private long timestamp;
    private int position;

    // Empty constructor for Firestore
    public Subpage() {}

    public Subpage(String id, String title, String content, String parentNoteId) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.parentNoteId = parentNoteId;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public int getPosition() { return position; }
    public String getParentNoteId() { return parentNoteId; }
    public long getTimestamp() { return timestamp; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setParentNoteId(String parentNoteId) { this.parentNoteId = parentNoteId; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setPosition(int position) { this.position = position; }
}