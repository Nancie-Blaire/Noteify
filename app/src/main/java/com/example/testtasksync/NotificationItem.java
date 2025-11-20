package com.example.testtasksync;

import java.util.Date;

public class NotificationItem {
    private String sourceId; // todoList ID or weeklyPlan ID
    private String title;
    private String taskText;
    private Date dueDate;
    private String dueTime;
    private String type; // "todo" or "weekly"

    public NotificationItem(String sourceId, String title, String taskText,
                            Date dueDate, String dueTime, String type) {
        this.sourceId = sourceId;
        this.title = title;
        this.taskText = taskText;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.type = type;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTaskText() {
        return taskText;
    }

    public void setTaskText(String taskText) {
        this.taskText = taskText;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }

    public String getDueTime() {
        return dueTime;
    }

    public void setDueTime(String dueTime) {
        this.dueTime = dueTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}