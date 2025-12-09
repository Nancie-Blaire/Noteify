package com.example.testtasksync;

import java.util.Date;

public class WeeklyTask {
    private String id;
    private String day;
    private String taskText;
    private boolean isCompleted;
    private int position;

    // ✅ NEW: Task-level schedule fields
    private Date scheduleDate;
    private String scheduleTime;
    private boolean hasNotification;
    private int notificationMinutes;

    public WeeklyTask() {
        this.id = "";
        this.day = "";
        this.taskText = "";
        this.isCompleted = false;
        this.position = 0;
        this.scheduleDate = null;
        this.scheduleTime = "";
        this.hasNotification = false;
        this.notificationMinutes = 60;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
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

    // ✅ NEW: Schedule getters and setters
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