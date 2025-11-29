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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class WeeklyTaskAdapter extends RecyclerView.Adapter<WeeklyTaskAdapter.TaskViewHolder> {

    private List<WeeklyTask> tasks;
    private String day;
    private TaskActionListener listener;

    public interface TaskActionListener {
        void onTaskTextChanged(WeeklyTask task, String newText);
        void onTaskCompletionChanged(WeeklyTask task, boolean isCompleted);
        void onDeleteClicked(WeeklyTask task, int position);
        void onTaskMoved(int fromPosition, int toPosition);
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

    class TaskViewHolder extends RecyclerView.ViewHolder {
        public CheckBox checkbox;      // public para ma-access from WeeklyActivity
        public EditText taskText;      // public para ma-access from WeeklyActivity
        public ImageView deleteButton; // public para ma-access from WeeklyActivity

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.taskCheckbox);
            taskText = itemView.findViewById(R.id.taskEditText);
            deleteButton = itemView.findViewById(R.id.deleteTaskButton);
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
    }
}
