package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Notifications extends Fragment {
    private static final String TAG = "NotificationsFragment";

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private RecyclerView upcomingRecyclerView;
    private LinearLayout overdueContainer;
    private TextView upcomingEmptyText;
    private TextView overdueEmptyText;

    private List<NotificationItem> upcomingList = new ArrayList<>();
    private List<NotificationItem> overdueList = new ArrayList<>();

    private NotificationAdapter upcomingAdapter;

    private Set<String> deletedTodoLists = new HashSet<>();
    private Set<String> deletedWeeklyPlans = new HashSet<>();

    private int pendingLoads = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        upcomingRecyclerView = view.findViewById(R.id.upcomingRecyclerView);
        overdueContainer = view.findViewById(R.id.overdueContainer);
        upcomingEmptyText = view.findViewById(R.id.upcomingEmptyText);
        overdueEmptyText = view.findViewById(R.id.overdueEmptyText);

        upcomingRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        upcomingAdapter = new NotificationAdapter(upcomingList, new NotificationAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(NotificationItem item) {
                openTask(item);
            }
        });

        upcomingRecyclerView.setAdapter(upcomingAdapter);

        loadNotifications();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void checkAndCallUpdateUI() {
        pendingLoads--;
        if (pendingLoads <= 0) {
            updateUI();
        }
    }

    private void loadNotifications() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        upcomingList.clear();
        overdueList.clear();
        deletedTodoLists.clear();
        deletedWeeklyPlans.clear();

        pendingLoads = 2;

        Calendar now = Calendar.getInstance();

        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .get()
                .addOnSuccessListener(scheduleSnapshots -> {
                    for (QueryDocumentSnapshot doc : scheduleSnapshots) {
                        if (doc.get("deletedAt") != null) {
                            String category = doc.getString("category");
                            String sourceId = doc.getString("sourceId");

                            if (sourceId != null) {
                                if ("todo".equals(category)) {
                                    deletedTodoLists.add(sourceId);
                                    Log.d(TAG, "Marking todo as deleted: " + sourceId);
                                } else if ("weekly".equals(category)) {
                                    deletedWeeklyPlans.add(sourceId);
                                    Log.d(TAG, "Marking weekly as deleted: " + sourceId);
                                }
                            }
                        }
                    }

                    loadTodoTasks(user, now);
                    loadWeeklyTasks(user, now);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading schedules", e);
                    loadTodoTasks(user, now);
                    loadWeeklyTasks(user, now);
                });
    }

    private void loadTodoTasks(FirebaseUser user, Calendar now) {
        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .get()
                .addOnSuccessListener(todoSnapshots -> {
                    final List<Task<QuerySnapshot>> taskFetchTasks = new ArrayList<>();

                    if (todoSnapshots.isEmpty()) {
                        checkAndCallUpdateUI();
                        return;
                    }

                    for (QueryDocumentSnapshot todoDoc : todoSnapshots) {
                        String listId = todoDoc.getId();

                        if (deletedTodoLists.contains(listId)) {
                            Log.d(TAG, "Skipping deleted todo list: " + listId);
                            continue;
                        }

                        String listTitle = todoDoc.getString("title");

                        Task<QuerySnapshot> checkScheduleTask = db.collection("users")
                                .document(user.getUid())
                                .collection("schedules")
                                .whereEqualTo("sourceId", listId)
                                .whereEqualTo("category", "todo")
                                .get()
                                .continueWithTask(scheduleResult -> {
                                    boolean listHasReminder = false;
                                    Timestamp listScheduleDate = null;
                                    String listScheduleTime = "";

                                    if (scheduleResult.isSuccessful() && scheduleResult.getResult() != null &&
                                            !scheduleResult.getResult().isEmpty()) {
                                        DocumentSnapshot scheduleDoc = scheduleResult.getResult().getDocuments().get(0);
                                        Boolean hasReminder = scheduleDoc.getBoolean("hasReminder");
                                        listHasReminder = Boolean.TRUE.equals(hasReminder);
                                        listScheduleDate = scheduleDoc.getTimestamp("date");
                                        listScheduleTime = scheduleDoc.getString("time");

                                        Log.d(TAG, "✅ Todo list '" + listTitle + "' has reminder: " + listHasReminder);
                                    }

                                    final boolean finalListHasReminder = listHasReminder;
                                    final Timestamp finalListScheduleDate = listScheduleDate;
                                    final String finalListScheduleTime = listScheduleTime;

                                    return db.collection("users")
                                            .document(user.getUid())
                                            .collection("todoLists")
                                            .document(listId)
                                            .collection("tasks")
                                            .get()
                                            .addOnSuccessListener(taskSnapshots -> {
                                                for (QueryDocumentSnapshot taskDoc : taskSnapshots) {
                                                    Boolean isCompleted = taskDoc.getBoolean("isCompleted");

                                                    if (Boolean.TRUE.equals(isCompleted)) {
                                                        continue;
                                                    }

                                                    Timestamp scheduleTimestamp = taskDoc.getTimestamp("scheduleDate");
                                                    String scheduleTime = taskDoc.getString("scheduleTime");
                                                    String taskText = taskDoc.getString("taskText");

                                                    if (scheduleTimestamp != null) {
                                                        Date scheduleDate = scheduleTimestamp.toDate();
                                                        Calendar taskCalendar = Calendar.getInstance();
                                                        taskCalendar.setTime(scheduleDate);

                                                        if (scheduleTime != null && !scheduleTime.isEmpty()) {
                                                            try {
                                                                String[] timeParts = scheduleTime.split(":");
                                                                taskCalendar.set(Calendar.HOUR_OF_DAY,
                                                                        Integer.parseInt(timeParts[0]));
                                                                taskCalendar.set(Calendar.MINUTE,
                                                                        Integer.parseInt(timeParts[1]));
                                                            } catch (Exception e) {
                                                                Log.e(TAG, "Error parsing time", e);
                                                            }
                                                        }

                                                        NotificationItem item = new NotificationItem(
                                                                listId,
                                                                listTitle,
                                                                taskText,
                                                                taskCalendar.getTime(),
                                                                scheduleTime,
                                                                "todo"
                                                        );

                                                        if (taskCalendar.getTimeInMillis() < now.getTimeInMillis()) {
                                                            if (!overdueList.contains(item)) {
                                                                overdueList.add(item);
                                                            }
                                                        } else {
                                                            if (!upcomingList.contains(item)) {
                                                                upcomingList.add(item);
                                                            }
                                                        }
                                                    } else if (finalListHasReminder && finalListScheduleDate != null) {
                                                        Date scheduleDate = finalListScheduleDate.toDate();
                                                        Calendar taskCalendar = Calendar.getInstance();
                                                        taskCalendar.setTime(scheduleDate);

                                                        if (finalListScheduleTime != null && !finalListScheduleTime.isEmpty()) {
                                                            try {
                                                                String[] timeParts = finalListScheduleTime.split(":");
                                                                taskCalendar.set(Calendar.HOUR_OF_DAY,
                                                                        Integer.parseInt(timeParts[0]));
                                                                taskCalendar.set(Calendar.MINUTE,
                                                                        Integer.parseInt(timeParts[1]));
                                                            } catch (Exception e) {
                                                                Log.e(TAG, "Error parsing time", e);
                                                            }
                                                        }

                                                        NotificationItem item = new NotificationItem(
                                                                listId,
                                                                listTitle,
                                                                taskText,
                                                                taskCalendar.getTime(),
                                                                finalListScheduleTime,
                                                                "todo"
                                                        );

                                                        if (taskCalendar.getTimeInMillis() < now.getTimeInMillis()) {
                                                            if (!overdueList.contains(item)) {
                                                                overdueList.add(item);
                                                            }
                                                        } else {
                                                            if (!upcomingList.contains(item)) {
                                                                upcomingList.add(item);
                                                            }
                                                        }
                                                    }
                                                }
                                            });
                                });

                        taskFetchTasks.add(checkScheduleTask);
                    }

                    Tasks.whenAllComplete(taskFetchTasks)
                            .addOnCompleteListener(task -> {
                                checkAndCallUpdateUI();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading todo lists", e);
                    checkAndCallUpdateUI();
                });
    }

    private void loadWeeklyTasks(FirebaseUser user, Calendar now) {
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .get()
                .addOnSuccessListener(weeklySnapshots -> {
                    final List<Task<QuerySnapshot>> taskFetchTasks = new ArrayList<>();

                    if (weeklySnapshots.isEmpty()) {
                        checkAndCallUpdateUI();
                        return;
                    }

                    for (QueryDocumentSnapshot weeklyDoc : weeklySnapshots) {
                        String planId = weeklyDoc.getId();

                        if (deletedWeeklyPlans.contains(planId)) {
                            Log.d(TAG, "Skipping deleted weekly plan: " + planId);
                            continue;
                        }

                        String planTitle = weeklyDoc.getString("title");
                        String planTime = weeklyDoc.getString("time");
                        Timestamp startTimestamp = weeklyDoc.getTimestamp("startDate");
                        Timestamp endTimestamp = weeklyDoc.getTimestamp("endDate");

                        if (startTimestamp == null) {
                            continue;
                        }

                        Task<QuerySnapshot> checkScheduleTask = db.collection("users")
                                .document(user.getUid())
                                .collection("schedules")
                                .whereEqualTo("sourceId", planId)
                                .whereEqualTo("category", "weekly")
                                .get()
                                .continueWithTask(scheduleResult -> {
                                    boolean planHasReminder = false;
                                    String planScheduleTime = planTime;

                                    if (scheduleResult.isSuccessful() && scheduleResult.getResult() != null &&
                                            !scheduleResult.getResult().isEmpty()) {
                                        DocumentSnapshot scheduleDoc = scheduleResult.getResult().getDocuments().get(0);
                                        Boolean hasReminder = scheduleDoc.getBoolean("hasReminder");
                                        planHasReminder = Boolean.TRUE.equals(hasReminder);

                                        String scheduleTime = scheduleDoc.getString("time");
                                        if (scheduleTime != null && !scheduleTime.isEmpty()) {
                                            planScheduleTime = scheduleTime;
                                        }

                                        Log.d(TAG, "✅ Weekly plan '" + planTitle + "' has reminder: " + planHasReminder);
                                    }

                                    final boolean finalPlanHasReminder = planHasReminder;
                                    final String finalPlanScheduleTime = planScheduleTime;

                                    if (finalPlanHasReminder && startTimestamp != null) {
                                        Calendar weekStartCalendar = Calendar.getInstance();
                                        weekStartCalendar.setTime(startTimestamp.toDate());

                                        if (finalPlanScheduleTime != null && !finalPlanScheduleTime.isEmpty()) {
                                            try {
                                                String[] timeParts = finalPlanScheduleTime.split(":");
                                                weekStartCalendar.set(Calendar.HOUR_OF_DAY,
                                                        Integer.parseInt(timeParts[0]));
                                                weekStartCalendar.set(Calendar.MINUTE,
                                                        Integer.parseInt(timeParts[1]));
                                                weekStartCalendar.set(Calendar.SECOND, 0);
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error parsing time", e);
                                            }
                                        }

                                        String weekRange = android.text.format.DateFormat.format("MMM dd", startTimestamp.toDate()).toString();
                                        if (endTimestamp != null) {
                                            weekRange += " - " + android.text.format.DateFormat.format("MMM dd", endTimestamp.toDate()).toString();
                                        }

                                        NotificationItem weekItem = new NotificationItem(
                                                planId,
                                                planTitle + " (" + weekRange + ")",
                                                "Weekly plan starts",
                                                weekStartCalendar.getTime(),
                                                finalPlanScheduleTime,
                                                "weekly"
                                        );

                                        if (weekStartCalendar.getTimeInMillis() < now.getTimeInMillis()) {
                                            if (!overdueList.contains(weekItem)) {
                                                overdueList.add(weekItem);
                                            }
                                        } else {
                                            if (!upcomingList.contains(weekItem)) {
                                                upcomingList.add(weekItem);
                                            }
                                        }
                                    }

                                    return db.collection("users")
                                            .document(user.getUid())
                                            .collection("weeklyPlans")
                                            .document(planId)
                                            .collection("tasks")
                                            .get()
                                            .continueWithTask(taskResult -> {
                                                return db.collection("users")
                                                        .document(user.getUid())
                                                        .collection("weeklyPlans")
                                                        .document(planId)
                                                        .collection("daySchedules")
                                                        .orderBy("scheduleNumber")
                                                        .get()
                                                        .addOnSuccessListener(dayScheduleSnapshots -> {
                                                            Map<String, List<DaySchedule>> daySchedulesMap = new HashMap<>();

                                                            for (QueryDocumentSnapshot dayDoc : dayScheduleSnapshots) {
                                                                DaySchedule schedule = dayDoc.toObject(DaySchedule.class);
                                                                String day = schedule.getDay();

                                                                if (!daySchedulesMap.containsKey(day)) {
                                                                    daySchedulesMap.put(day, new ArrayList<>());
                                                                }
                                                                daySchedulesMap.get(day).add(schedule);
                                                            }

                                                            QuerySnapshot taskSnapshots = taskResult.getResult();
                                                            if (taskSnapshots != null) {
                                                                for (QueryDocumentSnapshot taskDoc : taskSnapshots) {
                                                                    Boolean isCompleted = taskDoc.getBoolean("isCompleted");

                                                                    if (Boolean.TRUE.equals(isCompleted)) {
                                                                        continue;
                                                                    }

                                                                    String day = taskDoc.getString("day");
                                                                    String taskText = taskDoc.getString("taskText");

                                                                    if (day != null) {
                                                                        List<DaySchedule> daySchedules = daySchedulesMap.get(day);

                                                                        if (daySchedules != null && !daySchedules.isEmpty()) {
                                                                            for (DaySchedule daySchedule : daySchedules) {
                                                                                if (daySchedule.getDate() != null) {
                                                                                    Calendar taskDate = Calendar.getInstance();
                                                                                    taskDate.setTime(daySchedule.getDate().toDate());
                                                                                    String time = daySchedule.getTime();

                                                                                    if (time != null && !time.isEmpty()) {
                                                                                        try {
                                                                                            String[] timeParts = time.split(":");
                                                                                            taskDate.set(Calendar.HOUR_OF_DAY,
                                                                                                    Integer.parseInt(timeParts[0]));
                                                                                            taskDate.set(Calendar.MINUTE,
                                                                                                    Integer.parseInt(timeParts[1]));
                                                                                            taskDate.set(Calendar.SECOND, 0);
                                                                                        } catch (Exception e) {
                                                                                            Log.e(TAG, "Error parsing time", e);
                                                                                        }
                                                                                    }

                                                                                    String itemTitle = planTitle + " - " + day;
                                                                                    if (daySchedules.size() > 1) {
                                                                                        itemTitle += " (Schedule " +
                                                                                                daySchedule.getScheduleNumber() + ")";
                                                                                    }

                                                                                    NotificationItem item = new NotificationItem(
                                                                                            planId,
                                                                                            itemTitle,
                                                                                            taskText,
                                                                                            taskDate.getTime(),
                                                                                            time,
                                                                                            "weekly"
                                                                                    );

                                                                                    if (taskDate.getTimeInMillis() < now.getTimeInMillis()) {
                                                                                        if (!overdueList.contains(item)) {
                                                                                            overdueList.add(item);
                                                                                        }
                                                                                    } else {
                                                                                        if (!upcomingList.contains(item)) {
                                                                                            upcomingList.add(item);
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        } else {
                                                                            Calendar taskDate = Calendar.getInstance();
                                                                            taskDate.setTime(startTimestamp.toDate());
                                                                            int targetDay = getDayOfWeek(day);

                                                                            while (taskDate.get(Calendar.DAY_OF_WEEK) != targetDay) {
                                                                                taskDate.add(Calendar.DAY_OF_MONTH, 1);
                                                                            }

                                                                            String taskScheduleTime = taskDoc.getString("scheduleTime");
                                                                            String timeToUse = (taskScheduleTime != null && !taskScheduleTime.isEmpty())
                                                                                    ? taskScheduleTime
                                                                                    : finalPlanScheduleTime;

                                                                            if (timeToUse != null && !timeToUse.isEmpty()) {
                                                                                try {
                                                                                    String[] timeParts = timeToUse.split(":");
                                                                                    taskDate.set(Calendar.HOUR_OF_DAY,
                                                                                            Integer.parseInt(timeParts[0]));
                                                                                    taskDate.set(Calendar.MINUTE,
                                                                                            Integer.parseInt(timeParts[1]));
                                                                                    taskDate.set(Calendar.SECOND, 0);
                                                                                } catch (Exception e) {
                                                                                    Log.e(TAG, "Error parsing time", e);
                                                                                }
                                                                            }

                                                                            NotificationItem item = new NotificationItem(
                                                                                    planId,
                                                                                    planTitle + " - " + day,
                                                                                    taskText,
                                                                                    taskDate.getTime(),
                                                                                    timeToUse != null ? timeToUse : "",
                                                                                    "weekly"
                                                                            );

                                                                            if (taskDate.getTimeInMillis() < now.getTimeInMillis()) {
                                                                                if (!overdueList.contains(item)) {
                                                                                    overdueList.add(item);
                                                                                }
                                                                            } else {
                                                                                if (!upcomingList.contains(item)) {
                                                                                    upcomingList.add(item);
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        });
                                            });
                                });

                        taskFetchTasks.add(checkScheduleTask);
                    }

                    Tasks.whenAllComplete(taskFetchTasks)
                            .addOnCompleteListener(task -> {
                                checkAndCallUpdateUI();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading weekly plans", e);
                    checkAndCallUpdateUI();
                });
    }

    private void updateUI() {
        Collections.sort(upcomingList, new Comparator<NotificationItem>() {
            @Override
            public int compare(NotificationItem a, NotificationItem b) {
                return a.getDueDate().compareTo(b.getDueDate());
            }
        });

        Collections.sort(overdueList, new Comparator<NotificationItem>() {
            @Override
            public int compare(NotificationItem a, NotificationItem b) {
                return b.getDueDate().compareTo(a.getDueDate());
            }
        });

        upcomingAdapter.notifyDataSetChanged();

        if (upcomingList.isEmpty()) {
            upcomingRecyclerView.setVisibility(View.GONE);
            upcomingEmptyText.setVisibility(View.VISIBLE);
        } else {
            upcomingRecyclerView.setVisibility(View.VISIBLE);
            upcomingEmptyText.setVisibility(View.GONE);
        }

        overdueContainer.removeAllViews();

        if (overdueList.isEmpty()) {
            overdueContainer.setVisibility(View.GONE);
            overdueEmptyText.setVisibility(View.VISIBLE);
        } else {
            overdueContainer.setVisibility(View.VISIBLE);
            overdueEmptyText.setVisibility(View.GONE);

            for (NotificationItem item : overdueList) {
                View itemView = createOverdueItemView(item);
                overdueContainer.addView(itemView);
            }
        }

        Log.d(TAG, "✅ Notifications updated - Upcoming: " + upcomingList.size() +
                ", Overdue: " + overdueList.size());
    }

    private View createOverdueItemView(NotificationItem item) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_notification, overdueContainer, false);

        TextView titleText = view.findViewById(R.id.notificationTitle);
        TextView taskText = view.findViewById(R.id.notificationTask);
        TextView dueDateText = view.findViewById(R.id.notificationDueDate);
        View categoryIndicator = view.findViewById(R.id.categoryIndicator);

        titleText.setText(item.getTitle());
        taskText.setText(item.getTaskText());

        String timeFormat = Settings.getTimeFormat(getContext());

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        String dateStr = "Due: " + dateFormat.format(item.getDueDate());

        if (item.getDueTime() != null && !item.getDueTime().isEmpty()) {
            String formattedTime = convertTimeFormat(item.getDueTime(), timeFormat);
            dateStr += ", " + formattedTime;
        }

        dueDateText.setText(dateStr);

        int color = getResources().getColor(android.R.color.holo_red_dark);
        categoryIndicator.setBackgroundColor(color);
        dueDateText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

        view.setOnClickListener(v -> openTask(item));

        return view;
    }

    private String convertTimeFormat(String time24, String format) {
        if (time24 == null || time24.isEmpty()) {
            return "";
        }

        try {
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            if ("civilian".equals(format)) {
                String period = (hour >= 12) ? "PM" : "AM";
                int hour12 = (hour == 0) ? 12 : (hour > 12) ? hour - 12 : hour;
                return String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, period);
            } else {
                return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            }
        } catch (Exception e) {
            return time24;
        }
    }

    private void openTask(NotificationItem item) {
        if ("todo".equals(item.getType())) {
            Intent intent = new Intent(getContext(), TodoActivity.class);
            intent.putExtra("listId", item.getSourceId());
            startActivity(intent);
        } else if ("weekly".equals(item.getType())) {
            Intent intent = new Intent(getContext(), WeeklyActivity.class);
            intent.putExtra("planId", item.getSourceId());
            startActivity(intent);
        }
    }

    private int getDayOfWeek(String day) {
        switch (day) {
            case "Mon": return Calendar.MONDAY;
            case "Tues": return Calendar.TUESDAY;
            case "Wed": return Calendar.WEDNESDAY;
            case "Thur": return Calendar.THURSDAY;
            case "Fri": return Calendar.FRIDAY;
            case "Sat": return Calendar.SATURDAY;
            case "Sun": return Calendar.SUNDAY;
            default: return Calendar.MONDAY;
        }
    }
}