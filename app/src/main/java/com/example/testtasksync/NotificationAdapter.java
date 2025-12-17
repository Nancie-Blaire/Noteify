package com.example.testtasksync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationItem> items;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(NotificationItem item);
    }

    public NotificationAdapter(List<NotificationItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView taskText;
        TextView dueDateText;
        View categoryIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.notificationTitle);
            taskText = itemView.findViewById(R.id.notificationTask);
            dueDateText = itemView.findViewById(R.id.notificationDueDate);
            categoryIndicator = itemView.findViewById(R.id.categoryIndicator);
        }

        public void bind(NotificationItem item, OnItemClickListener listener) {
            titleText.setText(item.getTitle());
            taskText.setText(item.getTaskText());

            // ✅ Get user's time format preference
            String timeFormat = Settings.getTimeFormat(itemView.getContext());

            // Format due date
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            String dateStr = "Due: " + dateFormat.format(item.getDueDate());

            if (item.getDueTime() != null && !item.getDueTime().isEmpty()) {
                // ✅ Convert time to user's preferred format
                String formattedTime = convertTimeFormat(item.getDueTime(), timeFormat);
                dateStr += ", " + formattedTime;
            }

            dueDateText.setText(dateStr);

            // ✅ NEW: Check if overdue
            boolean isOverdue = item.getDueDate().before(new Date());

            // Set category indicator color
            int color;
            if (isOverdue) {
                // ✅ OVERDUE: Always show RED
                color = itemView.getContext().getResources().getColor(android.R.color.holo_red_dark);
                // ✅ Make due date text RED
                dueDateText.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
            } else {
                // ✅ NOT OVERDUE: Use category colors
                if ("todo".equals(item.getType())) {
                    color = itemView.getContext().getResources().getColor(R.color.todo_green);
                } else {
                    color = itemView.getContext().getResources().getColor(R.color.weekly_blue);
                }
                // ✅ Keep due date text normal (gray)
                dueDateText.setTextColor(itemView.getContext().getResources().getColor(android.R.color.darker_gray));
            }
            categoryIndicator.setBackgroundColor(color);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }

        // ✅ NEW: Convert time format based on user preference
        private String convertTimeFormat(String time24, String format) {
            if (time24 == null || time24.isEmpty()) {
                return "";
            }

            try {
                // Parse the 24-hour time (HH:mm format)
                String[] parts = time24.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                if ("civilian".equals(format)) {
                    // Convert to 12-hour format
                    String period = (hour >= 12) ? "PM" : "AM";
                    int hour12 = (hour == 0) ? 12 : (hour > 12) ? hour - 12 : hour;
                    return String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, period);
                } else {
                    // Keep 24-hour format
                    return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                }
            } catch (Exception e) {
                // If parsing fails, return original
                return time24;
            }
        }
    }
}