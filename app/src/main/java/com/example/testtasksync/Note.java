package com.example.testtasksync;

import java.util.ArrayList;
import java.util.List;

public class Note {
    private String id;
    private String title;
    private String content;
    private boolean isPrio;
    private boolean isStarred;
    private List<String> subpageIds; // Optional: to track subpage IDs

    // Empty constructor for Firestore
    public Note() {
        this.subpageIds = new ArrayList<>();
    }

    public Note(String id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.subpageIds = new ArrayList<>();
    }

    public Note(String id, String title, String content, boolean isPrio) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.isPrio = isPrio;
        this.subpageIds = new ArrayList<>();
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isPrio() { return isPrio; }
    public boolean isStarred() { return isStarred; }
    public List<String> getSubpageIds() { return subpageIds; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setPrio(boolean prio) { isPrio = prio; }
    public void setStarred(boolean starred) { isStarred = starred; }
    public void setSubpageIds(List<String> subpageIds) { this.subpageIds = subpageIds; }

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