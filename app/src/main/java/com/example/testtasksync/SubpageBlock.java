package com.example.testtasksync;

public class SubpageBlock {
    private String blockId;
    private String type; // "text", "bullet", "numbered", "checkbox", "divider", "heading1", "heading2", "heading3"
    private String content;
    private int order;
    private int indentLevel;
    private boolean checked;
    private String imageUrl; // For image blocks
    private String linkUrl; // For link blocks
    private long timestamp;

    private String imageId;
    private boolean isChunked;
    private int sizeKB;
    private String linkBackgroundColor;
    private String linkDescription;
    private String dividerStyle;
    private String fontStyle;  // "bold", "italic", "boldItalic", null
    private String fontColor;  // Hex color code (e.g., "#E53935")

    // ✅ NEW GETTERS AND SETTERS
    public String getFontStyle() {
        return fontStyle;
    }

    public void setFontStyle(String fontStyle) {
        this.fontStyle = fontStyle;
    }

    public String getFontColor() {
        return fontColor != null ? fontColor : "#333333"; // Default black
    }

    public void setFontColor(String fontColor) {
        this.fontColor = fontColor;
    }


    public String getDividerStyle() { return dividerStyle; }
    public void setDividerStyle(String dividerStyle) { this.dividerStyle = dividerStyle; }

//link
public String getLinkBackgroundColor() { return linkBackgroundColor; }
    public void setLinkBackgroundColor(String color) { linkBackgroundColor = color; }

    public String getLinkDescription() { return linkDescription; }
    public void setLinkDescription(String desc) { linkDescription = desc; }
    //images
    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    public boolean isChunked() { return isChunked; }
    public void setChunked(boolean chunked) { isChunked = chunked; }
    public int getSizeKB() { return sizeKB; }
    public void setSizeKB(int sizeKB) { this.sizeKB = sizeKB; }

    // Empty constructor for Firestore
    public SubpageBlock() {
    }

    // Constructor
    public SubpageBlock(String blockId, String type, String content, int order) {
        this.blockId = blockId;
        this.type = type;
        this.content = content;
        this.order = order;
        this.indentLevel = 0;
        this.checked = false;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getIndentLevel() {
        return indentLevel;
    }

    public void setIndentLevel(int indentLevel) {
        this.indentLevel = indentLevel;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}