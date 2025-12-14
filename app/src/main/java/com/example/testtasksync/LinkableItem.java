package com.example.testtasksync;

public class LinkableItem {
    private String id;
    private String title;
    private String type; // "note", "todo", "weekly"
    private String collection; // Firestore collection path

    public LinkableItem(String id, String title, String type, String collection) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.collection = collection;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getCollection() { return collection; }
}