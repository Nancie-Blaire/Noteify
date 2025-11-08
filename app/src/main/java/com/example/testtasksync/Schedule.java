package com.example.testtasksync;

import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Schedule {
    private String id;
    private String title;
    private String description;
    private Timestamp date;
    private String time; // Format: "HH:mm"
    private String category; // "event", "todo", "weekly", "holiday"
    private boolean isCompleted;
    private Timestamp createdAt;
    private String sourceId; // Reference to original todo/weekly task
    private boolean hasReminder;
    private int reminderMinutes;

    public Schedule() {
        // Required empty constructor for Firebase
    }

    public Schedule(String id, String title, String description, Timestamp date,
                    String time, String category, boolean isCompleted, String sourceId,
                    boolean hasReminder, int reminderMinutes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.date = date;
        this.time = time;
        this.category = category;
        this.isCompleted = isCompleted;
        this.sourceId = sourceId;
        this.hasReminder = hasReminder;
        this.reminderMinutes = reminderMinutes;
    }

    // Helper method to get formatted date string
    public String getFormattedDate() {
        if (date != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return sdf.format(date.toDate());
        }
        return "";
    }

    // Helper method to get formatted time string
    public String getFormattedTime() {
        if (time != null && !time.isEmpty()) {
            try {
                SimpleDateFormat input = new SimpleDateFormat("HH:mm", Locale.getDefault());
                SimpleDateFormat output = new SimpleDateFormat("h:mm a", Locale.getDefault());
                Date d = input.parse(time);
                return output.format(d);
            } catch (Exception e) {
                return time;
            }
        }
        return "All day";
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getDate() { return date; }
    public void setDate(Timestamp date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public boolean hasReminder() { return hasReminder; }
    public void setHasReminder(boolean hasReminder) { this.hasReminder = hasReminder; }

    public int getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }
}