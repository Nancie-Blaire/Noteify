package com.example.testtasksync;

import android.content.Context;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class WeeklyTaskAdapter extends RecyclerView.Adapter<WeeklyTaskAdapter.TaskViewHolder> {

    private List<WeeklyTask> tasks;
    private String day;
    private TaskActionListener listener;
    private Context context; // ✅ NEW: Store context

    public interface TaskActionListener {
        void onTaskTextChanged(WeeklyTask task, String newText);
        void onTaskCompletionChanged(WeeklyTask task, boolean isCompleted);
        void onDeleteClicked(WeeklyTask task, int position);
        void onTaskMoved(int fromPosition, int toPosition);
        void onScheduleClicked(WeeklyTask task, int position);
        void onClearScheduleClicked(WeeklyTask task);
    }

    public WeeklyTaskAdapter(String day, List<WeeklyTask> tasks, TaskActionListener listener) {
        this.day = day;
        this.tasks = tasks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // ✅ Store context
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_weekly_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        WeeklyTask task = tasks.get(position);
        holder.bind(task, position);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                WeeklyTask temp = tasks.get(i);
                tasks.set(i, tasks.get(i + 1));
                tasks.set(i + 1, temp);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                WeeklyTask temp = tasks.get(i);
                tasks.set(i, tasks.get(i - 1));
                tasks.set(i - 1, temp);
            }
        }

        notifyItemMoved(fromPosition, toPosition);

        // Update positions
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setPosition(i);
        }

        if (listener != null) {
            listener.onTaskMoved(fromPosition, toPosition);
        }
    }

    // ✅ NEW: Helper method to format time based on user preference
    private String formatTimeForDisplay(String time24) {
        if (time24 == null || time24.isEmpty()) {
            return "";
        }

        // Get user's time format preference
        String timeFormat = Settings.getTimeFormat(context);

        try {
            // Parse the 24-hour time
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            if ("civilian".equals(timeFormat)) {
                // Convert to 12-hour format with AM/PM
                String period = (hour >= 12) ? "PM" : "AM";
                int hour12 = (hour == 0) ? 12 : (hour > 12) ? hour - 12 : hour;
                return String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, period);
            } else {
                // Keep 24-hour format
                return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            }
        } catch (Exception e) {
            return time24;
        }
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        public CheckBox checkbox;
        public EditText taskText;
        public ImageView deleteButton;
        public ImageView scheduleButton;
        public LinearLayout scheduleDisplay;
        public TextView scheduleText;
        public ImageView notificationIcon;
        public ImageView clearScheduleButton;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.taskCheckbox);
            taskText = itemView.findViewById(R.id.taskEditText);
            deleteButton = itemView.findViewById(R.id.deleteTaskButton);
            scheduleButton = itemView.findViewById(R.id.scheduleButton);
            scheduleDisplay = itemView.findViewById(R.id.scheduleDisplay);
            scheduleText = itemView.findViewById(R.id.scheduleText);
            notificationIcon = itemView.findViewById(R.id.notificationIcon);
            clearScheduleButton = itemView.findViewById(R.id.clearScheduleButton);
        }

        void bind(WeeklyTask task, int position) {
            // Remove previous listeners
            checkbox.setOnCheckedChangeListener(null);

            // Set task text
            if (task.getTaskText() != null && !task.getTaskText().isEmpty()) {
                taskText.setText(task.getTaskText());
            } else {
                taskText.setText("");
            }

            // Set checkbox state
            checkbox.setChecked(task.isCompleted());

            // Apply strikethrough
            if (task.isCompleted()) {
                taskText.setTextColor(itemView.getContext().getResources()
                        .getColor(android.R.color.darker_gray));
                taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                taskText.setTextColor(itemView.getContext().getResources()
                        .getColor(android.R.color.black));
                taskText.setPaintFlags(taskText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            }

            // ✅ UPDATED: Update schedule display with formatted time
            updateScheduleDisplay(task);

            // Checkbox listener
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                task.setCompleted(isChecked);
                if (isChecked) {
                    taskText.setTextColor(itemView.getContext().getResources()
                            .getColor(android.R.color.darker_gray));
                    taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    taskText.setTextColor(itemView.getContext().getResources()
                            .getColor(android.R.color.black));
                    taskText.setPaintFlags(taskText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                }
                if (listener != null) {
                    listener.onTaskCompletionChanged(task, isChecked);
                }
            });

            // Text change listener
            taskText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    task.setTaskText(s.toString());
                    if (listener != null) {
                        listener.onTaskTextChanged(task, s.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            // Schedule button listener
            scheduleButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onScheduleClicked(task, getAdapterPosition());
                }
            });

            // Clear schedule button listener
            clearScheduleButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onClearScheduleClicked(task);
                }
            });

            // Focus listener
            taskText.setOnFocusChangeListener((v, hasFocus) -> {
                deleteButton.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
            });

            // Delete button
            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClicked(task, getAdapterPosition());
                }
            });
        }

        // ✅ UPDATED: Schedule display method with time formatting
        private void updateScheduleDisplay(WeeklyTask task) {
            if (task.getScheduleDate() != null) {
                scheduleDisplay.setVisibility(View.VISIBLE);

                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                String displayText = dateFormat.format(task.getScheduleDate());

                if (task.getScheduleTime() != null && !task.getScheduleTime().isEmpty()) {
                    // ✅ Format time according to user preference
                    String formattedTime = formatTimeForDisplay(task.getScheduleTime());
                    displayText += ", " + formattedTime;
                }

                scheduleText.setText(displayText);
                notificationIcon.setVisibility(task.hasNotification() ? View.VISIBLE : View.GONE);
            } else {
                scheduleDisplay.setVisibility(View.GONE);
            }
        }
    }
}