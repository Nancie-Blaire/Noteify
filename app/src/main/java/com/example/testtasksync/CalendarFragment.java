package com.example.testtasksync;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarFragment extends Fragment {

    private static final String TAG = "CalendarFragment";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration scheduleListener;

    private TextView monthYearText;
    private RecyclerView calendarGridRecyclerView;
    private CalendarGridAdapter calendarAdapter;


    private Calendar currentCalendar;
    private Map<String, List<Schedule>> dateSchedulesMap; // "yyyy-MM-dd" -> List<Schedule>

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check if user is logged in
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Initialize UI
        monthYearText = view.findViewById(R.id.monthYearText);
        calendarGridRecyclerView = view.findViewById(R.id.calendarGridRecyclerView);
        View monthYearHeader = view.findViewById(R.id.monthYearHeader);

        // Initialize data
        currentCalendar = Calendar.getInstance();
        dateSchedulesMap = new HashMap<>();

        // Set up calendar grid
        calendarGridRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 7));
        calendarAdapter = new CalendarGridAdapter(new ArrayList<>(), dateSchedulesMap);
        calendarGridRecyclerView.setAdapter(calendarAdapter);

        // Month/Year selector
        monthYearHeader.setOnClickListener(v -> showMonthYearPicker());

        // Load month data
        updateCalendarDisplay();
        loadSchedulesForMonth();
    }

    private void updateCalendarDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM ▼", Locale.getDefault());
        monthYearText.setText(sdf.format(currentCalendar.getTime()));

        // Generate calendar days
        List<CalendarDay> days = generateCalendarDays(currentCalendar);
        calendarAdapter.updateDays(days);
    }

    private List<CalendarDay> generateCalendarDays(Calendar calendar) {
        List<CalendarDay> days = new ArrayList<>();

        Calendar cal = (Calendar) calendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Add empty cells for days before the 1st
        for (int i = Calendar.SUNDAY; i < firstDayOfWeek; i++) {
            days.add(new CalendarDay("", false, null));
        }

        // Add actual days
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

        // Remove old listener
        if (scheduleListener != null) {
            scheduleListener.remove();
            scheduleListener = null;
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

        // Load all schedules for this month
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

                    dateSchedulesMap.clear();

                    if (snapshots != null) {
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                            Schedule schedule = doc.toObject(Schedule.class);
                            schedule.setId(doc.getId());

                            if (schedule.getDate() != null) {
                                // Ã¢Å“â€¦ FIXED: Skip weekly schedules here - they'll be loaded separately
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

                    // Ã¢Å“â€¦ NOW load weekly plans and distribute tasks to correct dates
                    loadWeeklyPlansForMonth();
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
            Calendar monthEnd) {

        // Get the target day of week number
        int targetDayOfWeek = getDayOfWeekFromName(dayName);
        if (targetDayOfWeek == -1) {
            Log.e(TAG, "❌ Invalid day name: " + dayName);
            return;
        }

        // Normalize all dates to midnight for proper comparison
        Calendar planStartNorm = (Calendar) planStart.clone();
        planStartNorm.set(Calendar.HOUR_OF_DAY, 0);
        planStartNorm.set(Calendar.MINUTE, 0);
        planStartNorm.set(Calendar.SECOND, 0);
        planStartNorm.set(Calendar.MILLISECOND, 0);

        Calendar planEndNorm = (Calendar) planEnd.clone();
        planEndNorm.set(Calendar.HOUR_OF_DAY, 23);
        planEndNorm.set(Calendar.MINUTE, 59);
        planEndNorm.set(Calendar.SECOND, 59);
        planEndNorm.set(Calendar.MILLISECOND, 999);

        Calendar monthStartNorm = (Calendar) monthStart.clone();
        monthStartNorm.set(Calendar.HOUR_OF_DAY, 0);
        monthStartNorm.set(Calendar.MINUTE, 0);
        monthStartNorm.set(Calendar.SECOND, 0);
        monthStartNorm.set(Calendar.MILLISECOND, 0);

        Calendar monthEndNorm = (Calendar) monthEnd.clone();
        monthEndNorm.set(Calendar.HOUR_OF_DAY, 23);
        monthEndNorm.set(Calendar.MINUTE, 59);
        monthEndNorm.set(Calendar.SECOND, 59);
        monthEndNorm.set(Calendar.MILLISECOND, 999);

        // Start from the LATER of planStart or monthStart
        Calendar current = Calendar.getInstance();
        if (planStartNorm.after(monthStartNorm)) {
            current.setTime(planStartNorm.getTime());
        } else {
            current.setTime(monthStartNorm.getTime());
        }

        current.set(Calendar.HOUR_OF_DAY, 0);
        current.set(Calendar.MINUTE, 0);
        current.set(Calendar.SECOND, 0);
        current.set(Calendar.MILLISECOND, 0);

        // End is the EARLIER of planEnd or monthEnd
        Calendar endLimit = Calendar.getInstance();
        if (planEndNorm.before(monthEndNorm)) {
            endLimit.setTime(planEndNorm.getTime());
        } else {
            endLimit.setTime(monthEndNorm.getTime());
        }
        endLimit.set(Calendar.HOUR_OF_DAY, 23);
        endLimit.set(Calendar.MINUTE, 59);
        endLimit.set(Calendar.SECOND, 59);
        endLimit.set(Calendar.MILLISECOND, 999);

        SimpleDateFormat debugFormat = new SimpleDateFormat("MMM dd, yyyy (EEE)", Locale.getDefault());
        Log.d(TAG, "🔍 Looking for " + dayName + " between " +
                debugFormat.format(current.getTime()) + " and " +
                debugFormat.format(endLimit.getTime()));

        // Find the first occurrence of the target day starting from current
        int daysSearched = 0;
        while (current.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek && !current.after(endLimit)) {
            current.add(Calendar.DAY_OF_MONTH, 1);
            daysSearched++;
            if (daysSearched > 7) {
                Log.e(TAG, "❌ Couldn't find " + dayName + " in the first week");
                return;
            }
        }

        // If we went past the end limit, no matching days exist
        if (current.after(endLimit)) {
            Log.d(TAG, "⚠️ No " + dayName + " found in range");
            return;
        }

        Log.d(TAG, "✅ First " + dayName + " found: " + debugFormat.format(current.getTime()));

        // Now iterate through all occurrences of this day within the range
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        int occurrencesAdded = 0;

        while (!current.after(endLimit)) {
            // Normalize current date for comparison
            Calendar currentNorm = (Calendar) current.clone();
            currentNorm.set(Calendar.HOUR_OF_DAY, 12); // Use noon to avoid edge cases
            currentNorm.set(Calendar.MINUTE, 0);
            currentNorm.set(Calendar.SECOND, 0);
            currentNorm.set(Calendar.MILLISECOND, 0);

            // Check if current date is within BOTH the plan range AND the month range
            boolean isInPlanRange = !currentNorm.before(planStartNorm) && !currentNorm.after(planEndNorm);
            boolean isInMonthRange = !currentNorm.before(monthStartNorm) && !currentNorm.after(monthEndNorm);

            Log.d(TAG, "📅 Checking " + debugFormat.format(currentNorm.getTime()) +
                    " - In plan range: " + isInPlanRange + ", In month range: " + isInMonthRange);

            if (isInPlanRange && isInMonthRange) {
                String dateKey = sdf.format(currentNorm.getTime());

                if (!dateSchedulesMap.containsKey(dateKey)) {
                    dateSchedulesMap.put(dateKey, new ArrayList<>());
                }

                // Create task schedule for this date
                Schedule taskSchedule = new Schedule();
                taskSchedule.setId(planId + "_" + taskDocId + "_" + dateKey);
                taskSchedule.setTitle(taskText);
                taskSchedule.setCategory("weekly");
                taskSchedule.setSourceId(planId);
                taskSchedule.setCompleted(isCompleted != null && isCompleted);
                taskSchedule.setDate(new Timestamp(currentNorm.getTime()));

                // Check if not already added
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
                    Log.d(TAG, "✅ Added task '" + taskText + "' to " + dateKey);
                } else {
                    Log.d(TAG, "⚠️ Task already exists for " + dateKey);
                }
            } else {
                Log.d(TAG, "⏭️ Skipping " + debugFormat.format(currentNorm.getTime()) +
                        " (out of range)");
            }

            // Move to next week (same day, 7 days later)
            current.add(Calendar.DAY_OF_MONTH, 7);
        }

        if (occurrencesAdded == 0) {
            Log.w(TAG, "⚠️ No occurrences of '" + taskText + "' were added for " + dayName);
        } else {
            Log.d(TAG, "🎉 Distributed " + occurrencesAdded + " occurrences of '" + taskText + "' for " + dayName);
        }
    }
    private void loadWeeklyPlansForMonth() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Log.d(TAG, "ðŸ“… Loading weekly plans for month: " +
                new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentCalendar.getTime()));

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "âš ï¸ No weekly plans found");
                        calendarAdapter.notifyDataSetChanged();
                        return;
                    }

                    Log.d(TAG, "ðŸ“‹ Found " + queryDocumentSnapshots.size() + " weekly plan(s)");

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        // Get the plan details to check date range
                        Timestamp startDateTimestamp = doc.getTimestamp("startDate");
                        Timestamp endDateTimestamp = doc.getTimestamp("endDate");

                        if (startDateTimestamp != null && endDateTimestamp != null) {
                            Calendar planStart = Calendar.getInstance();
                            planStart.setTime(startDateTimestamp.toDate());

                            Calendar planEnd = Calendar.getInstance();
                            planEnd.setTime(endDateTimestamp.toDate());

                            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                            Log.d(TAG, "ðŸ“… Weekly plan '" + doc.getId() + "' range: " +
                                    sdf.format(planStart.getTime()) + " - " + sdf.format(planEnd.getTime()));

                            // Check if plan overlaps with current month
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

                            // Check if plan's date range overlaps with current month
                            if (!planEnd.before(monthStart) && !planStart.after(monthEnd)) {
                                Log.d(TAG, "âœ… Plan overlaps with current month - loading tasks");
                                // Load tasks for this plan
                                loadWeeklyPlanTasks(doc.getId(), planStart, planEnd, monthStart, monthEnd);
                            } else {
                                Log.d(TAG, "â­ï¸ Plan doesn't overlap with current month - skipping");
                            }
                        } else {
                            Log.e(TAG, "âŒ Plan missing start/end date");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load weekly plans", e);
                    calendarAdapter.notifyDataSetChanged();
                });
    }


    private void loadWeeklyPlanTasks(String planId, Calendar planStart, Calendar planEnd,
                                     Calendar monthStart, Calendar monthEnd) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .get()
                .addOnSuccessListener(taskSnapshots -> {
                    for (com.google.firebase.firestore.QueryDocumentSnapshot taskDoc : taskSnapshots) {
                        String day = taskDoc.getString("day"); // "Mon", "Tues", etc.
                        String taskText = taskDoc.getString("taskText");
                        Boolean isCompleted = taskDoc.getBoolean("isCompleted");

                        // Skip empty tasks
                        if (taskText == null || taskText.trim().isEmpty()) {
                            continue;
                        }

                        // Find ALL occurrences of this day within the plan's date range
                        distributeTaskToMatchingDatesInRange(
                                planId,
                                taskDoc.getId(),
                                day,
                                taskText,
                                isCompleted,
                                planStart,
                                planEnd,
                                monthStart,
                                monthEnd
                        );
                    }

                    // Update UI after all tasks are distributed
                    calendarAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load weekly plan tasks", e);
                });
    }


    private int getDayOfWeekFromName(String dayName) {
        if (dayName == null) return -1;

        switch (dayName) {
            case "Sun":
                return Calendar.SUNDAY;
            case "Mon":
                return Calendar.MONDAY;
            case "Tues":
                return Calendar.TUESDAY;
            case "Wed":
                return Calendar.WEDNESDAY;
            case "Thur":
                return Calendar.THURSDAY;
            case "Fri":
                return Calendar.FRIDAY;
            case "Sat":
                return Calendar.SATURDAY;
            default:
                return -1;
        }
    }

    private void showMonthYearPicker() {
        // Simple month/year picker (you can enhance this)
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select Month");
        builder.setItems(months, (dialog, which) -> {
            currentCalendar.set(Calendar.MONTH, which);
            updateCalendarDisplay();
            loadSchedulesForMonth();
        });
        builder.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scheduleListener != null) {
            scheduleListener.remove();
            scheduleListener = null;
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        // Reload schedules when fragment becomes visible again
        if (currentCalendar != null) {
            loadSchedulesForMonth();
        }
    }
    // Calendar Day Model
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