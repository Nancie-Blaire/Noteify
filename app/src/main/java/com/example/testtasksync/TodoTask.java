package com.example.testtasksync;

import java.util.Date;

public class TodoTask {
    private String id;
    private String taskText;
    private boolean isCompleted;
    private int position;

    // Schedule fields
    private Date scheduleDate;
    private String scheduleTime; // Format: "HH:mm" (e.g., "14:30")
    private boolean hasNotification;
    private int notificationMinutes; // Minutes before scheduled time

    public TodoTask() {
        // Required empty constructor for Firebase
        this.hasNotification = false;
        this.notificationMinutes = 30; // Default 30 minutes before
    }

    public TodoTask(String id, String taskText, boolean isCompleted, int position) {
        this.id = id;
        this.taskText = taskText;
        this.isCompleted = isCompleted;
        this.position = position;
        this.hasNotification = false;
        this.notificationMinutes = 30;
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

    public Date getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(Date scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public String getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(String scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    public boolean hasNotification() {
        return hasNotification;
    }

    public void setHasNotification(boolean hasNotification) {
        this.hasNotification = hasNotification;
    }

    public int getNotificationMinutes() {
        return notificationMinutes;
    }

    public void setNotificationMinutes(int notificationMinutes) {
        this.notificationMinutes = notificationMinutes;
    }
}