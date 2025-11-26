package com.example.testtasksync;

import java.util.HashMap;
import java.util.Map;

public class Table {
    private String id;
    private int position;
    private Map<String, String> cellContents; // "row-col" -> content
    private Map<String, Integer> cellColors; // "row-col" -> color
    private int columnCount;
    private int rowCount;
    private long timestamp;

    public Table() {
        this.cellContents = new HashMap<>();
        this.cellColors = new HashMap<>();
        this.columnCount = 3;
        this.rowCount = 4;
        this.timestamp = System.currentTimeMillis();
    }

    public Table(int position, int rows, int cols) {
        this.position = position;
        this.rowCount = rows;
        this.columnCount = cols;
        this.cellContents = new HashMap<>();
        this.cellColors = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    // ✅ NEW: Method to set cell content by row/col
    public void setCellContent(int row, int col, String content) {
        String key = row + "-" + col;
        if (content != null && !content.trim().isEmpty()) {
            cellContents.put(key, content);
        } else {
            cellContents.remove(key);
        }
    }

    // ✅ NEW: Method to get cell content by row/col
    public String getCellContent(int row, int col) {
        String key = row + "-" + col;
        return cellContents.getOrDefault(key, "");
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public Map<String, String> getCellContents() { return cellContents; }
    public void setCellContents(Map<String, String> cellContents) {
        this.cellContents = cellContents != null ? cellContents : new HashMap<>();
    }

    public Map<String, Integer> getCellColors() { return cellColors; }
    public void setCellColors(Map<String, Integer> cellColors) {
        this.cellColors = cellColors != null ? cellColors : new HashMap<>();
    }

    public int getColumnCount() { return columnCount; }
    public void setColumnCount(int columnCount) { this.columnCount = columnCount; }

    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}