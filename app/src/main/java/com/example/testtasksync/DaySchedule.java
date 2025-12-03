package com.example.testtasksync;
// COMMENT

import com.google.firebase.Timestamp;

public class DaySchedule {
    private String day;
    private Timestamp date;
    private String time;
    private boolean hasReminder;
    private int reminderMinutes;
    private int scheduleNumber; // ✅ NEW: to track which schedule this is

    public DaySchedule() {
    }

    public DaySchedule(String day, Timestamp date, String time, boolean hasReminder,
                       int reminderMinutes, int scheduleNumber) {
        this.day = day;
        this.date = date;
        this.time = time;
        this.hasReminder = hasReminder;
        this.reminderMinutes = reminderMinutes;
        this.scheduleNumber = scheduleNumber;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public Timestamp getDate() {
        return date;
    }

    public void setDate(Timestamp date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public boolean isHasReminder() {
        return hasReminder;
    }

    public void setHasReminder(boolean hasReminder) {
        this.hasReminder = hasReminder;
    }

    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }
 
    public int getScheduleNumber() {
        return scheduleNumber;
    }

    public void setScheduleNumber(int scheduleNumber) {
        this.scheduleNumber = scheduleNumber;
    }
}