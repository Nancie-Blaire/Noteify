package com.example.testtasksync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Table {
    private String id;
    private int position;
    private List<List<String>> cellContents; // Cell text content
    private Map<String, Integer> cellColors; // "row-col" -> color
    private int columnCount;
    private int rowCount;
    private long timestamp;

    public Table() {
        this.cellContents = new ArrayList<>();
        this.cellColors = new HashMap<>();
        this.columnCount = 3;
        this.rowCount = 4;
        this.timestamp = System.currentTimeMillis();
    }

    public Table(int position, int rows, int cols) {
        this.position = position;
        this.rowCount = rows;
        this.columnCount = cols;
        this.cellContents = new ArrayList<>();
        this.cellColors = new HashMap<>();
        this.timestamp = System.currentTimeMillis();

        // Initialize empty cells
        for (int i = 0; i < rows; i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j < cols; j++) {
                row.add("");
            }
            cellContents.add(row);
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public List<List<String>> getCellContents() { return cellContents; }
    public void setCellContents(List<List<String>> cellContents) { this.cellContents = cellContents; }

    public Map<String, Integer> getCellColors() { return cellColors; }
    public void setCellColors(Map<String, Integer> cellColors) { this.cellColors = cellColors; }

    public int getColumnCount() { return columnCount; }
    public void setColumnCount(int columnCount) { this.columnCount = columnCount; }

    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}