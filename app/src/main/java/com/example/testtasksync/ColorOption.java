package com.example.testtasksync;
public class ColorOption {
    private String name;
    private int color;
    private boolean isSelected;

    public ColorOption(String name, int color) {
        this.name = name;
        this.color = color;
        this.isSelected = false;
    }

    // Getters and Setters
    public String getName() { return name; }
    public int getColor() { return color; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}

