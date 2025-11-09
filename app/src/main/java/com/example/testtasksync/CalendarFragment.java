package com.example.testtasksync;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarFragment extends Fragment {

    private static final String TAG = "CalendarFragment";

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // UI Elements
    private CalendarView calendarView;
    private TextView monthYearText;
    private ImageView addScheduleButton;
    private TextView selectedDateText;
    private TextView scheduleCountText;
    private LinearLayout holidayBanner;
    private TextView holidayNameText;
    private RecyclerView schedulesRecyclerView;
    private LinearLayout emptyStateLayout;

    // Data
    private List<Schedule> scheduleList;
    private ScheduleAdapter adapter;
    private java.util.Calendar selectedCalendar;
    private Map<String, Integer> scheduleCountMap; // date -> count

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
        initializeViews(view);

        // Initialize data
        scheduleList = new ArrayList<>();
        scheduleCountMap = new HashMap<>();
        selectedCalendar = java.util.Calendar.getInstance();

        // Set up adapter
        adapter = new ScheduleAdapter(scheduleList, new ScheduleAdapter.OnScheduleClickListener() {
            @Override
            public void onScheduleClick(Schedule schedule) {
                showScheduleDetailsDialog(schedule);
            }

            @Override
            public void onScheduleLongClick(Schedule schedule) {
                showDeleteConfirmationDialog(schedule);
            }
        });

        schedulesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        schedulesRecyclerView.setAdapter(adapter);

        // Set up listeners
        setupListeners();

        // Load schedules for today
        loadSchedulesForDate(selectedCalendar);
        updateMonthYearText();
        checkIfHoliday(selectedCalendar);
    }

    private void initializeViews(View view) {
        calendarView = view.findViewById(R.id.calendarView);
        monthYearText = view.findViewById(R.id.monthYearText);
        addScheduleButton = view.findViewById(R.id.addScheduleButton);
        selectedDateText = view.findViewById(R.id.selectedDateText);
        scheduleCountText = view.findViewById(R.id.scheduleCountText);
        holidayBanner = view.findViewById(R.id.holidayBanner);
        holidayNameText = view.findViewById(R.id.holidayNameText);
        schedulesRecyclerView = view.findViewById(R.id.schedulesRecyclerView);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
    }

    private void setupListeners() {
        // Calendar date change listener
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedCalendar.set(year, month, dayOfMonth);
            loadSchedulesForDate(selectedCalendar);
            checkIfHoliday(selectedCalendar);
        });

        // Add schedule button
        addScheduleButton.setOnClickListener(v -> showAddScheduleDialog());
    }

    private void loadSchedulesForDate(java.util.Calendar calendar) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Get start and end of selected day
        java.util.Calendar startOfDay = (java.util.Calendar) calendar.clone();
        startOfDay.set(java.util.Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(java.util.Calendar.MINUTE, 0);
        startOfDay.set(java.util.Calendar.SECOND, 0);

        java.util.Calendar endOfDay = (java.util.Calendar) calendar.clone();
        endOfDay.set(java.util.Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(java.util.Calendar.MINUTE, 59);
        endOfDay.set(java.util.Calendar.SECOND, 59);

        Timestamp startTimestamp = new Timestamp(startOfDay.getTime());
        Timestamp endTimestamp = new Timestamp(endOfDay.getTime());

        // Query schedules for this date
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .whereGreaterThanOrEqualTo("date", startTimestamp)
                .whereLessThanOrEqualTo("date", endTimestamp)
                .orderBy("date", Query.Direction.ASCENDING)

                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }

                    scheduleList.clear();

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Schedule schedule = doc.toObject(Schedule.class);
                            schedule.setId(doc.getId());
                            scheduleList.add(schedule);
                        }
                    }

                    // Update UI
                    updateScheduleDisplay();
                });

        // Update selected date text
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
        selectedDateText.setText(sdf.format(calendar.getTime()));
    }

    private void updateScheduleDisplay() {
        adapter.notifyDataSetChanged();

        int count = scheduleList.size();
        scheduleCountText.setText(count + (count == 1 ? " event" : " events"));

        if (scheduleList.isEmpty()) {
            schedulesRecyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            schedulesRecyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void checkIfHoliday(java.util.Calendar calendar) {
        int year = calendar.get(java.util.Calendar.YEAR);
        int month = calendar.get(java.util.Calendar.MONTH);
        int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);

        if (PhilippineHolidays.isHoliday(year, month, day)) {
            String holidayName = PhilippineHolidays.getHolidayName(year, month, day);
            holidayNameText.setText("🎉 " + holidayName);
            holidayBanner.setVisibility(View.VISIBLE);
        } else {
            holidayBanner.setVisibility(View.GONE);
        }
    }

    private void updateMonthYearText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthYearText.setText(sdf.format(selectedCalendar.getTime()));
    }

    private void showAddScheduleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_schedule, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Get dialog views
        TextInputEditText titleInput = dialogView.findViewById(R.id.scheduleTitleInput);
        TextInputEditText descriptionInput = dialogView.findViewById(R.id.scheduleDescriptionInput);
        LinearLayout datePickerButton = dialogView.findViewById(R.id.datePickerButton);
        TextView selectedDateTextView = dialogView.findViewById(R.id.selectedDateText);
        LinearLayout timePickerButton = dialogView.findViewById(R.id.timePickerButton);
        TextView selectedTimeTextView = dialogView.findViewById(R.id.selectedTimeText);
        ImageView clearTimeButton = dialogView.findViewById(R.id.clearTimeButton);
        RadioGroup categoryRadioGroup = dialogView.findViewById(R.id.categoryRadioGroup);
        SwitchMaterial reminderSwitch = dialogView.findViewById(R.id.reminderSwitch);
        Spinner reminderTimeSpinner = dialogView.findViewById(R.id.reminderTimeSpinner);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveButton);

        // Set up reminder spinner
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.reminder_times,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderTimeSpinner.setAdapter(spinnerAdapter);

        // Initialize with selected date
        final java.util.Calendar scheduleCalendar = (java.util.Calendar) selectedCalendar.clone();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        selectedDateTextView.setText(dateFormat.format(scheduleCalendar.getTime()));

        // Store selected time
        final String[] selectedTime = {null};

        // Date picker
        datePickerButton.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    getContext(),
                    (view, year, month, dayOfMonth) -> {
                        scheduleCalendar.set(year, month, dayOfMonth);
                        selectedDateTextView.setText(dateFormat.format(scheduleCalendar.getTime()));
                    },
                    scheduleCalendar.get(java.util.Calendar.YEAR),
                    scheduleCalendar.get(java.util.Calendar.MONTH),
                    scheduleCalendar.get(java.util.Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // Time picker
        timePickerButton.setOnClickListener(v -> {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            int minute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE);

            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    getContext(),
                    (view, hourOfDay, minuteOfHour) -> {
                        selectedTime[0] = String.format(Locale.getDefault(),
                                "%02d:%02d", hourOfDay, minuteOfHour);
                        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                        try {
                            Date time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .parse(selectedTime[0]);
                            selectedTimeTextView.setText(timeFormat.format(time));
                            clearTimeButton.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            selectedTimeTextView.setText(selectedTime[0]);
                        }
                    },
                    hour,
                    minute,
                    false
            );
            timePickerDialog.show();
        });

        // Clear time button
        clearTimeButton.setOnClickListener(v -> {
            selectedTime[0] = null;
            selectedTimeTextView.setText("Select Time");
            clearTimeButton.setVisibility(View.GONE);
        });

        // Reminder switch
        reminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            reminderTimeSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Save button
        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a title", Toast.LENGTH_SHORT).show();
                return;
            }

            String description = descriptionInput.getText().toString().trim();
            String category = getSelectedCategory(categoryRadioGroup);
            boolean hasReminder = reminderSwitch.isChecked();
            int reminderMinutes = getReminderMinutes(reminderTimeSpinner.getSelectedItemPosition());

            saveSchedule(title, description, scheduleCalendar, selectedTime[0],
                    category, hasReminder, reminderMinutes);
            dialog.dismiss();
        });

        dialog.show();
    }

    private String getSelectedCategory(RadioGroup radioGroup) {
        int selectedId = radioGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.weeklyRadio) {
            return "weekly";
        } else {
            return "todo";  // Default to "todo" instead of "event"
        }
    }

    private int getReminderMinutes(int position) {
        switch (position) {
            case 0: return 5;      // 5 minutes before
            case 1: return 10;     // 10 minutes before
            case 2: return 15;     // 15 minutes before
            case 3: return 30;     // 30 minutes before
            case 4: return 60;     // 1 hour before
            case 5: return 1440;   // 1 day before
            default: return 15;
        }
    }

    private void saveSchedule(String title, String description, java.util.Calendar calendar,
            String time, String category, boolean hasReminder, int reminderMinutes) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Create schedule data
        Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("title", title);
        scheduleData.put("description", description);
        scheduleData.put("date", new Timestamp(calendar.getTime()));
        scheduleData.put("time", time != null ? time : "");
        scheduleData.put("category", category);
        scheduleData.put("isCompleted", false);
        scheduleData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        scheduleData.put("sourceId", "");
        scheduleData.put("hasReminder", hasReminder);
        scheduleData.put("reminderMinutes", reminderMinutes);

        // Save to Firestore
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .add(scheduleData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Schedule saved successfully");
                    Toast.makeText(getContext(), "✓ Schedule added", Toast.LENGTH_SHORT).show();

                    // If reminder is set, schedule notification
                    if (hasReminder && time != null && !time.isEmpty()) {
                        scheduleNotification(documentReference.getId(), title, calendar,
                                time, reminderMinutes);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save schedule", e);
                    Toast.makeText(getContext(), "Failed to add schedule", Toast.LENGTH_SHORT).show();
                });
    }

    private void scheduleNotification(String scheduleId, String title,
            java.util.Calendar calendar, String time, int reminderMinutes) {
        // TODO: Implement notification scheduling using AlarmManager or WorkManager
        // This is a placeholder - you'll need to implement proper notification handling
        Log.d(TAG, "Notification scheduled for: " + title + " at " + time);
        Toast.makeText(getContext(), "🔔 Reminder set for " + reminderMinutes + " minutes before",
                Toast.LENGTH_SHORT).show();
    }

    private void showScheduleDetailsDialog(Schedule schedule) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_schedule_details, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Get views
        TextView titleText = dialogView.findViewById(R.id.scheduleDetailTitle);
        TextView descriptionText = dialogView.findViewById(R.id.scheduleDetailDescription);
        TextView dateText = dialogView.findViewById(R.id.scheduleDetailDate);
        TextView timeText = dialogView.findViewById(R.id.scheduleDetailTime);
        TextView categoryText = dialogView.findViewById(R.id.scheduleDetailCategory);
        TextView reminderText = dialogView.findViewById(R.id.scheduleDetailReminder);
        Button editButton = dialogView.findViewById(R.id.editScheduleButton);
        Button deleteButton = dialogView.findViewById(R.id.deleteScheduleButton);
        Button closeButton = dialogView.findViewById(R.id.closeButton);

        // Set data
        titleText.setText(schedule.getTitle());

        if (schedule.getDescription() != null && !schedule.getDescription().isEmpty()) {
            descriptionText.setText(schedule.getDescription());
            descriptionText.setVisibility(View.VISIBLE);
        } else {
            descriptionText.setVisibility(View.GONE);
        }

        dateText.setText(schedule.getFormattedDate());
        timeText.setText(schedule.getFormattedTime());
        categoryText.setText(getCategoryDisplayName(schedule.getCategory()));

        if (schedule.hasReminder()) {
            reminderText.setText("🔔 " + schedule.getReminderMinutes() + " minutes before");
            reminderText.setVisibility(View.VISIBLE);
        } else {
            reminderText.setVisibility(View.GONE);
        }

        // Button listeners
        editButton.setOnClickListener(v -> {
            dialog.dismiss();
            showEditScheduleDialog(schedule);
        });

        deleteButton.setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteConfirmationDialog(schedule);
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showEditScheduleDialog(Schedule schedule) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_schedule, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Get dialog views
        TextInputEditText titleInput = dialogView.findViewById(R.id.scheduleTitleInput);
        TextInputEditText descriptionInput = dialogView.findViewById(R.id.scheduleDescriptionInput);
        LinearLayout datePickerButton = dialogView.findViewById(R.id.datePickerButton);
        TextView selectedDateTextView = dialogView.findViewById(R.id.selectedDateText);
        LinearLayout timePickerButton = dialogView.findViewById(R.id.timePickerButton);
        TextView selectedTimeTextView = dialogView.findViewById(R.id.selectedTimeText);
        ImageView clearTimeButton = dialogView.findViewById(R.id.clearTimeButton);
        RadioGroup categoryRadioGroup = dialogView.findViewById(R.id.categoryRadioGroup);
        SwitchMaterial reminderSwitch = dialogView.findViewById(R.id.reminderSwitch);
        Spinner reminderTimeSpinner = dialogView.findViewById(R.id.reminderTimeSpinner);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveButton);

        // Set up reminder spinner
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.reminder_times,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderTimeSpinner.setAdapter(spinnerAdapter);

        // Pre-fill with existing data
        titleInput.setText(schedule.getTitle());
        descriptionInput.setText(schedule.getDescription());

        // Set date
        final java.util.Calendar scheduleCalendar = java.util.Calendar.getInstance();
        if (schedule.getDate() != null) {
            scheduleCalendar.setTime(schedule.getDate().toDate());
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        selectedDateTextView.setText(dateFormat.format(scheduleCalendar.getTime()));

        // Set time
        final String[] selectedTime = {schedule.getTime()};
        if (selectedTime[0] != null && !selectedTime[0].isEmpty()) {
            selectedTimeTextView.setText(schedule.getFormattedTime());
            clearTimeButton.setVisibility(View.VISIBLE);
        }

        // Set category
        // Set category
        switch (schedule.getCategory()) {
            case "weekly":
                categoryRadioGroup.check(R.id.weeklyRadio);
                break;
            case "todo":
            case "event":     // Old data compatibility
            case "holiday":   // Holidays default to todo when editing
            default:
                categoryRadioGroup.check(R.id.todoRadio);
                break;
        }

        // Set reminder
        reminderSwitch.setChecked(schedule.hasReminder());
        if (schedule.hasReminder()) {
            reminderTimeSpinner.setVisibility(View.VISIBLE);
            reminderTimeSpinner.setSelection(getReminderSpinnerPosition(schedule.getReminderMinutes()));
        }

        // Date picker
        datePickerButton.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    getContext(),
                    (view, year, month, dayOfMonth) -> {
                        scheduleCalendar.set(year, month, dayOfMonth);
                        selectedDateTextView.setText(dateFormat.format(scheduleCalendar.getTime()));
                    },
                    scheduleCalendar.get(java.util.Calendar.YEAR),
                    scheduleCalendar.get(java.util.Calendar.MONTH),
                    scheduleCalendar.get(java.util.Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // Time picker
        timePickerButton.setOnClickListener(v -> {
            int hour = 12;
            int minute = 0;
            if (selectedTime[0] != null && !selectedTime[0].isEmpty()) {
                String[] timeParts = selectedTime[0].split(":");
                hour = Integer.parseInt(timeParts[0]);
                minute = Integer.parseInt(timeParts[1]);
            }

            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    getContext(),
                    (view, hourOfDay, minuteOfHour) -> {
                        selectedTime[0] = String.format(Locale.getDefault(),
                                "%02d:%02d", hourOfDay, minuteOfHour);
                        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                        try {
                            Date time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .parse(selectedTime[0]);
                            selectedTimeTextView.setText(timeFormat.format(time));
                            clearTimeButton.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            selectedTimeTextView.setText(selectedTime[0]);
                        }
                    },
                    hour,
                    minute,
                    false
            );
            timePickerDialog.show();
        });

        // Clear time button
        clearTimeButton.setOnClickListener(v -> {
            selectedTime[0] = null;
            selectedTimeTextView.setText("Select Time");
            clearTimeButton.setVisibility(View.GONE);
        });

        // Reminder switch
        reminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            reminderTimeSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Save button
        saveButton.setText("Update");
        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a title", Toast.LENGTH_SHORT).show();
                return;
            }

            String description = descriptionInput.getText().toString().trim();
            String category = getSelectedCategory(categoryRadioGroup);
            boolean hasReminder = reminderSwitch.isChecked();
            int reminderMinutes = getReminderMinutes(reminderTimeSpinner.getSelectedItemPosition());

            updateSchedule(schedule.getId(), title, description, scheduleCalendar,
                    selectedTime[0], category, hasReminder, reminderMinutes);
            dialog.dismiss();
        });

        dialog.show();
    }

    private int getReminderSpinnerPosition(int minutes) {
        switch (minutes) {
            case 5: return 0;
            case 10: return 1;
            case 15: return 2;
            case 30: return 3;
            case 60: return 4;
            case 1440: return 5;
            default: return 2; // 15 minutes default
        }
    }

    private void updateSchedule(String scheduleId, String title, String description,
            java.util.Calendar calendar, String time, String category,
    boolean hasReminder, int reminderMinutes) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", title);
        updates.put("description", description);
        updates.put("date", new Timestamp(calendar.getTime()));
        updates.put("time", time != null ? time : "");
        updates.put("category", category);
        updates.put("hasReminder", hasReminder);
        updates.put("reminderMinutes", reminderMinutes);

        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .document(scheduleId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "✓ Schedule updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to update schedule", Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteConfirmationDialog(Schedule schedule) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Schedule")
                .setMessage("Are you sure you want to delete \"" + schedule.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteSchedule(schedule))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSchedule(Schedule schedule) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .document(schedule.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "✓ Schedule deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to delete schedule", Toast.LENGTH_SHORT).show();
                });
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
}