package com.example.testtasksync;

public class Bookmark {
    private String id;
    private String blockId;
    private String text;
    private String note;
    private String color;
    private String style; // "highlight" or "underline"
    private long timestamp;
    private int startIndex;
    private int endIndex;
    public String getBlockId() { return blockId; }
    public void setBlockId(String blockId) { this.blockId = blockId; }
    public Bookmark() {
        // Required empty constructor for Firestore
    }

    public Bookmark(String text, String note, String color, String style, int startIndex, int endIndex) {
        this.text = text;
        this.note = note;
        this.color = color;
        this.style = style;
        this.timestamp = System.currentTimeMillis();
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    //  go lia
}