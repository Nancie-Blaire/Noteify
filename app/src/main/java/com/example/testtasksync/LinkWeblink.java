package com.example.testtasksync;

import java.util.HashMap;
import java.util.Map;

public class LinkWeblink {
    private String id;
    private String url;
    private String title;
    private String description;
    private String faviconUrl;
    private String backgroundColor;
    private long position;
    private long timestamp;

    public LinkWeblink() {
        this.backgroundColor = "#FFFFFF";
        this.timestamp = System.currentTimeMillis();
    }

    public LinkWeblink(String url, String title, String description, String faviconUrl,
                       String backgroundColor, long position) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.faviconUrl = faviconUrl;
        this.backgroundColor = backgroundColor;
        this.position = position;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
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

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    // Convert to Firestore map
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("url", url);
        map.put("title", title);
        map.put("description", description);
        map.put("faviconUrl", faviconUrl);
        map.put("backgroundColor", backgroundColor);
        map.put("position", position);
        map.put("timestamp", timestamp);
        return map;
    }

    // Create from Firestore map
    public static LinkWeblink fromMap(Map<String, Object> map) {
        LinkWeblink link = new LinkWeblink();
        link.url = (String) map.get("url");
        link.title = (String) map.get("title");
        link.description = (String) map.get("description");
        link.faviconUrl = (String) map.get("faviconUrl");
        link.backgroundColor = (String) map.get("backgroundColor");

        if (map.get("position") != null) {
            link.position = (Long) map.get("position");
        }
        if (map.get("timestamp") != null) {
            link.timestamp = (Long) map.get("timestamp");
        }

        return link;
    }
}