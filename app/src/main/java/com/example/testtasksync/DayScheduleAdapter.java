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
    private boolean isDeleteMode = false;
    private List<Schedule> selectedSchedules = new ArrayList<>();

    public interface OnScheduleClickListener {
        void onScheduleClick(Schedule schedule);
        void onScheduleLongClick(Schedule schedule);
    }

    public DayScheduleAdapter(List<Schedule> scheduleList, OnScheduleClickListener listener) {
        this.scheduleList = scheduleList;
        this.listener = listener;
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
                .inflate(R.layout.item_schedule_compact, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        Schedule schedule = scheduleList.get(position);
        holder.bind(schedule, listener, isDeleteMode, selectedSchedules.contains(schedule));
    }

    @Override
    public int getItemCount() {
        return scheduleList.size();
    }

    public static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        CheckBox scheduleCheckbox;
        TextView scheduleTitle, scheduleDescription, categoryBadge;
        ImageView reminderIcon;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            scheduleCheckbox = itemView.findViewById(R.id.scheduleCheckbox);
            scheduleTitle = itemView.findViewById(R.id.scheduleTitle);
            scheduleDescription = itemView.findViewById(R.id.scheduleDescription);
            categoryBadge = itemView.findViewById(R.id.categoryBadge);
            reminderIcon = itemView.findViewById(R.id.reminderIcon);
        }

        public void bind(Schedule schedule, OnScheduleClickListener listener, boolean isDeleteMode, boolean isSelected) {
            // Set title
            scheduleTitle.setText(schedule.getTitle());

            // Set description
            if (schedule.getDescription() != null && !schedule.getDescription().isEmpty()) {
                scheduleDescription.setText(schedule.getDescription());
                scheduleDescription.setVisibility(View.VISIBLE);
            } else {
                scheduleDescription.setVisibility(View.GONE);
            }

            // Set category badge
            String category = schedule.getCategory();
            int categoryColor = getCategoryColor(category);
            categoryBadge.setText(getCategoryDisplayName(category));
            categoryBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(categoryColor));

            // Show reminder icon if reminder is set
            reminderIcon.setVisibility(schedule.hasReminder() ? View.VISIBLE : View.GONE);

            // Handle delete mode vs normal mode
            if (isDeleteMode) {
                scheduleCheckbox.setVisibility(View.VISIBLE);
                scheduleCheckbox.setChecked(isSelected);
                scheduleCheckbox.setOnCheckedChangeListener(null); // Remove listener to avoid loops
            } else {
                scheduleCheckbox.setVisibility(View.VISIBLE);
                scheduleCheckbox.setChecked(schedule.isCompleted());

                // Normal checkbox behavior (mark as complete)
                scheduleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    schedule.setCompleted(isChecked);
                    // TODO: Update in Firebase
                });
            }

            // Apply completed style
            if (schedule.isCompleted() && !isDeleteMode) {
                scheduleTitle.setTextColor(Color.GRAY);
                scheduleTitle.setPaintFlags(scheduleTitle.getPaintFlags() |
                        android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                scheduleTitle.setTextColor(Color.BLACK);
                scheduleTitle.setPaintFlags(scheduleTitle.getPaintFlags() &
                        (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            }

            // Highlight if selected in delete mode
            if (isDeleteMode && isSelected) {
                itemView.setBackgroundColor(Color.parseColor("#E3F2FD"));
            } else {
                itemView.setBackgroundResource(R.drawable.schedule_item_background);
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