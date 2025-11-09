package com.example.testtasksync;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder> {

    private List<Schedule> scheduleList;
    private OnScheduleClickListener listener;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    public interface OnScheduleClickListener {
        void onScheduleClick(Schedule schedule);
        void onScheduleLongClick(Schedule schedule);
    }

    public ScheduleAdapter(List<Schedule> scheduleList, OnScheduleClickListener listener) {
        this.scheduleList = scheduleList;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
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
        holder.bind(schedule, listener, db, auth);
    }

    @Override
    public int getItemCount() {
        return scheduleList.size();
    }

    public static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        TextView scheduleTime, scheduleAmPm, scheduleTitle, scheduleDescription, categoryBadge;
        View categoryIndicator;
        ImageView reminderIcon;
        CheckBox scheduleCheckbox;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            scheduleTime = itemView.findViewById(R.id.scheduleTime);
            scheduleAmPm = itemView.findViewById(R.id.scheduleAmPm);
            scheduleTitle = itemView.findViewById(R.id.scheduleTitle);
            scheduleDescription = itemView.findViewById(R.id.scheduleDescription);
            categoryBadge = itemView.findViewById(R.id.categoryBadge);
            categoryIndicator = itemView.findViewById(R.id.categoryIndicator);
            reminderIcon = itemView.findViewById(R.id.reminderIcon);
            scheduleCheckbox = itemView.findViewById(R.id.scheduleCheckbox);
        }

        public void bind(Schedule schedule, OnScheduleClickListener listener,
                         FirebaseFirestore db, FirebaseAuth auth) {
            // Set title
            scheduleTitle.setText(schedule.getTitle());

            // Set description
            if (schedule.getDescription() != null && !schedule.getDescription().isEmpty()) {
                scheduleDescription.setText(schedule.getDescription());
                scheduleDescription.setVisibility(View.VISIBLE);
            } else {
                scheduleDescription.setVisibility(View.GONE);
            }

            // Set time
            if (schedule.getTime() != null && !schedule.getTime().isEmpty()) {
                String formattedTime = schedule.getFormattedTime();
                String[] timeParts = formattedTime.split(" ");
                if (timeParts.length == 2) {
                    scheduleTime.setText(timeParts[0]);
                    scheduleAmPm.setText(timeParts[1]);
                } else {
                    scheduleTime.setText(formattedTime);
                    scheduleAmPm.setText("");
                }
            } else {
                scheduleTime.setText("All");
                scheduleAmPm.setText("Day");
            }

            // Set category badge and color
            String category = schedule.getCategory();
            int categoryColor = getCategoryColor(category);
            categoryIndicator.setBackgroundColor(categoryColor);
            categoryBadge.setText(getCategoryDisplayName(category));
            categoryBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(categoryColor));

            // Show reminder icon if reminder is set
            reminderIcon.setVisibility(schedule.hasReminder() ? View.VISIBLE : View.GONE);

            // Set checkbox state
            scheduleCheckbox.setChecked(schedule.isCompleted());

            // Apply completed style if checked
            if (schedule.isCompleted()) {
                scheduleTitle.setTextColor(Color.GRAY);
                scheduleTitle.setPaintFlags(scheduleTitle.getPaintFlags() |
                        android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                scheduleTitle.setTextColor(Color.BLACK);
                scheduleTitle.setPaintFlags(scheduleTitle.getPaintFlags() &
                        (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            }

            // Checkbox listener
            scheduleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                schedule.setCompleted(isChecked);
                updateCompletionInFirebase(schedule, db, auth, itemView);

                // Update UI
                if (isChecked) {
                    scheduleTitle.setTextColor(Color.GRAY);
                    scheduleTitle.setPaintFlags(scheduleTitle.getPaintFlags() |
                            android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    scheduleTitle.setTextColor(Color.BLACK);
                    scheduleTitle.setPaintFlags(scheduleTitle.getPaintFlags() &
                            (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                }
            });

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
                    return Color.parseColor("#4CAF50"); // Default to Green (todo)
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
                    return "To-Do";  // Changed from "Event" to "To-Do"
            }
        }

        private void updateCompletionInFirebase(Schedule schedule, FirebaseFirestore db,
                                                FirebaseAuth auth, View view) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                db.collection("users")
                        .document(user.getUid())
                        .collection("schedules")
                        .document(schedule.getId())
                        .update("isCompleted", schedule.isCompleted())
                        .addOnSuccessListener(aVoid -> {
                            String message = schedule.isCompleted() ?
                                    "✓ Marked as done" : "Marked as not done";
                            Toast.makeText(view.getContext(), message, Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(view.getContext(),
                                    "Failed to update status", Toast.LENGTH_SHORT).show();
                        });
            }
        }
    }
}