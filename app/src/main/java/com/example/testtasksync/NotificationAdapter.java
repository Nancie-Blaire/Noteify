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

            // Format due date
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            String dateStr = "Due: " + dateFormat.format(item.getDueDate());

            if (item.getDueTime() != null && !item.getDueTime().isEmpty()) {
                dateStr += ", " + item.getDueTime();
            }

            dueDateText.setText(dateStr);

            // Set category indicator color
            int color;
            if ("todo".equals(item.getType())) {
                color = itemView.getContext().getResources().getColor(R.color.todo_blue);
            } else {
                color = itemView.getContext().getResources().getColor(R.color.weekly_green);
            }
            categoryIndicator.setBackgroundColor(color);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}