package com.example.testtasksync;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

public class CalendarGridAdapter extends RecyclerView.Adapter<CalendarGridAdapter.DayViewHolder> {

    private List<CalendarFragment.CalendarDay> days;
    private Map<String, List<Schedule>> dateSchedulesMap;

    public CalendarGridAdapter(List<CalendarFragment.CalendarDay> days,
                               Map<String, List<Schedule>> dateSchedulesMap) {
        this.days = days;
        this.dateSchedulesMap = dateSchedulesMap;
    }

    public void updateDays(List<CalendarFragment.CalendarDay> newDays) {
        this.days = newDays;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        CalendarFragment.CalendarDay day = days.get(position);
        holder.bind(day, dateSchedulesMap);
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    public static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView dayNumber, holidayIndicator;
        LinearLayout taskPreviewContainer;

        public DayViewHolder(@NonNull View itemView) {
            super(itemView);
            dayNumber = itemView.findViewById(R.id.dayNumber);
            holidayIndicator = itemView.findViewById(R.id.holidayIndicator);
            taskPreviewContainer = itemView.findViewById(R.id.taskPreviewContainer);
        }

        public void bind(CalendarFragment.CalendarDay day, Map<String, List<Schedule>> dateSchedulesMap) {
            if (!day.isCurrentMonth()) {
                dayNumber.setText("");
                taskPreviewContainer.removeAllViews();
                holidayIndicator.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
                itemView.setAlpha(0.3f);
                return;
            }

            itemView.setAlpha(1.0f);
            dayNumber.setText(day.getDayNumber());

            // Highlight today
            if (day.isToday()) {
                dayNumber.setTextColor(Color.BLACK);
                dayNumber.setBackgroundResource(R.drawable.item_highlight_day);
            } else {
                dayNumber.setTextColor(Color.BLACK);
                dayNumber.setBackground(null);
            }

            // Clear previous task previews
            taskPreviewContainer.removeAllViews();

            // Get schedules for this day
            List<Schedule> schedules = dateSchedulesMap.get(day.getDateKey());

            if (schedules != null && !schedules.isEmpty()) {
                // Check for holidays
                boolean hasHoliday = false;
                for (Schedule schedule : schedules) {
                    if ("holiday".equals(schedule.getCategory())) {
                        holidayIndicator.setVisibility(View.VISIBLE);
                        hasHoliday = true;
                        break;
                    }
                }
                if (!hasHoliday) {
                    holidayIndicator.setVisibility(View.GONE);
                }

                // Show up to 3 tasks
                int count = Math.min(3, schedules.size());
                for (int i = 0; i < count; i++) {
                    Schedule schedule = schedules.get(i);
                    addMiniTaskView(schedule);
                }

                // Show "+X more" if there are more tasks
                if (schedules.size() > 3) {
                    TextView moreText = new TextView(itemView.getContext());
                    moreText.setText("+" + (schedules.size() - 3) + " more");
                    moreText.setTextSize(9);
                    moreText.setTextColor(Color.GRAY);
                    moreText.setPadding(4, 2, 4, 2);
                    taskPreviewContainer.addView(moreText);
                }
            } else {
                holidayIndicator.setVisibility(View.GONE);
            }

            // Click to view day details
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), DayDetailsActivity.class);
                intent.putExtra("dateKey", day.getDateKey());
                itemView.getContext().startActivity(intent);
            });
        }

        private void addMiniTaskView(Schedule schedule) {
            View miniTask = LayoutInflater.from(itemView.getContext())
                    .inflate(R.layout.item_mini_task, taskPreviewContainer, false);

            TextView miniTaskText = miniTask.findViewById(R.id.miniTaskText);
            miniTaskText.setText(schedule.getTitle());

            // Set color based on category
            int bgColor = getCategoryColor(schedule.getCategory());
            miniTask.setBackgroundColor(bgColor);

            taskPreviewContainer.addView(miniTask);
        }

        private int getCategoryColor(String category) {
            switch (category) {
                case "todo":
                    return Color.parseColor("#81C784"); // Light Green
                case "weekly":
                    return Color.parseColor("#64B5F6"); // Light Blue
                case "holiday":
                    return Color.parseColor("#E57373"); // Light Red
                default:
                    return Color.parseColor("#90CAF9"); // Default Light Blue
            }
        }
    }
}