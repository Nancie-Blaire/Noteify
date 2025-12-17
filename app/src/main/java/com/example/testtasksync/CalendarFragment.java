package com.example.testtasksync;
// COMMENT
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CalendarFragment extends Fragment {

    private static final String TAG = "CalendarFragment";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration scheduleListener;
    private ListenerRegistration weeklyPlansListener;
    private ListenerRegistration todoTasksListener; // ✅ NEW

    private TextView monthYearText;
    private RecyclerView calendarGridRecyclerView;
    private CalendarGridAdapter calendarAdapter;

    private Calendar currentCalendar;
    private Map<String, List<Schedule>> dateSchedulesMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        monthYearText = view.findViewById(R.id.monthYearText);
        calendarGridRecyclerView = view.findViewById(R.id.calendarGridRecyclerView);
        View monthYearHeader = view.findViewById(R.id.monthYearHeader);

        currentCalendar = Calendar.getInstance();
        dateSchedulesMap = new HashMap<>();

        calendarGridRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 7));
        calendarAdapter = new CalendarGridAdapter(new ArrayList<>(), dateSchedulesMap);
        calendarGridRecyclerView.setAdapter(calendarAdapter);

        monthYearHeader.setOnClickListener(v -> showMonthYearPicker());

        updateCalendarDisplay();
        loadSchedulesForMonth();
    }

    private void updateCalendarDisplay() {
        // Show both month and year
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthYearText.setText(sdf.format(currentCalendar.getTime()));

        List<CalendarDay> days = generateCalendarDays(currentCalendar);
        calendarAdapter.updateDays(days);
    }

    private List<CalendarDay> generateCalendarDays(Calendar calendar) {
        List<CalendarDay> days = new ArrayList<>();

        Calendar cal = (Calendar) calendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = Calendar.SUNDAY; i < firstDayOfWeek; i++) {
            days.add(new CalendarDay("", false, null));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            cal.set(Calendar.DAY_OF_MONTH, day);
            String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

            boolean isToday = isSameDay(cal, Calendar.getInstance());
            days.add(new CalendarDay(String.valueOf(day), true, dateKey, isToday));
        }

        return days;
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH);
    }

    private void loadSchedulesForMonth() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Remove old listeners
        if (scheduleListener != null) {
            scheduleListener.remove();
            scheduleListener = null;
        }
        if (weeklyPlansListener != null) {
            weeklyPlansListener.remove();
            weeklyPlansListener = null;
        }
        if (todoTasksListener != null) {
            todoTasksListener.remove();
            todoTasksListener = null;
        }

        // Get start and end of month
        Calendar startOfMonth = (Calendar) currentCalendar.clone();
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1);
        startOfMonth.set(Calendar.HOUR_OF_DAY, 0);
        startOfMonth.set(Calendar.MINUTE, 0);
        startOfMonth.set(Calendar.SECOND, 0);

        Calendar endOfMonth = (Calendar) currentCalendar.clone();
        endOfMonth.set(Calendar.DAY_OF_MONTH, currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        endOfMonth.set(Calendar.HOUR_OF_DAY, 23);
        endOfMonth.set(Calendar.MINUTE, 59);
        endOfMonth.set(Calendar.SECOND, 59);

        Timestamp startTimestamp = new Timestamp(startOfMonth.getTime());
        Timestamp endTimestamp = new Timestamp(endOfMonth.getTime());

        // Load schedules (todos and holidays)
        scheduleListener = db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .whereGreaterThanOrEqualTo("date", startTimestamp)
                .whereLessThanOrEqualTo("date", endTimestamp)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }

                    // Clear only non-weekly and non-todo_task items
                    Map<String, List<Schedule>> tempMap = new HashMap<>();
                    for (Map.Entry<String, List<Schedule>> entry : dateSchedulesMap.entrySet()) {
                        List<Schedule> keepItems = new ArrayList<>();
                        for (Schedule s : entry.getValue()) {
                            if ("weekly".equals(s.getCategory()) || "todo_task".equals(s.getCategory())) {
                                keepItems.add(s);
                            }
                        }
                        if (!keepItems.isEmpty()) {
                            tempMap.put(entry.getKey(), keepItems);
                        }
                    }
                    dateSchedulesMap.clear();
                    dateSchedulesMap.putAll(tempMap);

                    if (snapshots != null) {
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                            // ✅ SKIP DELETED ITEMS
                            if (doc.get("deletedAt") != null) {
                                continue;
                            }

                            Schedule schedule = doc.toObject(Schedule.class);
                            schedule.setId(doc.getId());

                            if (schedule.getDate() != null) {
                                if ("weekly".equals(schedule.getCategory())) {
                                    continue;
                                }

                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                String dateKey = sdf.format(schedule.getDate().toDate());

                                if (!dateSchedulesMap.containsKey(dateKey)) {
                                    dateSchedulesMap.put(dateKey, new ArrayList<>());
                                }
                                dateSchedulesMap.get(dateKey).add(schedule);
                            }
                        }
                    }

                    loadWeeklyPlansForMonth();
                    loadScheduledTodoTasks();
                });
    }

    // ========================================
    // ✅ NEW: LOAD SCHEDULED TODO TASKS
    // ========================================
    private void loadScheduledTodoTasks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Calendar monthStart = (Calendar) currentCalendar.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        monthStart.set(Calendar.HOUR_OF_DAY, 0);
        monthStart.set(Calendar.MINUTE, 0);
        monthStart.set(Calendar.SECOND, 0);

        Calendar monthEnd = (Calendar) currentCalendar.clone();
        monthEnd.set(Calendar.DAY_OF_MONTH, currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        monthEnd.set(Calendar.HOUR_OF_DAY, 23);
        monthEnd.set(Calendar.MINUTE, 59);
        monthEnd.set(Calendar.SECOND, 59);

        Timestamp startTimestamp = new Timestamp(monthStart.getTime());
        Timestamp endTimestamp = new Timestamp(monthEnd.getTime());

        Log.d(TAG, "📅 Loading scheduled todo tasks for month");

        todoTasksListener = db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .addSnapshotListener((listSnapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Todo lists listen failed.", e);
                        return;
                    }

                    if (listSnapshots == null || listSnapshots.isEmpty()) {
                        Log.d(TAG, "⚠️ No todo lists found");
                        calendarAdapter.notifyDataSetChanged();
                        return;
                    }

                    Log.d(TAG, "📋 Found " + listSnapshots.size() + " todo list(s)");

                    // Clear old todo tasks from map
                    for (Map.Entry<String, List<Schedule>> entry : dateSchedulesMap.entrySet()) {
                        List<Schedule> schedules = entry.getValue();
                        for (int i = schedules.size() - 1; i >= 0; i--) {
                            if ("todo_task".equals(schedules.get(i).getCategory())) {
                                schedules.remove(i);
                            }
                        }
                    }

                    AtomicInteger completedQueries = new AtomicInteger(0);
                    int totalLists = listSnapshots.size();

                    for (com.google.firebase.firestore.QueryDocumentSnapshot listDoc : listSnapshots) {
                        // ✅ SKIP DELETED TODO LISTS
                        if (listDoc.get("deletedAt") != null) {
                            Log.d(TAG, "⭕️ Skipping deleted todo list: " + listDoc.getId());
                            int completed = completedQueries.incrementAndGet();
                            if (completed == totalLists) {
                                Log.d(TAG, "✅ All todo lists processed");
                                calendarAdapter.notifyDataSetChanged();
                                continue;
                            }
                        }

                        String listId = listDoc.getId();
                        String listTitle = listDoc.getString("title");

                        loadTasksFromTodoList(listId, listTitle, startTimestamp, endTimestamp, () -> {
                            int completed = completedQueries.incrementAndGet();
                            if (completed == totalLists) {
                                Log.d(TAG, "✅ All todo lists processed");
                                calendarAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                });
    }


    // ========================================
    // ✅ NEW: LOAD TASKS FROM SPECIFIC TODO LIST
    // ========================================
    private void loadTasksFromTodoList(String listId, String listTitle,
                                       Timestamp startTimestamp, Timestamp endTimestamp,
                                       Runnable onComplete) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .document(listId)
                .collection("tasks")
                .whereGreaterThanOrEqualTo("scheduleDate", startTimestamp)
                .whereLessThanOrEqualTo("scheduleDate", endTimestamp)
                .get()
                .addOnSuccessListener(taskSnapshots -> {
                    if (taskSnapshots.isEmpty()) {
                        Log.d(TAG, "⚠️ No scheduled tasks in list: " + listTitle);
                    } else {
                        Log.d(TAG, "📋 Found " + taskSnapshots.size() + " scheduled task(s) in: " + listTitle);
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                    for (com.google.firebase.firestore.QueryDocumentSnapshot taskDoc : taskSnapshots) {
                        Timestamp scheduleDate = taskDoc.getTimestamp("scheduleDate");
                        String taskText = taskDoc.getString("taskText");
                        Boolean isCompleted = taskDoc.getBoolean("isCompleted");
                        String scheduleTime = taskDoc.getString("scheduleTime");

                        if (scheduleDate != null && taskText != null && !taskText.trim().isEmpty()) {
                            // ✅ Skip completed tasks - don't show in calendar
                            if (isCompleted != null && isCompleted) {
                                Log.d(TAG, "⏭️ Skipping completed task: " + taskText);
                                continue;
                            }

                            String dateKey = sdf.format(scheduleDate.toDate());

                            if (!dateSchedulesMap.containsKey(dateKey)) {
                                dateSchedulesMap.put(dateKey, new ArrayList<>());
                            }

                            Schedule taskSchedule = new Schedule();
                            taskSchedule.setId(listId + "_task_" + taskDoc.getId());
                            taskSchedule.setTitle(taskText);
                            taskSchedule.setDescription("From: " + (listTitle != null ? listTitle : "To-Do List"));
                            taskSchedule.setCategory("todo_task");
                            taskSchedule.setSourceId(listId);
                            taskSchedule.setCompleted(false); // Always false since we filtered completed ones
                            taskSchedule.setDate(scheduleDate);

                            if (scheduleTime != null && !scheduleTime.isEmpty()) {
                                taskSchedule.setTime(scheduleTime);
                            }

                            boolean exists = false;
                            for (Schedule s : dateSchedulesMap.get(dateKey)) {
                                if (s.getId().equals(taskSchedule.getId())) {
                                    exists = true;
                                    break;
                                }
                            }

                            if (!exists) {
                                dateSchedulesMap.get(dateKey).add(taskSchedule);
                                Log.d(TAG, "✅ Added scheduled task: " + taskText + " on " + dateKey);
                            }
                        }
                    }

                    if (onComplete != null) {
                        onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load tasks from list: " + listTitle, e);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }

    private void loadWeeklyPlansForMonth() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Log.d(TAG, "📅 Loading weekly plans for month: " +
                new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentCalendar.getTime()));

        weeklyPlansListener = db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Weekly plans listen failed.", e);
                        return;
                    }

                    // Clear only weekly items from map
                    for (Map.Entry<String, List<Schedule>> entry : dateSchedulesMap.entrySet()) {
                        List<Schedule> schedules = entry.getValue();
                        for (int i = schedules.size() - 1; i >= 0; i--) {
                            if ("weekly".equals(schedules.get(i).getCategory())) {
                                schedules.remove(i);
                            }
                        }
                    }

                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "⚠️ No weekly plans found");
                        calendarAdapter.notifyDataSetChanged();
                        return;
                    }

                    Log.d(TAG, "📋 Found " + queryDocumentSnapshots.size() + " weekly plan(s)");

                    // ✅ NEW: Track how many plans we need to process
                    final int totalPlans = queryDocumentSnapshots.size();
                    final int[] processedPlans = {0};

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        // ✅ SKIP DELETED WEEKLY PLANS
                        if (doc.get("deletedAt") != null) {
                            Log.d(TAG, "⭕️ Skipping deleted weekly plan: " + doc.getId());
                            processedPlans[0]++;
                            if (processedPlans[0] == totalPlans) {
                                calendarAdapter.notifyDataSetChanged();
                                continue;
                            }
                        }

                        Timestamp startDateTimestamp = doc.getTimestamp("startDate");
                        Timestamp endDateTimestamp = doc.getTimestamp("endDate");

                        if (startDateTimestamp != null && endDateTimestamp != null) {
                            Calendar planStart = Calendar.getInstance();
                            planStart.setTime(startDateTimestamp.toDate());

                            Calendar planEnd = Calendar.getInstance();
                            planEnd.setTime(endDateTimestamp.toDate());

                            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                            Log.d(TAG, "📅 Weekly plan '" + doc.getId() + "' range: " +
                                    sdf.format(planStart.getTime()) + " - " + sdf.format(planEnd.getTime()));

                            Calendar monthStart = (Calendar) currentCalendar.clone();
                            monthStart.set(Calendar.DAY_OF_MONTH, 1);
                            monthStart.set(Calendar.HOUR_OF_DAY, 0);
                            monthStart.set(Calendar.MINUTE, 0);
                            monthStart.set(Calendar.SECOND, 0);

                            Calendar monthEnd = (Calendar) currentCalendar.clone();
                            monthEnd.set(Calendar.DAY_OF_MONTH,
                                    currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                            monthEnd.set(Calendar.HOUR_OF_DAY, 23);
                            monthEnd.set(Calendar.MINUTE, 59);
                            monthEnd.set(Calendar.SECOND, 59);

                            if (!planEnd.before(monthStart) && !planStart.after(monthEnd)) {
                                Log.d(TAG, "✅ Plan overlaps with current month - loading tasks");
                                loadWeeklyPlanTasks(doc.getId(), planStart, planEnd, monthStart, monthEnd,
                                        () -> {
                                            // ✅ Callback when done loading this plan
                                            processedPlans[0]++;
                                            Log.d(TAG, "✅ Processed plan " + processedPlans[0] + "/" + totalPlans);
                                            if (processedPlans[0] == totalPlans) {
                                                calendarAdapter.notifyDataSetChanged();
                                            }
                                        });
                            } else {
                                Log.d(TAG, "⭕️ Plan doesn't overlap with current month - skipping");
                                processedPlans[0]++;
                                if (processedPlans[0] == totalPlans) {
                                    calendarAdapter.notifyDataSetChanged();
                                }
                            }
                        } else {
                            Log.e(TAG, "❌ Plan missing start/end date");
                            processedPlans[0]++;
                            if (processedPlans[0] == totalPlans) {
                                calendarAdapter.notifyDataSetChanged();
                            }
                        }
                    }
                });
    }

    private void loadWeeklyPlanTasks(String planId, Calendar planStart, Calendar planEnd,
                                     Calendar monthStart, Calendar monthEnd, Runnable onComplete) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .get()
                .addOnSuccessListener(taskSnapshots -> {
                    // ✅ Track tasks to process
                    final int totalTasks = taskSnapshots.size();
                    final int[] processedTasks = {0};

                    if (totalTasks == 0) {
                        if (onComplete != null) onComplete.run();
                        return;
                    }

                    for (com.google.firebase.firestore.QueryDocumentSnapshot taskDoc : taskSnapshots) {
                        String day = taskDoc.getString("day");
                        String taskText = taskDoc.getString("taskText");
                        Boolean isCompleted = taskDoc.getBoolean("isCompleted");

                        if (taskText == null || taskText.trim().isEmpty()) {
                            processedTasks[0]++;
                            if (processedTasks[0] == totalTasks && onComplete != null) {
                                onComplete.run();
                            }
                            continue;
                        }

                        distributeTaskToMatchingDatesInRange(
                                planId,
                                taskDoc.getId(),
                                day,
                                taskText,
                                isCompleted,
                                planStart,
                                planEnd,
                                monthStart,
                                monthEnd,
                                () -> {
                                    // ✅ Callback when this task is distributed
                                    processedTasks[0]++;
                                    if (processedTasks[0] == totalTasks && onComplete != null) {
                                        onComplete.run();
                                    }
                                }
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load weekly plan tasks", e);
                    if (onComplete != null) onComplete.run();
                });
    }

    private void distributeTaskToMatchingDatesInRange(
            String planId,
            String taskDocId,
            String dayName,
            String taskText,
            Boolean isCompleted,
            Calendar planStart,
            Calendar planEnd,
            Calendar monthStart,
            Calendar monthEnd,
            Runnable onComplete) {

        // ✅ Load ALL day schedules for this specific day
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .collection("daySchedules")
                .whereEqualTo("day", dayName)
                .orderBy("scheduleNumber")
                .get()
                .addOnSuccessListener(dayScheduleDocs -> {
                    List<DaySchedule> daySchedules = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : dayScheduleDocs) {
                        DaySchedule schedule = doc.toObject(DaySchedule.class);
                        daySchedules.add(schedule);
                    }

                    // ✅ If this day has specific schedules, add task for each schedule
                    if (!daySchedules.isEmpty()) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                        for (DaySchedule schedule : daySchedules) {
                            if (schedule.getDate() != null) {
                                Calendar scheduleCalendar = Calendar.getInstance();
                                scheduleCalendar.setTime(schedule.getDate().toDate());

                                // Check if scheduled date is in current month
                                if (!scheduleCalendar.before(monthStart) &&
                                        !scheduleCalendar.after(monthEnd)) {
                                    String dateKey = sdf.format(scheduleCalendar.getTime());

                                    if (!dateSchedulesMap.containsKey(dateKey)) {
                                        dateSchedulesMap.put(dateKey, new ArrayList<>());
                                    }

                                    // Skip completed tasks
                                    if (isCompleted != null && isCompleted) {
                                        continue;
                                    }

                                    // ✅ Create unique ID with schedule number
                                    String uniqueId = planId + "_" + taskDocId + "_" +
                                            dateKey + "_sched_" + schedule.getScheduleNumber();

                                    Schedule taskSchedule = new Schedule();
                                    taskSchedule.setId(uniqueId);
                                    taskSchedule.setTitle(taskText);
                                    taskSchedule.setCategory("weekly");
                                    taskSchedule.setSourceId(planId);
                                    taskSchedule.setCompleted(false);
                                    taskSchedule.setDate(new Timestamp(scheduleCalendar.getTime()));
                                    taskSchedule.setTime(schedule.getTime());

                                    boolean exists = false;
                                    for (Schedule s : dateSchedulesMap.get(dateKey)) {
                                        if (s.getId().equals(taskSchedule.getId())) {
                                            exists = true;
                                            break;
                                        }
                                    }

                                    if (!exists) {
                                        dateSchedulesMap.get(dateKey).add(taskSchedule);
                                        Log.d(TAG, "✅ Added scheduled task for " + dayName +
                                                " on " + dateKey + " (Schedule " +
                                                schedule.getScheduleNumber() + ")");
                                    }
                                }
                            }
                        }

                        // ✅ Done processing this task
                        if (onComplete != null) onComplete.run();
                    } else {
                        // No specific schedule - use original logic
                        distributeTaskAcrossWeekRange(planId, taskDocId, dayName, taskText,
                                isCompleted, planStart, planEnd, monthStart, monthEnd, null);

                        // ✅ Done processing this task
                        if (onComplete != null) onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load day schedules for " + dayName, e);

                    // On failure, use fallback
                    distributeTaskAcrossWeekRange(planId, taskDocId, dayName, taskText,
                            isCompleted, planStart, planEnd, monthStart, monthEnd, null);

                    if (onComplete != null) onComplete.run();
                });
    }
    // ✅ ADD original distribution logic as separate method
    private void distributeTaskAcrossWeekRange(
            String planId,
            String taskDocId,
            String dayName,
            String taskText,
            Boolean isCompleted,
            Calendar planStart,
            Calendar planEnd,
            Calendar monthStart,
            Calendar monthEnd,
            String scheduleTime) {

        int targetDayOfWeek = getDayOfWeekFromName(dayName);
        if (targetDayOfWeek == -1) {
            Log.e(TAG, "❌ Invalid day name: " + dayName);
            return;
        }

        Calendar current = Calendar.getInstance();
        if (planStart.after(monthStart)) {
            current.setTime(planStart.getTime());
        } else {
            current.setTime(monthStart.getTime());
        }

        current.set(Calendar.HOUR_OF_DAY, 0);
        current.set(Calendar.MINUTE, 0);
        current.set(Calendar.SECOND, 0);
        current.set(Calendar.MILLISECOND, 0);

        Calendar endLimit = Calendar.getInstance();
        if (planEnd.before(monthEnd)) {
            endLimit.setTime(planEnd.getTime());
        } else {
            endLimit.setTime(monthEnd.getTime());
        }

        int daysSearched = 0;
        while (current.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek && !current.after(endLimit)) {
            current.add(Calendar.DAY_OF_MONTH, 1);
            daysSearched++;
            if (daysSearched > 7) {
                return;
            }
        }

        if (current.after(endLimit)) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        int occurrencesAdded = 0;

        while (!current.after(endLimit)) {
            String dateKey = sdf.format(current.getTime());

            if (!dateSchedulesMap.containsKey(dateKey)) {
                dateSchedulesMap.put(dateKey, new ArrayList<>());
            }

            if (isCompleted != null && isCompleted) {
                current.add(Calendar.DAY_OF_MONTH, 7);
                continue;
            }

            Schedule taskSchedule = new Schedule();
            taskSchedule.setId(planId + "_" + taskDocId + "_" + dateKey);
            taskSchedule.setTitle(taskText);
            taskSchedule.setCategory("weekly");
            taskSchedule.setSourceId(planId);
            taskSchedule.setCompleted(false);
            taskSchedule.setDate(new Timestamp(current.getTime()));
            taskSchedule.setTime(scheduleTime);

            boolean exists = false;
            for (Schedule s : dateSchedulesMap.get(dateKey)) {
                if (s.getId().equals(taskSchedule.getId())) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                dateSchedulesMap.get(dateKey).add(taskSchedule);
                occurrencesAdded++;
            }

            current.add(Calendar.DAY_OF_MONTH, 7);
        }

        if (occurrencesAdded > 0) {
            Log.d(TAG, "🎉 Distributed " + occurrencesAdded + " occurrences for " + dayName);
        }
    }

    private int getDayOfWeekFromName(String dayName) {
        if (dayName == null) return -1;

        switch (dayName) {
            case "Sun": return Calendar.SUNDAY;
            case "Mon": return Calendar.MONDAY;
            case "Tues": return Calendar.TUESDAY;
            case "Wed": return Calendar.WEDNESDAY;
            case "Thur": return Calendar.THURSDAY;
            case "Fri": return Calendar.FRIDAY;
            case "Sat": return Calendar.SATURDAY;
            default: return -1;
        }
    }

    private void showMonthYearPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_month_year_picker, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Get views
        TextView selectedYearText = dialogView.findViewById(R.id.selectedYearText);
        ImageView prevYearButton = dialogView.findViewById(R.id.prevYearButton);
        ImageView nextYearButton = dialogView.findViewById(R.id.nextYearButton);

        // Month buttons
        TextView janBtn = dialogView.findViewById(R.id.janBtn);
        TextView febBtn = dialogView.findViewById(R.id.febBtn);
        TextView marBtn = dialogView.findViewById(R.id.marBtn);
        TextView aprBtn = dialogView.findViewById(R.id.aprBtn);
        TextView mayBtn = dialogView.findViewById(R.id.mayBtn);
        TextView junBtn = dialogView.findViewById(R.id.junBtn);
        TextView julBtn = dialogView.findViewById(R.id.julBtn);
        TextView augBtn = dialogView.findViewById(R.id.augBtn);
        TextView sepBtn = dialogView.findViewById(R.id.sepBtn);
        TextView octBtn = dialogView.findViewById(R.id.octBtn);
        TextView novBtn = dialogView.findViewById(R.id.novBtn);
        TextView decBtn = dialogView.findViewById(R.id.decBtn);

        TextView[] monthButtons = {janBtn, febBtn, marBtn, aprBtn, mayBtn, junBtn,
                julBtn, augBtn, sepBtn, octBtn, novBtn, decBtn};

        // Store selected year in an array so we can modify it in listeners
        final int[] selectedYear = {currentCalendar.get(Calendar.YEAR)};
        final int currentMonth = currentCalendar.get(Calendar.MONTH);

        // Update year display
        selectedYearText.setText(String.valueOf(selectedYear[0]));

        // Highlight current month
        updateMonthButtonsHighlight(monthButtons, currentMonth);

        // Year navigation
        prevYearButton.setOnClickListener(v -> {
            selectedYear[0]--;
            selectedYearText.setText(String.valueOf(selectedYear[0]));
        });

        nextYearButton.setOnClickListener(v -> {
            selectedYear[0]++;
            selectedYearText.setText(String.valueOf(selectedYear[0]));
        });

        // Month selection
        for (int i = 0; i < monthButtons.length; i++) {
            final int monthIndex = i;
            monthButtons[i].setOnClickListener(v -> {
                currentCalendar.set(Calendar.YEAR, selectedYear[0]);
                currentCalendar.set(Calendar.MONTH, monthIndex);
                updateCalendarDisplay();
                loadSchedulesForMonth();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void updateMonthButtonsHighlight(TextView[] monthButtons, int selectedMonth) {
        for (int i = 0; i < monthButtons.length; i++) {
            if (i == selectedMonth) {
                // Highlight selected month
                monthButtons[i].setBackgroundResource(R.drawable.bg_month_selected);
                monthButtons[i].setTextColor(getResources().getColor(android.R.color.black));
            } else {
                // Normal month
                monthButtons[i].setBackgroundResource(R.drawable.bg_month_normal);
                monthButtons[i].setTextColor(getResources().getColor(android.R.color.black));
            }
        }
    }
    private void showMonthPicker() {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        // Highlight current selected month
        int currentMonth = currentCalendar.get(Calendar.MONTH);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select Month");
        builder.setSingleChoiceItems(months, currentMonth, (dialog, which) -> {
            currentCalendar.set(Calendar.MONTH, which);
            updateCalendarDisplay();
            loadSchedulesForMonth();
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showYearPicker() {
        // Generate years from 2020 to 2030 (or you can customize this range)
        int currentYear = currentCalendar.get(Calendar.YEAR);
        int startYear = 2020;
        int endYear = 2035;

        int totalYears = endYear - startYear + 1;
        String[] years = new String[totalYears];
        int selectedIndex = 0;

        for (int i = 0; i < totalYears; i++) {
            int year = startYear + i;
            years[i] = String.valueOf(year);
            if (year == currentYear) {
                selectedIndex = i;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select Year");
        builder.setSingleChoiceItems(years, selectedIndex, (dialog, which) -> {
            int selectedYear = startYear + which;
            currentCalendar.set(Calendar.YEAR, selectedYear);
            updateCalendarDisplay();
            loadSchedulesForMonth();
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scheduleListener != null) {
            scheduleListener.remove();
            scheduleListener = null;
        }
        if (weeklyPlansListener != null) {
            weeklyPlansListener.remove();
            weeklyPlansListener = null;
        }
        // ✅ NEW
        if (todoTasksListener != null) {
            todoTasksListener.remove();
            todoTasksListener = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentCalendar != null) {
            loadSchedulesForMonth();
        }
    }

    public static class CalendarDay {
        private String dayNumber;
        private boolean isCurrentMonth;
        private String dateKey;
        private boolean isToday;

        public CalendarDay(String dayNumber, boolean isCurrentMonth, String dateKey) {
            this(dayNumber, isCurrentMonth, dateKey, false);
        }

        public CalendarDay(String dayNumber, boolean isCurrentMonth, String dateKey, boolean isToday) {
            this.dayNumber = dayNumber;
            this.isCurrentMonth = isCurrentMonth;
            this.dateKey = dateKey;
            this.isToday = isToday;
        }

        public String getDayNumber() { return dayNumber; }
        public boolean isCurrentMonth() { return isCurrentMonth; }
        public String getDateKey() { return dateKey; }
        public boolean isToday() { return isToday; }
    }
}