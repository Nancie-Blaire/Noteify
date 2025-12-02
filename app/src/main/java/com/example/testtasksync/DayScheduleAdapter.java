package com.example.testtasksync;

import android.content.Context;
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
import java.util.Collections;
import java.util.List;

public class DayScheduleAdapter extends RecyclerView.Adapter<DayScheduleAdapter.ScheduleViewHolder> {

    private List<Schedule> scheduleList;
    private OnScheduleClickListener listener;
    private OnTaskCompletionListener completionListener;
    private boolean isDeleteMode = false;
    private List<Schedule> selectedSchedules = new ArrayList<>();
    private Context context;

    public interface OnScheduleClickListener {
        void onScheduleClick(Schedule schedule);
        void onScheduleLongClick(Schedule schedule);
    }

    public interface OnTaskCompletionListener {
        void onTaskCompleted(Schedule schedule);
    }

    public DayScheduleAdapter(List<Schedule> scheduleList, OnScheduleClickListener listener) {
        this.scheduleList = scheduleList;
        this.listener = listener;
    }

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

    // ✅ UPDATED: Method to update the entire list
    public void updateScheduleList(List<Schedule> newScheduleList) {
        this.scheduleList = newScheduleList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_schedule, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        Schedule schedule = scheduleList.get(position);
        holder.bind(schedule, listener, completionListener, isDeleteMode,
                selectedSchedules.contains(schedule), context);
    }

    @Override
    public int getItemCount() {
        return scheduleList.size();
    }

    // ✅ IMPROVED: Method to remove item with better notification
    public void removeItem(int position) {
        if (position >= 0 && position < scheduleList.size()) {
            scheduleList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, scheduleList.size() - position);
        }
    }

    // ✅ IMPROVED: Method to remove single schedule by object
    public void removeSchedule(Schedule schedule) {
        int position = -1;

        // Find the position by ID to ensure we're removing the correct item
        for (int i = 0; i < scheduleList.size(); i++) {
            if (scheduleList.get(i).getId().equals(schedule.getId())) {
                position = i;
                break;
            }
        }

        if (position >= 0 && position < scheduleList.size()) {
            scheduleList.remove(position);
            notifyItemRemoved(position);
            // Notify all items after this position to rebind
            if (position < scheduleList.size()) {
                notifyItemRangeChanged(position, scheduleList.size() - position);
            }
        }
    }

    // ✅ IMPROVED: Method to remove multiple items with proper animation
    public void removeItems(List<Schedule> itemsToRemove) {
        List<Integer> positions = new ArrayList<>();
        for (Schedule schedule : itemsToRemove) {
            int pos = scheduleList.indexOf(schedule);
            if (pos >= 0) {
                positions.add(pos);
            }
        }

        Collections.sort(positions, Collections.reverseOrder());

        for (int position : positions) {
            if (position >= 0 && position < scheduleList.size()) {
                scheduleList.remove(position);
                notifyItemRemoved(position);
            }
        }

        if (!positions.isEmpty()) {
            int lowestPosition = positions.get(positions.size() - 1);
            if (lowestPosition < scheduleList.size()) {
                notifyItemRangeChanged(lowestPosition, scheduleList.size() - lowestPosition);
            }
        }
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
                         OnTaskCompletionListener completionListener, boolean isDeleteMode,
                         boolean isSelected, Context context) {
            scheduleTitle.setText(schedule.getTitle());

            // Set time with format preference
            if (schedule.getTime() != null && !schedule.getTime().isEmpty()) {
                String timeFormat = Settings.getTimeFormat(context);

                try {
                    String[] timeParts = schedule.getTime().split(":");
                    if (timeParts.length == 2) {
                        int hour = Integer.parseInt(timeParts[0]);
                        int minute = Integer.parseInt(timeParts[1]);

                        if (timeFormat.equals("military")) {
                            String time = String.format("%02d:%02d", hour, minute);
                            scheduleTime.setText(time);
                            scheduleAmPm.setVisibility(View.GONE);
                        } else {
                            int displayHour = hour;
                            String amPm;

                            if (hour == 0) {
                                displayHour = 12;
                                amPm = "AM";
                            } else if (hour < 12) {
                                amPm = "AM";
                            } else if (hour == 12) {
                                amPm = "PM";
                            } else {
                                displayHour = hour - 12;
                                amPm = "PM";
                            }

                            String time = String.format("%d:%02d", displayHour, minute);
                            scheduleTime.setText(time);
                            scheduleAmPm.setText(amPm);
                            scheduleAmPm.setVisibility(View.VISIBLE);
                        }

                        scheduleTime.setVisibility(View.VISIBLE);
                    } else {
                        scheduleTime.setVisibility(View.GONE);
                        scheduleAmPm.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    android.util.Log.e("DayScheduleAdapter", "Error parsing time: " + schedule.getTime(), e);
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

            // Show checkbox only for weekly and todo_task categories
            String scheduleCategory = schedule.getCategory();

            if ("weekly".equals(scheduleCategory) || "todo_task".equals(scheduleCategory)) {
                scheduleCheckbox.setVisibility(View.VISIBLE);

                // ✅ CRITICAL: Clear ALL listeners first
                scheduleCheckbox.setOnCheckedChangeListener(null);
                scheduleCheckbox.setOnClickListener(null);
                scheduleCheckbox.setChecked(false);
                scheduleCheckbox.setEnabled(true);

                // ✅ Set new click listener
                scheduleCheckbox.setOnClickListener(v -> {
                    CheckBox cb = (CheckBox) v;
                    android.util.Log.d("DayScheduleAdapter", "🖱️ Checkbox clicked for: " + schedule.getTitle());
                    android.util.Log.d("DayScheduleAdapter", "📍 Current checked state: " + cb.isChecked());

                    if (cb.isChecked() && completionListener != null) {
                        android.util.Log.d("DayScheduleAdapter", "✅ Calling completionListener for: " + schedule.getTitle());

                        // Disable checkbox to prevent double-clicks
                        cb.setEnabled(false);

                        // Call the completion listener
                        completionListener.onTaskCompleted(schedule);
                    } else if (!cb.isChecked()) {
                        // If somehow unchecked, keep it unchecked
                        android.util.Log.d("DayScheduleAdapter", "⚠️ Checkbox unchecked, ignoring");
                    }
                });
            } else {
                scheduleCheckbox.setVisibility(View.GONE);
                scheduleCheckbox.setOnCheckedChangeListener(null);
                scheduleCheckbox.setOnClickListener(null);
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
                    return Color.parseColor("#4CAF50");
                case "todo_task":
                    return Color.parseColor("#66BB6A");
                case "weekly":
                    return Color.parseColor("#2196F3");
                case "holiday":
                    return Color.parseColor("#F44336");
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