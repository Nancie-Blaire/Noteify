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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

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
                .inflate(R.layout.item_schedule, parent, false);
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
        TextView scheduleTime, scheduleAmPm;
        ImageView reminderIcon;
        View categoryIndicator;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            scheduleCheckbox = itemView.findViewById(R.id.scheduleCheckbox);
            scheduleTitle = itemView.findViewById(R.id.scheduleTitle);
            scheduleDescription = itemView.findViewById(R.id.scheduleDescription);
            categoryBadge = itemView.findViewById(R.id.categoryBadge);
            reminderIcon = itemView.findViewById(R.id.reminderIcon);
            scheduleTime = itemView.findViewById(R.id.scheduleTime);
            scheduleAmPm = itemView.findViewById(R.id.scheduleAmPm);
            categoryIndicator = itemView.findViewById(R.id.categoryIndicator);
        }

        public void bind(Schedule schedule, OnScheduleClickListener listener, boolean isDeleteMode, boolean isSelected) {
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

            // Handle delete mode vs normal mode
            if (isDeleteMode) {
                // DELETE MODE: Checkbox is for selection
                scheduleCheckbox.setVisibility(View.VISIBLE);
                scheduleCheckbox.setChecked(isSelected);

                // Remove any previous listeners to avoid loops
                scheduleCheckbox.setOnCheckedChangeListener(null);

                // Add click listener for selection toggle
                scheduleCheckbox.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onScheduleClick(schedule);
                    }
                });

                // Don't show strikethrough in delete mode
                scheduleTitle.setTextColor(Color.BLACK);
                scheduleTitle.setPaintFlags(scheduleTitle.getPaintFlags() &
                        (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                scheduleTime.setTextColor(Color.parseColor("#2196F3"));

            } else {
                // NORMAL MODE: Checkbox is for completion
                scheduleCheckbox.setVisibility(View.VISIBLE);
                scheduleCheckbox.setChecked(schedule.isCompleted());

                // Remove click listener (we'll use change listener instead)
                scheduleCheckbox.setOnClickListener(null);

                // Normal checkbox behavior (mark as complete)
                scheduleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    schedule.setCompleted(isChecked);

                    // Update in Firebase
                    String cat = schedule.getCategory();
                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    FirebaseAuth auth = FirebaseAuth.getInstance();

                    if (auth.getCurrentUser() == null) return;

                    String userId = auth.getCurrentUser().getUid();

                    if ("weekly".equals(cat)) {
                        // Update weekly task
                        String sourceId = schedule.getSourceId();
                        if (sourceId == null) return;

                        String taskId = schedule.getId().replace(sourceId + "_", "");

                        db.collection("users")
                                .document(userId)
                                .collection("weeklyPlans")
                                .document(sourceId)
                                .collection("tasks")
                                .document(taskId)
                                .update("isCompleted", isChecked)
                                .addOnSuccessListener(aVoid -> {
                                    // Update UI
                                    updateCompletedStyle(isChecked);
                                })
                                .addOnFailureListener(e -> {
                                    // Revert checkbox on failure
                                    schedule.setCompleted(!isChecked);
                                    buttonView.setChecked(!isChecked);
                                });
                    } else if ("todo".equals(cat)) {
                        // Update todo task
                        String sourceId = schedule.getSourceId();
                        if (sourceId == null) return;

                        String taskId = schedule.getId().replace(sourceId + "_", "");

                        db.collection("users")
                                .document(userId)
                                .collection("todoLists")
                                .document(sourceId)
                                .collection("tasks")
                                .document(taskId)
                                .update("isCompleted", isChecked)
                                .addOnSuccessListener(aVoid -> {
                                    updateCompletedStyle(isChecked);
                                })
                                .addOnFailureListener(e -> {
                                    schedule.setCompleted(!isChecked);
                                    buttonView.setChecked(!isChecked);
                                });
                    } else {
                        // Update regular schedule
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .document(schedule.getId())
                                .update("completed", isChecked)
                                .addOnSuccessListener(aVoid -> {
                                    updateCompletedStyle(isChecked);
                                })
                                .addOnFailureListener(e -> {
                                    schedule.setCompleted(!isChecked);
                                    buttonView.setChecked(!isChecked);
                                });
                    }
                });

                // Apply completed style in normal mode
                updateCompletedStyle(schedule.isCompleted());
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

        private void updateCompletedStyle(boolean isCompleted) {
            if (isCompleted) {
                scheduleTitle.setTextColor(Color.GRAY);
                scheduleTitle.setPaintFlags(scheduleTitle.getPaintFlags() |
                        android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                scheduleTime.setTextColor(Color.GRAY);
            } else {
                scheduleTitle.setTextColor(Color.BLACK);
                scheduleTitle.setPaintFlags(scheduleTitle.getPaintFlags() &
                        (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                scheduleTime.setTextColor(Color.parseColor("#2196F3"));
            }
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