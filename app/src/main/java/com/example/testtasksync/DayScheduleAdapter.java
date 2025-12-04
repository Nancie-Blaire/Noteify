package com.example.testtasksync;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DayScheduleAdapter extends RecyclerView.Adapter<DayScheduleAdapter.ScheduleViewHolder> {

    private List<Schedule> scheduleList;
    private OnScheduleClickListener listener;
    private OnTaskCompletionListener completionListener;
    private boolean isDeleteMode = false;
    private List<Schedule> selectedSchedules = new ArrayList<>();

    public interface OnScheduleClickListener {
        void onScheduleClick(Schedule schedule);
        void onScheduleLongClick(Schedule schedule);
    }

    // ✅ Listener for checkbox clicks
    public interface OnTaskCompletionListener {
        void onTaskCompleted(Schedule schedule);
    }

    public DayScheduleAdapter(List<Schedule> scheduleList, OnScheduleClickListener listener) {
        this.scheduleList = scheduleList;
        this.listener = listener;
    }

    // ✅ Set completion listener
    public void setCompletionListener(OnTaskCompletionListener listener) {
        this.completionListener = listener;
    }

    public void setDeleteMode(boolean deleteMode) {
        this.isDeleteMode = deleteMode;
        if (!deleteMode) {
            selectedSchedules.clear();
        }
        notifyDataSetChanged();
    }

    public void setSelectedSchedules(List<Schedule> selected) {
        this.selectedSchedules = selected;
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        Schedule schedule = scheduleList.get(position);
        holder.bind(schedule, listener, completionListener, isDeleteMode, selectedSchedules.contains(schedule));
    }

    @Override
    public int getItemCount() {
        return scheduleList.size();
    }

    public static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        TextView scheduleTitle, scheduleDescription, categoryBadge;
        TextView scheduleTime, scheduleAmPm;
        ImageView reminderIcon;
        View categoryIndicator;
        CheckBox scheduleCheckbox;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            scheduleTitle = itemView.findViewById(R.id.scheduleTitle);
            scheduleDescription = itemView.findViewById(R.id.scheduleDescription);
            categoryBadge = itemView.findViewById(R.id.categoryBadge);
            reminderIcon = itemView.findViewById(R.id.reminderIcon);
            scheduleTime = itemView.findViewById(R.id.scheduleTime);
            scheduleAmPm = itemView.findViewById(R.id.scheduleAmPm);
            categoryIndicator = itemView.findViewById(R.id.categoryIndicator);
            scheduleCheckbox = itemView.findViewById(R.id.scheduleCheckbox);
        }

        public void bind(Schedule schedule, OnScheduleClickListener listener,
                         OnTaskCompletionListener completionListener, boolean isDeleteMode, boolean isSelected) {
            // Set title
            scheduleTitle.setText(schedule.getTitle());

            // Set time
            if (schedule.getTime() != null && !schedule.getTime().isEmpty()) {
                String[] timeParts = schedule.getTime().split(" ");
                if (timeParts.length >= 1) {
                    scheduleTime.setText(timeParts[0]); // e.g., "10:00"
                    scheduleTime.setVisibility(View.VISIBLE);
                    scheduleAmPm.setVisibility(View.VISIBLE);

                    if (timeParts.length >= 2) {
                        scheduleAmPm.setText(timeParts[1]); // e.g., "AM" or "PM"
                    } else {
                        scheduleAmPm.setText("");
                    }
                } else {
                    scheduleTime.setVisibility(View.GONE);
                    scheduleAmPm.setVisibility(View.GONE);
                }
            } else {
                scheduleTime.setVisibility(View.GONE);
                scheduleAmPm.setVisibility(View.GONE);
            }

            // Set description
            if (schedule.getDescription() != null && !schedule.getDescription().isEmpty()) {
                scheduleDescription.setText(schedule.getDescription());
                scheduleDescription.setVisibility(View.VISIBLE);
            } else {
                scheduleDescription.setVisibility(View.GONE);
            }

            // Set category badge and indicator
            String category = schedule.getCategory();
            int categoryColor = getCategoryColor(category);
            categoryBadge.setText(getCategoryDisplayName(category));
            categoryBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(categoryColor));
            categoryIndicator.setBackgroundColor(categoryColor);

            // Show reminder icon if reminder is set
            reminderIcon.setVisibility(schedule.hasReminder() ? View.VISIBLE : View.GONE);

            // ✅ Show checkbox only for weekly and todo_task categories
            String scheduleCategory = schedule.getCategory();
            android.util.Log.d("DayScheduleAdapter", "🔍 Binding schedule: " + schedule.getTitle() + " | Category: " + scheduleCategory);

            if ("weekly".equals(scheduleCategory) || "todo_task".equals(scheduleCategory)) {
                scheduleCheckbox.setVisibility(View.VISIBLE);

                android.util.Log.d("DayScheduleAdapter", "✅ Showing checkbox for: " + schedule.getTitle());

                // ✅ CRITICAL: Remove listener BEFORE setting checked state to prevent false triggers
                scheduleCheckbox.setOnCheckedChangeListener(null);
                scheduleCheckbox.setChecked(false); // Reset to unchecked

                // ✅ Set listener AFTER resetting checked state
                scheduleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    android.util.Log.d("DayScheduleAdapter", "📦 Checkbox changed! Checked: " + isChecked + " | Task: " + schedule.getTitle());

                    if (isChecked) {
                        if (completionListener != null) {
                            android.util.Log.d("DayScheduleAdapter", "🚀 Calling completionListener.onTaskCompleted()");
                            completionListener.onTaskCompleted(schedule);
                        } else {
                            android.util.Log.e("DayScheduleAdapter", "❌ completionListener is NULL!");
                        }
                    }
                });
            } else {
                scheduleCheckbox.setVisibility(View.GONE);
                scheduleCheckbox.setOnCheckedChangeListener(null); // Clear listener
            }

            // Highlight if selected in delete mode
            if (isDeleteMode && isSelected) {
                itemView.setBackgroundColor(Color.parseColor("#E3F2FD"));
            } else {
                itemView.setBackgroundColor(Color.WHITE);
            }

            // Click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onScheduleClick(schedule);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onScheduleLongClick(schedule);
                }
                return true;
            });
        }

        private int getCategoryColor(String category) {
            switch (category) {
                case "todo":
                    return Color.parseColor("#4CAF50"); // Green
                case "todo_task":
                    return Color.parseColor("#66BB6A"); // Light Green
                case "weekly":
                    return Color.parseColor("#2196F3"); // Blue
                case "holiday":
                    return Color.parseColor("#F44336"); // Red
                default:
                    return Color.parseColor("#4CAF50");
            }
        }

        private String getCategoryDisplayName(String category) {
            switch (category) {
                case "todo":
                    return "To-Do";
                case "todo_task":
                    return "To-Do Task";
                case "weekly":
                    return "Weekly";
                case "holiday":
                    return "Holiday";
                default:
                    return "To-Do";
            }
        }
    }
}