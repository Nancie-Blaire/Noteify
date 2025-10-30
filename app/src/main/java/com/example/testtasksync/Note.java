package com.example.testtasksync;



public class Note {
    private String id;
    private String title;
    private String content;
    private boolean isPrio;
    private boolean isStarred;


    public Note(String id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
    } // Firestore requires empty constructor

    public Note(String id, String title, String content, boolean isPrio) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.isPrio = isPrio;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public boolean isStarred() {
        return isStarred;
    }

    public void setStarred(boolean starred) {
        isStarred = starred;
    }
}
