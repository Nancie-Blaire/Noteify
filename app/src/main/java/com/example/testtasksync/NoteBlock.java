package com.example.testtasksync;

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
        LINK
    }

    private String id;
    private BlockType type;
    private String content;
    private int indentLevel;
    private boolean isChecked; // For checkboxes
    private String imageId; // For images
    private String subpageId; // For subpages
    private String linkUrl; // For links
    private String dividerStyle; // For dividers
    private int position;
    private String styleData; // JSON string for additional style info

    // For numbered lists - track number
    private int listNumber;

    // Constructor
    public NoteBlock(String id, BlockType type) {
        this.id = id;
        this.type = type;
        this.content = "";
        this.indentLevel = 0;
        this.isChecked = false;
        this.listNumber = 1;
    }

    // Empty constructor for Firestore
    public NoteBlock() {
        this.id = System.currentTimeMillis() + "";
        this.type = BlockType.TEXT;
        this.content = "";
        this.indentLevel = 0;
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
        return map;
    }

    // Create from Firestore map
    public static NoteBlock fromMap(Map<String, Object> map) {
        NoteBlock block = new NoteBlock();
        block.id = (String) map.get("id");
        block.type = BlockType.valueOf((String) map.get("type"));
        block.content = (String) map.get("content");
        block.indentLevel = ((Long) map.get("indentLevel")).intValue();
        block.isChecked = (Boolean) map.get("isChecked");
        block.imageId = (String) map.get("imageId");
        block.subpageId = (String) map.get("subpageId");
        block.linkUrl = (String) map.get("linkUrl");
        block.dividerStyle = (String) map.get("dividerStyle");
        block.position = ((Long) map.get("position")).intValue();
        block.styleData = (String) map.get("styleData");
        if (map.get("listNumber") != null) {
            block.listNumber = ((Long) map.get("listNumber")).intValue();
        }
        return block;
    }
}