package com.example.testtasksync;

import java.util.ArrayList;
import java.util.List;

public class Note {
    private String id;
    private String title;
    private String content;
    private boolean isPrio;
    private boolean isStarred;
    private boolean isLocked;
    private List<String> subpageIds;
    private String sourceId;
    private long timestamp;

    // NEW: Fields for bin functionality
    private long deletedAt;
    private String category;  // For schedules: "todo" or "weekly"

    // Empty constructor for Firestore
    public Note() {
        this.subpageIds = new ArrayList<>();
        this.isLocked = false;
    }

    public Note(String id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.subpageIds = new ArrayList<>();
        this.isLocked = false;
    }

    public Note(String id, String title, String content, boolean isPrio) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.isPrio = isPrio;
        this.subpageIds = new ArrayList<>();
        this.isLocked = false;
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isPrio() { return isPrio; }
    public boolean isStarred() { return isStarred; }
    public boolean isLocked() { return isLocked; }
    public List<String> getSubpageIds() { return subpageIds; }
    public long getTimestamp() { return timestamp; }
    public String getSourceId() { return sourceId; }

    // NEW: Getters for bin functionality
    public long getDeletedAt() { return deletedAt; }
    public String getCategory() { return category; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setPrio(boolean prio) { isPrio = prio; }
    public void setStarred(boolean starred) { isStarred = starred; }
    public void setLocked(boolean locked) { isLocked = locked; }
    public void setSubpageIds(List<String> subpageIds) { this.subpageIds = subpageIds; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    // NEW: Setters for bin functionality
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }
    public void setCategory(String category) { this.category = category; }

    // Helper methods
    public void addSubpageId(String subpageId) {
        if (subpageIds == null) {
            subpageIds = new ArrayList<>();
        }
        subpageIds.add(subpageId);
    }

    public boolean hasSubpages() {
        return subpageIds != null && !subpageIds.isEmpty();
    }
}