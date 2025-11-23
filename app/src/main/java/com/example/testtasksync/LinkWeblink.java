package com.example.testtasksync;

public class LinkWeblink {
    private String id;
    private String url;
    private String title;
    private String description;
    private String faviconUrl;
    private String backgroundColor;
    private long position;

    public LinkWeblink() {}

    public LinkWeblink(String url, String title, String description, String faviconUrl, String backgroundColor, long position) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.faviconUrl = faviconUrl;
        this.backgroundColor = backgroundColor;
        this.position = position;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFaviconUrl() { return faviconUrl; }
    public void setFaviconUrl(String faviconUrl) { this.faviconUrl = faviconUrl; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

    public long getPosition() { return position; }
    public void setPosition(long position) { this.position = position; }
}