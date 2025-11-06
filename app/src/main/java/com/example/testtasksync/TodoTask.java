package com.example.testtasksync;

public class TodoTask {
    private String id;
    private String taskText;
    private boolean isCompleted;
    private int position;

    public TodoTask() {
        // Required empty constructor for Firebase
    }

    public TodoTask(String id, String taskText, boolean isCompleted, int position) {
        this.id = id;
        this.taskText = taskText;
        this.isCompleted = isCompleted;
        this.position = position;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskText() {
        return taskText;
    }

    public void setTaskText(String taskText) {
        this.taskText = taskText;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}