package com.example.testtasksync;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class NoteBlock {
    public enum BlockType {
        TEXT,
        HEADING_1,
        HEADING_2,
        HEADING_3,
        BULLET,
        NUMBERED,
        CHECKBOX,
        IMAGE,
        DIVIDER,
        SUBPAGE,
        LINK,
        LINK_TO_PAGE
    }

    private String id;
    private BlockType type;
    private String content;
    private int indentLevel;
    private boolean isChecked;
    private String imageId;
    private String subpageId;
    private String linkUrl;
    private String dividerStyle;
    private int position;
    private String styleData; // JSON string for style info (bold, italic, etc.)
    private String fontColor; // ✅ NEW: Font color hex code

    // For image blocks
    private String base64Data;
    private boolean isChunked;
    private int sizeKB;

    // For numbered lists
    private int listNumber;
    private String linkBackgroundColor;
    private String linkDescription;

    //LINK TO PAGE
    // For LINK_TO_PAGE blocks
    private String linkedPageId;
    private String linkedPageType;      // "note", "todo", "weekly"
    private String linkedPageCollection; // Firestore collection

    // Getters and Setters
    public String getLinkedPageId() { return linkedPageId; }
    public void setLinkedPageId(String linkedPageId) { this.linkedPageId = linkedPageId; }

    public String getLinkedPageType() { return linkedPageType; }
    public void setLinkedPageType(String linkedPageType) { this.linkedPageType = linkedPageType; }

    public String getLinkedPageCollection() { return linkedPageCollection; }
    public void setLinkedPageCollection(String linkedPageCollection) {
        this.linkedPageCollection = linkedPageCollection;
    }


    private String fontStyle;
    public String getFontStyle() {
        return fontStyle;
    }

    public void setFontStyle(String fontStyle) {
        this.fontStyle = fontStyle;
    }

    public String getLinkBackgroundColor() { return linkBackgroundColor; }
    public void setLinkBackgroundColor(String linkBackgroundColor) {
        this.linkBackgroundColor = linkBackgroundColor;
    }
    public String getLinkDescription() { return linkDescription; }
    public void setLinkDescription(String linkDescription) {
        this.linkDescription = linkDescription;
    }

    // ✅ NEW: Font color getter/setter
    public String getFontColor() { return fontColor; }
    public void setFontColor(String fontColor) { this.fontColor = fontColor; }

    // Constructor
    public NoteBlock(String id, BlockType type) {
        this.id = id;
        this.type = type;
        this.content = "";
        this.indentLevel = 0;
        this.isChecked = false;
        this.listNumber = 1;
        this.isChunked = false;
        this.sizeKB = 0;
        this.fontColor = "#333333";
        this.styleData = null;// Default black color
    }

    // Empty constructor for Firestore
    public NoteBlock() {
        this.id = System.currentTimeMillis() + "";
        this.type = BlockType.TEXT;
        this.content = "";
        this.indentLevel = 0;
        this.isChunked = false;
        this.fontColor = "#333333"; // Default black color
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public BlockType getType() { return type; }
    public void setType(BlockType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getIndentLevel() { return indentLevel; }
    public void setIndentLevel(int indentLevel) { this.indentLevel = indentLevel; }

    public boolean isChecked() { return isChecked; }
    public void setChecked(boolean checked) { isChecked = checked; }

    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }

    public String getSubpageId() { return subpageId; }
    public void setSubpageId(String subpageId) { this.subpageId = subpageId; }

    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }

    public String getDividerStyle() { return dividerStyle; }
    public void setDividerStyle(String dividerStyle) { this.dividerStyle = dividerStyle; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getStyleData() { return styleData; }
    public void setStyleData(String styleData) { this.styleData = styleData; }

    public int getListNumber() { return listNumber; }
    public void setListNumber(int listNumber) { this.listNumber = listNumber; }

    // Image-specific getters/setters
    public String getBase64Data() { return base64Data; }
    public void setBase64Data(String base64Data) { this.base64Data = base64Data; }

    public boolean isChunked() { return isChunked; }
    public void setChunked(boolean chunked) { isChunked = chunked; }

    public int getSizeKB() { return sizeKB; }
    public void setSizeKB(int sizeKB) { this.sizeKB = sizeKB; }

    // Convert to Firestore map
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("type", type.name());
        map.put("content", content);
        map.put("indentLevel", indentLevel);
        map.put("isChecked", isChecked);
        map.put("imageId", imageId);
        map.put("subpageId", subpageId);
        map.put("linkUrl", linkUrl);
        map.put("dividerStyle", dividerStyle);
        map.put("position", position);
        map.put("styleData", styleData);
        map.put("listNumber", listNumber);
        map.put("base64Data", base64Data);
        map.put("isChunked", isChunked);
        map.put("sizeKB", sizeKB);
        map.put("linkBackgroundColor", linkBackgroundColor);
        map.put("linkDescription", linkDescription);
        map.put("fontStyle", fontStyle);
        map.put("fontColor", fontColor);
        map.put("styleData", styleData);
        map.put("linkedPageId", linkedPageId);
        map.put("linkedPageType", linkedPageType);
        map.put("linkedPageCollection", linkedPageCollection);
        return map;
    }

    // Create from Firestore map
    public static NoteBlock fromMap(Map<String, Object> map) {
        NoteBlock block = new NoteBlock();
        block.id = (String) map.get("id");
        block.type = BlockType.valueOf((String) map.get("type"));
        block.content = (String) map.get("content");
        block.linkBackgroundColor = (String) map.get("linkBackgroundColor");
        block.linkDescription = (String) map.get("linkDescription");
        block.fontStyle = (String) map.get("fontStyle");
        block.fontColor = (String) map.get("fontColor");
        block.linkedPageId = (String) map.get("linkedPageId");
        block.linkedPageType = (String) map.get("linkedPageType");
        block.linkedPageCollection = (String) map.get("linkedPageCollection");

        // ✅ MIGRATION: If fontStyle is null but styleData exists, migrate it
        if (block.fontStyle == null && map.get("styleData") != null) {
            try {
                JSONObject styleJson = new JSONObject((String) map.get("styleData"));
                block.fontStyle = styleJson.optString("fontStyle", null);
            } catch (Exception e) {
                // Ignore migration errors
            }
        }

        // Set default color if not present
        if (block.fontColor == null || block.fontColor.isEmpty()) {
            block.fontColor = "#333333";
        }
        if (map.get("indentLevel") != null) {
            block.indentLevel = ((Long) map.get("indentLevel")).intValue();
        }

        if (map.get("isChecked") != null) {
            block.isChecked = (Boolean) map.get("isChecked");
        }

        block.imageId = (String) map.get("imageId");
        block.subpageId = (String) map.get("subpageId");
        block.linkUrl = (String) map.get("linkUrl");
        block.dividerStyle = (String) map.get("dividerStyle");

        if (map.get("position") != null) {
            block.position = ((Long) map.get("position")).intValue();
        }

        block.styleData = (String) map.get("styleData");

        if (map.get("listNumber") != null) {
            block.listNumber = ((Long) map.get("listNumber")).intValue();
        }

        block.base64Data = (String) map.get("base64Data");

        if (map.get("isChunked") != null) {
            block.isChunked = (Boolean) map.get("isChunked");
        }

        if (map.get("sizeKB") != null) {
            block.sizeKB = ((Long) map.get("sizeKB")).intValue();
        }

        // ✅ Set default color if not present
        if (block.fontColor == null || block.fontColor.isEmpty()) {
            block.fontColor = "#333333";
        }

        return block;
    }
}