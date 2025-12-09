package com.example.testtasksync;

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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WeeklyTaskAdapter extends RecyclerView.Adapter<WeeklyTaskAdapter.TaskViewHolder> {
    private String day;
    private List<WeeklyTask> tasks;
    private TaskActionListener listener;

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
        View view = LayoutInflater.from(parent.getContext())
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
                Collections.swap(tasks, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(tasks, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
        listener.onTaskMoved(fromPosition, toPosition);
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkbox;
        EditText taskText;
        ImageView scheduleButton;
        ImageView deleteButton;
        LinearLayout scheduleDisplay;
        TextView scheduleText;
        ImageView notificationIcon;
        ImageView clearScheduleButton;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.taskCheckbox);
            taskText = itemView.findViewById(R.id.taskEditText);
            scheduleButton = itemView.findViewById(R.id.scheduleButton);
            deleteButton = itemView.findViewById(R.id.deleteTaskButton);
            scheduleDisplay = itemView.findViewById(R.id.scheduleDisplay);
            scheduleText = itemView.findViewById(R.id.scheduleText);
            notificationIcon = itemView.findViewById(R.id.notificationIcon);
            clearScheduleButton = itemView.findViewById(R.id.clearScheduleButton);
        }

        void bind(WeeklyTask task, int position) {
            // ✅ FIX 1: Remove previous listeners properly
            checkbox.setOnCheckedChangeListener(null);
            taskText.removeTextChangedListener(null);

            // ✅ FIX 2: Set task text
            if (task.getTaskText() != null && !task.getTaskText().isEmpty()) {
                taskText.setText(task.getTaskText());
            } else {
                taskText.setText("");
            }

            // Set checkbox state
            checkbox.setChecked(task.isCompleted());

            // Apply strikethrough if completed
            if (task.isCompleted()) {
                taskText.setTextColor(itemView.getContext()
                        .getResources().getColor(android.R.color.darker_gray));
                taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                taskText.setTextColor(itemView.getContext()
                        .getResources().getColor(android.R.color.black));
                taskText.setPaintFlags(taskText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            }

            // ✅ FIX 3: Update schedule display
            updateScheduleDisplay(task);

            // Checkbox listener
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                task.setCompleted(isChecked);
                if (isChecked) {
                    taskText.setTextColor(itemView.getContext()
                            .getResources().getColor(android.R.color.darker_gray));
                    taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    taskText.setTextColor(itemView.getContext()
                            .getResources().getColor(android.R.color.black));
                    taskText.setPaintFlags(taskText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                }
                if (listener != null) {
                    listener.onTaskCompletionChanged(task, isChecked);
                }
            });

            // ✅ FIX 4: Text change listener (THIS WAS THE PROBLEM!)
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

            // Focus listener for delete button
            taskText.setOnFocusChangeListener((v, hasFocus) -> {
                deleteButton.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
            });

            // Delete button listener
            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClicked(task, getAdapterPosition());
                }
            });
        }

        // ✅ FIX 5: Schedule display method
        private void updateScheduleDisplay(WeeklyTask task) {
            if (task.getScheduleDate() != null) {
                scheduleDisplay.setVisibility(View.VISIBLE);

                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                String displayText = dateFormat.format(task.getScheduleDate());

                if (task.getScheduleTime() != null && !task.getScheduleTime().isEmpty()) {
                    displayText += ", " + task.getScheduleTime();
                }

                scheduleText.setText(displayText);
                notificationIcon.setVisibility(task.hasNotification() ? View.VISIBLE : View.GONE);
            } else {
                scheduleDisplay.setVisibility(View.GONE);
            }
        }
    }
}