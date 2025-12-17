package com.example.testtasksync;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

//FEATUES
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import android.graphics.Color;



public class WeeklyActivity extends AppCompatActivity {

    private boolean hasReminder = false;
    private int reminderMinutes = 60;
    private static final int NOTIFICATION_PERMISSION_CODE = 100;
    private static final String TAG = "WeeklyActivity";
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String planId;
    private boolean isNewPlan = false;

    // Week range fields
    private Calendar startDate = null;
    private Calendar endDate = null;

    private EditText weeklyTitle;
    private ImageView saveButton, backButton, scheduleButton;

    private List<String> days = Arrays.asList("Mon", "Tues", "Wed", "Thur", "Fri", "Sat", "Sun");
    private Map<String, RecyclerView> dayContainers = new HashMap<>();
    private Map<String, List<WeeklyTask>> dayTasks = new HashMap<>();
    private String selectedTime = "";
    private ListenerRegistration tasksListener;


    private Map<String, WeeklyTaskAdapter> dayAdapters = new HashMap<>();
    private Map<String, ItemTouchHelper> dayTouchHelpers = new HashMap<>();
    private View currentDraggedView = null;
    private String currentDraggedDay = null;


    // FEATURES
    private ImageButton headingsAndFont;
    private ImageButton addDividerBtn;
    private ImageButton insertImageBtn;
    private ImageButton addThemeBtn;
    private ImageButton addSubpageBtn;
    private View keyboardToolbar;
    private View colorPickerPanel;
    private LinearLayout mainLayout;
    private String currentBgColor = "#FAFAFA";

    // Image handling
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private Uri currentPhotoUri;

    private static final int MAX_IMAGE_WIDTH = 1024;
    private static final int MAX_IMAGE_HEIGHT = 1024;
    private static final int COMPRESSION_QUALITY = 80;

    private String titleFontStyle = "normal";
    private int titleFontSize = 16;
    private String titleFontColor = "#000000";
    private LinearLayout dueDateDisplay;
    private TextView dueDateText;
    private ImageView clearDateButton;
    private View draggedView = null;
    private WeeklyTask draggedTask = null;
    private String draggedFromDay = null;
    private int draggedFromPosition = -1;
    private boolean isDragging = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weekly);
        //TOP BAR COLOR THEME
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#8daaa6")); // Same as your top bar
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // ✅ FIX 1: Get planId FIRST
        Intent intent = getIntent();
        planId = intent.getStringExtra("planId");
        boolean fromNotification = intent.getBooleanExtra("fromNotification", false);
        isNewPlan = (planId == null || planId.isEmpty());

        Log.d(TAG, "🎯 WeeklyActivity opened:");
        Log.d(TAG, "   planId: " + planId);
        Log.d(TAG, "   isNewPlan: " + isNewPlan);
        Log.d(TAG, "   fromNotification: " + fromNotification);

        mainLayout = findViewById(R.id.mainLayout);

        // ✅ FIX 2: Set default FIRST
        currentBgColor = "#FAFAFA";
        mainLayout.setBackgroundColor(Color.parseColor(currentBgColor));

        // ✅ FIX 3: Load saved settings ONLY if not new plan
        if (!isNewPlan) {
            loadBackgroundColor();
            loadTitleFormatting();
        }

        weeklyTitle = findViewById(R.id.weeklyTitle);
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        scheduleButton = findViewById(R.id.scheduleButton);

        NotificationHelper.createNotificationChannel(this);
        requestNotificationPermission();

        // Set week range
        if (isNewPlan) {
            setCurrentWeek();
        }

        // Initialize day containers
        dayContainers.put("Mon", findViewById(R.id.monTasksContainer));
        dayContainers.put("Tues", findViewById(R.id.tuesTasksContainer));
        dayContainers.put("Wed", findViewById(R.id.wedTasksContainer));
        dayContainers.put("Thur", findViewById(R.id.thurTasksContainer));
        dayContainers.put("Fri", findViewById(R.id.friTasksContainer));
        dayContainers.put("Sat", findViewById(R.id.satTasksContainer));
        dayContainers.put("Sun", findViewById(R.id.sunTasksContainer));

        for (String day : days) {
            dayTasks.put(day, new ArrayList<>());
        }

        setupRecyclerViews();

        // Add task buttons
        findViewById(R.id.addMonTask).setOnClickListener(v -> addTask("Mon"));
        findViewById(R.id.addTuesTask).setOnClickListener(v -> addTask("Tues"));
        findViewById(R.id.addWedTask).setOnClickListener(v -> addTask("Wed"));
        findViewById(R.id.addThurTask).setOnClickListener(v -> addTask("Thur"));
        findViewById(R.id.addFriTask).setOnClickListener(v -> addTask("Fri"));
        findViewById(R.id.addSatTask).setOnClickListener(v -> addTask("Sat"));
        findViewById(R.id.addSunTask).setOnClickListener(v -> addTask("Sun"));

        saveButton.setOnClickListener(v -> saveWeeklyPlan());
        backButton.setOnClickListener(v -> saveWeeklyPlan());
        scheduleButton.setOnClickListener(v -> showScheduleDialog());

        // ✅ REMOVED: Image and divider setup
        dueDateDisplay = findViewById(R.id.dueDateDisplay);
        dueDateText = findViewById(R.id.dueDateText);
        clearDateButton = findViewById(R.id.clearDateButton);

        clearDateButton.setOnClickListener(v -> clearSchedule());


        if (isNewPlan) {
            Log.d(TAG, "📝 Creating new plan - adding default tasks");
            for (String day : days) {
                for (int i = 0; i < 3; i++) {
                    addTask(day);
                }
            }
        } else {
            Log.d(TAG, "📂 Loading existing plan: " + planId);
            loadWeeklyPlan();
        }
    }

    // ✅ FIXED setupRecyclerViews() method - Replace your existing method with this

    private void setupRecyclerViews() {
        for (String day : days) {
            RecyclerView recyclerView = dayContainers.get(day);
            List<WeeklyTask> tasks = dayTasks.get(day);

            WeeklyTaskAdapter adapter = new WeeklyTaskAdapter(day, tasks,
                    new WeeklyTaskAdapter.TaskActionListener() {
                        @Override
                        public void onTaskTextChanged(WeeklyTask task, String newText) {
                            // Auto-save functionality
                        }

                        @Override
                        public void onTaskCompletionChanged(WeeklyTask task, boolean isCompleted) {
                            if (!isNewPlan && task.getId() != null && !task.getId().isEmpty()) {
                                FirebaseUser user = auth.getCurrentUser();
                                if (user != null) {
                                    db.collection("users")
                                            .document(user.getUid())
                                            .collection("weeklyPlans")
                                            .document(planId)
                                            .collection("tasks")
                                            .document(task.getId())
                                            .update("isCompleted", isCompleted)
                                            .addOnSuccessListener(aVoid -> {
                                                if (isCompleted) {
                                                    NotificationHelper.cancelNotification(
                                                            WeeklyActivity.this, task.getId());
                                                }
                                            });
                                }
                            }
                        }

                        @Override
                        public void onDeleteClicked(WeeklyTask task, int position) {
                            List<WeeklyTask> dayTaskList = dayTasks.get(day);
                            if (dayTaskList != null && dayTaskList.size() > 1) {
                                dayTaskList.remove(position);
                                WeeklyTaskAdapter currentAdapter = dayAdapters.get(day);
                                if (currentAdapter != null) {
                                    currentAdapter.notifyItemRemoved(position);
                                }
                                updateTaskPositions(day);
                            } else {
                                Toast.makeText(WeeklyActivity.this,
                                        "Keep at least one task per day", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onTaskMoved(int fromPosition, int toPosition) {
                            // Save when order changes
                        }

                        @Override
                        public void onScheduleClicked(WeeklyTask task, int position) {
                            showTaskScheduleDialog(task, day);
                        }

                        @Override
                        public void onClearScheduleClicked(WeeklyTask task) {
                            task.setScheduleDate(null);
                            task.setScheduleTime(null);
                            task.setHasNotification(false);
                            task.setNotificationMinutes(60);

                            if (!isNewPlan && task.getId() != null && !task.getId().isEmpty()) {
                                NotificationHelper.cancelNotification(
                                        WeeklyActivity.this,
                                        planId + "_" + task.getId()
                                );
                            }

                            WeeklyTaskAdapter currentAdapter = dayAdapters.get(day);
                            if (currentAdapter != null) {
                                currentAdapter.notifyDataSetChanged();
                            }

                            if (!isNewPlan) {
                                saveTaskScheduleOnly(task);
                            }

                           // Toast.makeText(WeeklyActivity.this, "Task schedule cleared", Toast.LENGTH_SHORT).show();
                        }
                    });

            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
            recyclerView.setNestedScrollingEnabled(false);

            dayAdapters.put(day, adapter);

            // ✅ SIMPLE ItemTouchHelper for same-day reordering only
            ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder viewHolder,
                                      @NonNull RecyclerView.ViewHolder target) {

                    int fromPos = viewHolder.getAdapterPosition();
                    int toPos = target.getAdapterPosition();

                    WeeklyTaskAdapter currentAdapter = dayAdapters.get(day);
                    if (currentAdapter != null) {
                        currentAdapter.moveItem(fromPos, toPos);
                    }
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                    // No swipe
                }

                @Override
                public void clearView(@NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder viewHolder) {
                    super.clearView(recyclerView, viewHolder);
                    updateTaskPositions(day);
                }
            };

            ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(recyclerView);
            dayTouchHelpers.put(day, touchHelper);

            // ✅ MANUAL TOUCH LISTENER for cross-day dragging
            recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                private float startX = 0;
                private float startY = 0;
                private boolean isLongPress = false;
                private Runnable longPressRunnable = null;
                private View pressedChild = null;

                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {

                    if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        View child = rv.findChildViewUnder(e.getX(), e.getY());

                        if (child != null) {
                            startX = e.getRawX();
                            startY = e.getRawY();
                            isLongPress = false;
                            pressedChild = child;

                            // Start long press detection
                            longPressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    isLongPress = true;
                                    int position = rv.getChildAdapterPosition(child);
                                    if (position >= 0 && position < tasks.size()) {
                                        startCrossDayDrag(child, day, position);
                                    }
                                }
                            };

                            rv.postDelayed(longPressRunnable, 300); // 300ms
                        }
                    }

                    if (e.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                        // Cancel long press if moved too much before drag starts
                        if (!isDragging && Math.abs(e.getRawY() - startY) > 30 && longPressRunnable != null) {
                            rv.removeCallbacks(longPressRunnable);
                        }
                    }

                    if (e.getAction() == android.view.MotionEvent.ACTION_UP ||
                            e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                        if (longPressRunnable != null) {
                            rv.removeCallbacks(longPressRunnable);
                        }
                        pressedChild = null;
                    }

                    // Return true if dragging to intercept all touch events
                    return isDragging;
                }

                @Override
                public void onTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                    // Events are handled in dispatchTouchEvent
                }

                @Override
                public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                    // Allow parent to handle
                }
            });
        }
    }
    private void startCrossDayDrag(View view, String fromDay, int position) {
        List<WeeklyTask> tasks = dayTasks.get(fromDay);
        if (tasks == null || position >= tasks.size()) return;

        draggedView = view;
        draggedTask = tasks.get(position);
        draggedFromDay = fromDay;
        draggedFromPosition = position;
        isDragging = true;

        // Get task text from view
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof EditText) {
                    String currentText = ((EditText) child).getText().toString();
                    draggedTask.setTaskText(currentText);
                    break;
                }
            }
        }

        // Visual feedback - MORE PROMINENT
        view.setAlpha(0.7f);
        view.setScaleX(1.1f);
        view.setScaleY(1.1f);
        view.setElevation(8f);

        // Disable scroll
        androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView);
        if (scrollView != null) {
            scrollView.requestDisallowInterceptTouchEvent(true);
        }

        Log.d(TAG, "🎯 Started cross-day drag: " + draggedTask.getTaskText() + " from " + fromDay);
    }

    //Cross drag
    private void startUnifiedDrag(View view, String fromDay, int position) {
        List<WeeklyTask> tasks = dayTasks.get(fromDay);
        if (tasks == null || position >= tasks.size()) return;

        draggedView = view;
        draggedTask = tasks.get(position);
        draggedFromDay = fromDay;
        draggedFromPosition = position;
        isDragging = true;

        // Get task text from view
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof EditText) {
                    String currentText = ((EditText) child).getText().toString();
                    draggedTask.setTaskText(currentText);
                    break;
                }
            }
        }

        // Visual feedback
        view.setAlpha(0.7f);
        view.setScaleX(1.1f);
        view.setScaleY(1.1f);
        view.setElevation(8f);

        // Disable scroll
        androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView);
        if (scrollView != null) {
            scrollView.requestDisallowInterceptTouchEvent(true);
        }

        Log.d(TAG, "🎯 Started unified drag: " + draggedTask.getTaskText() + " from " + fromDay + " pos " + position);
    }
    private void handleUnifiedDrop(float rawY) {
        if (!isDragging || draggedTask == null || draggedFromDay == null) {
            return;
        }

        Log.d(TAG, "📍 Drop at Y: " + rawY);

        LinearLayout daysContainer = findViewById(R.id.daysContainer);
        String targetDay = null;
        int targetDayIndex = -1;

        // Find which day section the drop is in
        for (int i = 0; i < days.size(); i++) {
            String dayName = days.get(i);
            View daySection = daysContainer.getChildAt(i);

            if (daySection != null) {
                int[] location = new int[2];
                daySection.getLocationOnScreen(location);

                int sectionTop = location[1];
                int sectionBottom = location[1] + daySection.getHeight();

                if (rawY >= sectionTop && rawY <= sectionBottom) {
                    targetDay = dayName;
                    targetDayIndex = i;
                    Log.d(TAG, "🎯 Target day: " + targetDay);
                    break;
                }
            }
        }

        if (targetDay != null) {
            if (targetDay.equals(draggedFromDay)) {
                // ✅ SAME DAY - reorder within the day
                handleSameDayReorder(targetDay, rawY);
            } else {
                // ✅ DIFFERENT DAY - move to another day
                handleCrossDayMove(targetDay);
            }
        }

        // Reset drag state
        if (draggedView != null) {
            draggedView.setAlpha(1.0f);
            draggedView.setScaleX(1.0f);
            draggedView.setScaleY(1.0f);
            draggedView.setElevation(0f);
        }

        draggedView = null;
        draggedTask = null;
        draggedFromDay = null;
        draggedFromPosition = -1;
        isDragging = false;

        // Re-enable scroll
        androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView);
        if (scrollView != null) {
            scrollView.requestDisallowInterceptTouchEvent(false);
        }
    }
    private void handleSameDayReorder(String day, float dropY) {
        List<WeeklyTask> tasks = dayTasks.get(day);
        if (tasks == null) return;

        RecyclerView recyclerView = dayContainers.get(day);
        if (recyclerView == null) return;

        // Find which position to insert at based on Y coordinate
        int targetPosition = draggedFromPosition;

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            int[] location = new int[2];
            child.getLocationOnScreen(location);

            int childCenterY = location[1] + (child.getHeight() / 2);

            if (dropY < childCenterY) {
                targetPosition = recyclerView.getChildAdapterPosition(child);
                break;
            }
        }

        // Clamp position
        if (targetPosition < 0) targetPosition = 0;
        if (targetPosition >= tasks.size()) targetPosition = tasks.size() - 1;

        Log.d(TAG, "↕️ Reordering in " + day + " from " + draggedFromPosition + " to " + targetPosition);

        if (targetPosition != draggedFromPosition) {
            // Remove from old position
            tasks.remove(draggedFromPosition);

            // Insert at new position
            tasks.add(targetPosition, draggedTask);

            // Update adapter
            WeeklyTaskAdapter adapter = dayAdapters.get(day);
            if (adapter != null) {
                adapter.notifyItemMoved(draggedFromPosition, targetPosition);
            }

            // Update positions
            updateTaskPositions(day);
        }
    }
    private void handleCrossDayMove(String targetDay) {
        Log.d(TAG, "✅ Moving from " + draggedFromDay + " to " + targetDay);

        List<WeeklyTask> fromTasks = dayTasks.get(draggedFromDay);
        List<WeeklyTask> toTasks = dayTasks.get(targetDay);

        if (fromTasks == null || toTasks == null) return;

        // Remove from source
        fromTasks.remove(draggedFromPosition);

        WeeklyTaskAdapter fromAdapter = dayAdapters.get(draggedFromDay);
        if (fromAdapter != null) {
            fromAdapter.notifyItemRemoved(draggedFromPosition);
        }

        // Update task day
        draggedTask.setDay(targetDay);

        // Add to target
        toTasks.add(draggedTask);
        draggedTask.setPosition(toTasks.size() - 1);

        WeeklyTaskAdapter toAdapter = dayAdapters.get(targetDay);
        if (toAdapter != null) {
            toAdapter.notifyItemInserted(toTasks.size() - 1);
        }

        // Update positions
        updateTaskPositions(draggedFromDay);
        updateTaskPositions(targetDay);

        Toast.makeText(this, "→ " + targetDay, Toast.LENGTH_SHORT).show();
    }


    private void handleCrossDayDrop(float rawY) {
        if (!isDragging || draggedTask == null || draggedFromDay == null) {
            return;
        }

        Log.d(TAG, "📍 Drop at Y: " + rawY);

        LinearLayout daysContainer = findViewById(R.id.daysContainer);
        String targetDay = null;

        // Find which day section the drop is in
        for (int i = 0; i < days.size(); i++) {
            String dayName = days.get(i);
            View daySection = daysContainer.getChildAt(i);

            if (daySection != null) {
                int[] location = new int[2];
                daySection.getLocationOnScreen(location);

                int sectionTop = location[1];
                int sectionBottom = location[1] + daySection.getHeight();

                if (rawY >= sectionTop && rawY <= sectionBottom) {
                    targetDay = dayName;
                    Log.d(TAG, "🎯 Target day: " + targetDay);
                    break;
                }
            }
        }

        // Move task if dropped on different day
        if (targetDay != null && !targetDay.equals(draggedFromDay)) {
            Log.d(TAG, "✅ Moving from " + draggedFromDay + " to " + targetDay);

            List<WeeklyTask> fromTasks = dayTasks.get(draggedFromDay);
            int currentPosition = fromTasks.indexOf(draggedTask);

            if (currentPosition >= 0) {
                // Remove from source
                fromTasks.remove(currentPosition);

                WeeklyTaskAdapter fromAdapter = dayAdapters.get(draggedFromDay);
                if (fromAdapter != null) {
                    fromAdapter.notifyItemRemoved(currentPosition);
                }

                // Update task day
                draggedTask.setDay(targetDay);

                // Add to target
                List<WeeklyTask> toTasks = dayTasks.get(targetDay);
                toTasks.add(draggedTask);
                draggedTask.setPosition(toTasks.size() - 1);

                WeeklyTaskAdapter toAdapter = dayAdapters.get(targetDay);
                if (toAdapter != null) {
                    toAdapter.notifyItemInserted(toTasks.size() - 1);
                }

                // Update positions
                updateTaskPositions(draggedFromDay);
                updateTaskPositions(targetDay);

                // ✅ SHORT TOAST at the END (doesn't interrupt)
                Toast.makeText(this, "→ " + targetDay, Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d(TAG, "⚪ Stayed in same day");
        }

        // Reset drag state
        if (draggedView != null) {
            draggedView.setAlpha(1.0f);
            draggedView.setScaleX(1.0f);
            draggedView.setScaleY(1.0f);
            draggedView.setElevation(0f);
        }

        draggedView = null;
        draggedTask = null;
        draggedFromDay = null;
        draggedFromPosition = -1;
        isDragging = false;

        // Re-enable scroll
        androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView);
        if (scrollView != null) {
            scrollView.requestDisallowInterceptTouchEvent(false);
        }
    }
    private void showTaskScheduleDialog(WeeklyTask task, String taskDay) {
        // ✅ FIX: Check if week range exists FIRST before opening dialog
        if (startDate == null || endDate == null) {
            // Week range not set - show options
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Week Range Not Set")
                    .setMessage("Please set a week range first before scheduling tasks.")
                    .setPositiveButton("Set Current Week", (d, w) -> {
                        setCurrentWeek();
                        updateScheduleDisplay();
                       // Toast.makeText(this, "Week range set to current week", Toast.LENGTH_SHORT).show();
                        d.dismiss();
                        // Open the task schedule dialog again after setting week range
                        showTaskScheduleDialog(task, taskDay);
                    })
                    .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                    .show();
            return; // Exit early
        }

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_schedule);
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        // Get dialog views
        LinearLayout datePickerButton = dialog.findViewById(R.id.datePickerButton);
        TextView selectedDateText = dialog.findViewById(R.id.selectedDateText);
        LinearLayout timePickerButton = dialog.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = dialog.findViewById(R.id.selectedTimeText);
        CheckBox notificationCheckbox = dialog.findViewById(R.id.notificationCheckbox);
        LinearLayout notificationTimeSection = dialog.findViewById(R.id.notificationTimeSection);
        android.widget.Spinner notificationTimeSpinner = dialog.findViewById(R.id.notificationTimeSpinner);
        Button cancelButton = dialog.findViewById(R.id.cancelButton);
        Button saveScheduleButton = dialog.findViewById(R.id.saveScheduleButton);

        // ✅ DISABLE date picker button (date is auto-set based on day)
        datePickerButton.setEnabled(false);
        datePickerButton.setAlpha(0.5f);

        // Setup notification spinner
        String[] notificationTimes = {"5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "1 hour", "2 hours", "1 day"};
        int[] notificationMinutes = {5, 10, 15, 30, 60, 120, 1440};

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, notificationTimes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTimeSpinner.setAdapter(adapter);

        // ✅ AUTO-SET DATE: Now safe because we checked startDate != null above
        Calendar taskScheduleDate = getDateForDayInWeek(taskDay, startDate);

        if (taskScheduleDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
            selectedDateText.setText(sdf.format(taskScheduleDate.getTime()));
        } else {
            selectedDateText.setText("Week range not set");
        }

        // Load existing values if task already has schedule
        if (task.getScheduleTime() != null && !task.getScheduleTime().isEmpty()) {
            selectedTimeText.setText(task.getScheduleTime());
        }

        notificationCheckbox.setChecked(task.hasNotification());
        notificationTimeSection.setVisibility(task.hasNotification() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < notificationMinutes.length; i++) {
            if (notificationMinutes[i] == task.getNotificationMinutes()) {
                notificationTimeSpinner.setSelection(i);
                break;
            }
        }

        // Time picker
        timePickerButton.setOnClickListener(v -> {
            int hour = 9;
            int minute = 0;

            if (task.getScheduleTime() != null && !task.getScheduleTime().isEmpty()) {
                try {
                    String[] timeParts = task.getScheduleTime().split(":");
                    hour = Integer.parseInt(timeParts[0]);
                    minute = Integer.parseInt(timeParts[1]);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error parsing time", e);
                }
            }

            android.app.TimePickerDialog timePicker = new android.app.TimePickerDialog(
                    this,
                    (view, hourOfDay, minuteOfHour) -> {
                        String timeStr = String.format(Locale.getDefault(), "%02d:%02d",
                                hourOfDay, minuteOfHour);
                        selectedTimeText.setText(timeStr);
                    },
                    hour, minute, false
            );
            timePicker.show();
        });

        // Notification checkbox listener
        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Save button
        // Find this code in showScheduleDialog() and replace the saveScheduleButton.setOnClickListener:


        saveScheduleButton.setOnClickListener(v -> {
            if (taskScheduleDate == null) {
                Toast.makeText(this, "Please set week range first", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedTimeText.getText().equals("Select time")) {
                Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ Save schedule to task
            task.setScheduleDate(taskScheduleDate.getTime());
            task.setScheduleTime(selectedTimeText.getText().toString());
            task.setHasNotification(notificationCheckbox.isChecked());

            if (notificationCheckbox.isChecked()) {
                int selectedPos = notificationTimeSpinner.getSelectedItemPosition();
                task.setNotificationMinutes(notificationMinutes[selectedPos]);
            }

            // Update adapter (this updates the task's GREEN schedule display BELOW the task)
            WeeklyTaskAdapter currentAdapter = dayAdapters.get(taskDay);
            if (currentAdapter != null) {
                currentAdapter.notifyDataSetChanged();
            }

            // ✅ Save to Firebase WITHOUT exiting, and DON'T update top bar
            if (!isNewPlan) {
                saveTaskScheduleOnly(task);
            }

            // ✅ Show confirmation
            String message = "Schedule set for " + taskDay;
            if (notificationCheckbox.isChecked()) {
                message += " (Notification enabled)";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            dialog.dismiss();
        });

        dialog.show();
    }
    private void saveTaskScheduleOnly(WeeklyTask task) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || task.getId() == null || task.getId().isEmpty()) {
            return;
        }

        Map<String, Object> taskData = new HashMap<>();

        if (task.getScheduleDate() != null) {
            taskData.put("scheduleDate", new com.google.firebase.Timestamp(task.getScheduleDate()));
        } else {
            taskData.put("scheduleDate", null);
        }

        taskData.put("scheduleTime", task.getScheduleTime());
        taskData.put("hasNotification", task.hasNotification());
        taskData.put("notificationMinutes", task.getNotificationMinutes());

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .document(task.getId())
                .update(taskData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Task schedule saved");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save task schedule", e);
                });
    }

    // ✅ NEW: Method to move task between days
    private void moveTaskToAnotherDay(WeeklyTask task, String fromDay, String toDay, int fromPosition) {
        List<WeeklyTask> fromTasks = dayTasks.get(fromDay);
        List<WeeklyTask> toTasks = dayTasks.get(toDay);

        Log.d(TAG, "🔄 Moving: " + task.getTaskText());
        Log.d(TAG, "   From: " + fromDay + " (pos " + fromPosition + ")");
        Log.d(TAG, "   To: " + toDay);

        // Remove from source
        if (fromTasks != null && fromPosition >= 0 && fromPosition < fromTasks.size()) {
            fromTasks.remove(fromPosition);

            WeeklyTaskAdapter fromAdapter = dayAdapters.get(fromDay);
            if (fromAdapter != null) {
                fromAdapter.notifyItemRemoved(fromPosition);
                fromAdapter.notifyItemRangeChanged(fromPosition, fromTasks.size());
            }
        } else {
            Log.e(TAG, "❌ Remove failed");
            return;
        }

        // Update task
        task.setDay(toDay);

        // Add to target
        if (toTasks != null) {
            toTasks.add(task);
            task.setPosition(toTasks.size() - 1);

            WeeklyTaskAdapter toAdapter = dayAdapters.get(toDay);
            if (toAdapter != null) {
                toAdapter.notifyItemInserted(toTasks.size() - 1);
            }
        }

        // Update positions
        updateTaskPositions(fromDay);
        updateTaskPositions(toDay);

        Log.d(TAG, "✅ Move complete");
        Toast.makeText(this, "→ " + toDay, Toast.LENGTH_SHORT).show();
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    private void loadWeeklyPlan() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String userId = user.getUid();

        // ✅ FIX: Load BOTH schedule AND plan data to get the time
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .whereEqualTo("sourceId", planId)
                .whereEqualTo("category", "weekly")
                .get()
                .addOnSuccessListener(scheduleSnapshots -> {
                    if (!scheduleSnapshots.isEmpty()) {
                        DocumentSnapshot scheduleDoc = scheduleSnapshots.getDocuments().get(0);

                        // ✅ CRITICAL: Load time from schedule
                        String savedTime = scheduleDoc.getString("time");
                        if (savedTime != null && !savedTime.isEmpty()) {
                            selectedTime = savedTime;
                            Log.d(TAG, "✅ Loaded time from schedule: " + selectedTime);
                        }

                        // Load reminder settings
                        Boolean hasReminderFromSchedule = scheduleDoc.getBoolean("hasReminder");
                        Long reminderMinutesFromSchedule = scheduleDoc.getLong("reminderMinutes");

                        if (hasReminderFromSchedule != null) {
                            hasReminder = hasReminderFromSchedule;
                        }
                        if (reminderMinutesFromSchedule != null) {
                            reminderMinutes = reminderMinutesFromSchedule.intValue();
                        }

                        // Try to get date range from schedule
                        Timestamp dateTimestamp = scheduleDoc.getTimestamp("date");
                        if (dateTimestamp != null && startDate == null) {
                            startDate = Calendar.getInstance();
                            startDate.setTime(dateTimestamp.toDate());
                        }
                    }

                    // Load plan details
                    loadWeeklyPlanDetails(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load schedule", e);
                    loadWeeklyPlanDetails(userId);
                });
    }

    // ✅ NEW: Separate method to load plan details
    private void loadWeeklyPlanDetails(String userId) {
        db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        if (title != null && !title.isEmpty()) {
                            weeklyTitle.setText(title);
                        }

                        if (selectedTime == null || selectedTime.isEmpty()) {
                            String savedTime = documentSnapshot.getString("time");
                            if (savedTime != null && !savedTime.isEmpty()) {
                                selectedTime = savedTime;
                            }
                        }

                        // ✅ Load week range from Firebase
                        Timestamp startDateTimestamp = documentSnapshot.getTimestamp("startDate");
                        Timestamp endDateTimestamp = documentSnapshot.getTimestamp("endDate");

                        if (startDateTimestamp != null && endDateTimestamp != null) {
                            startDate = Calendar.getInstance();
                            startDate.setTime(startDateTimestamp.toDate());
                            startDate.set(Calendar.HOUR_OF_DAY, 0);
                            startDate.set(Calendar.MINUTE, 0);
                            startDate.set(Calendar.SECOND, 0);

                            endDate = Calendar.getInstance();
                            endDate.setTime(endDateTimestamp.toDate());
                            endDate.set(Calendar.HOUR_OF_DAY, 23);
                            endDate.set(Calendar.MINUTE, 59);
                            endDate.set(Calendar.SECOND, 59);

                            Log.d(TAG, "✅ Loaded week range: " +
                                    new SimpleDateFormat("MMM dd", Locale.getDefault()).format(startDate.getTime()) +
                                    " - " + new SimpleDateFormat("MMM dd", Locale.getDefault()).format(endDate.getTime()));
                        } else {
                            // ✅ FIXED: Don't auto-set current week - leave as null if cleared
                            Log.d(TAG, "⚠️ No saved dates - schedule is cleared");
                            startDate = null;
                            endDate = null;
                        }

                        // ✅ NEW: Update schedule display after loading
                        updateScheduleDisplay();

                        // Load notification settings
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .whereEqualTo("sourceId", planId)
                                .whereEqualTo("category", "weekly")
                                .get()
                                .addOnSuccessListener(scheduleSnapshots -> {
                                    if (!scheduleSnapshots.isEmpty()) {
                                        DocumentSnapshot scheduleDoc = scheduleSnapshots.getDocuments().get(0);
                                        Boolean hasReminderFromSchedule = scheduleDoc.getBoolean("hasReminder");
                                        Long reminderMinutesFromSchedule = scheduleDoc.getLong("reminderMinutes");

                                        if (hasReminderFromSchedule != null) {
                                            hasReminder = hasReminderFromSchedule;
                                        }
                                        if (reminderMinutesFromSchedule != null) {
                                            reminderMinutes = reminderMinutesFromSchedule.intValue();
                                        }

                                        // ✅ Update display again with reminder info
                                        updateScheduleDisplay();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to load schedule notification settings", e);
                                });
                    }

                    loadWeeklyPlanTasks(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load weekly plan", e);
                    Toast.makeText(this, "Failed to load plan", Toast.LENGTH_SHORT).show();
                });
    }

    // ✅ NEW: Separate method to load tasks
    private void loadWeeklyPlanTasks(String userId) {
        db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .orderBy("position")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (String day : days) {
                        dayTasks.get(day).clear();
                    }

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        WeeklyTask task = new WeeklyTask();
                        task.setId(doc.getId());
                        task.setDay(doc.getString("day"));
                        task.setTaskText(doc.getString("taskText"));
                        task.setCompleted(Boolean.TRUE.equals(doc.getBoolean("isCompleted")));
                        task.setPosition(doc.getLong("position").intValue());

                        // ✅ Load task-level schedule
                        com.google.firebase.Timestamp scheduleTimestamp = doc.getTimestamp("scheduleDate");
                        if (scheduleTimestamp != null) {
                            task.setScheduleDate(scheduleTimestamp.toDate());
                        }
                        task.setScheduleTime(doc.getString("scheduleTime"));
                        task.setHasNotification(Boolean.TRUE.equals(doc.getBoolean("hasNotification")));
                        Long notifMinutes = doc.getLong("notificationMinutes");
                        if (notifMinutes != null) {
                            task.setNotificationMinutes(notifMinutes.intValue());
                        }

                        List<WeeklyTask> tasks = dayTasks.get(task.getDay());
                        if (tasks != null) {
                            tasks.add(task);
                        }
                    }

                    for (String day : days) {
                        List<WeeklyTask> tasks = dayTasks.get(day);
                        if (tasks.isEmpty()) {
                            for (int i = 0; i < 3; i++) {
                                addTask(day);
                            }
                        } else {
                            WeeklyTaskAdapter adapter = dayAdapters.get(day);
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }

                    loadSubpages();
                    setupTasksRealtimeListener(userId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(TAG, "Failed to load tasks", e);
                    for (String day : days) {
                        for (int i = 0; i < 3; i++) {
                            addTask(day);
                        }
                    }
                });
    }
    private void addTask(String day) {
        WeeklyTask task = new WeeklyTask();
        task.setId("");
        task.setDay(day);
        task.setTaskText("");
        task.setCompleted(false);
        task.setPosition(dayTasks.get(day).size());

        dayTasks.get(day).add(task);

        WeeklyTaskAdapter adapter = dayAdapters.get(day);
        if (adapter != null) {
            adapter.notifyItemInserted(dayTasks.get(day).size() - 1);
        }
    }

    private void updateTaskPositions(String day) {
        List<WeeklyTask> tasks = dayTasks.get(day);
        if (tasks != null) {
            for (int i = 0; i < tasks.size(); i++) {
                tasks.get(i).setPosition(i);
            }
        }
    }

    private void saveWeeklyPlan() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = weeklyTitle.getText().toString().trim();
        if (title.isEmpty()) {
            title = "Weekly Plan";
        }

        int totalTasks = 0;
        int completedTasks = 0;
        for (String day : days) {
            List<WeeklyTask> tasks = dayTasks.get(day);
            if (tasks != null) {
                for (WeeklyTask task : tasks) {
                    if (!task.getTaskText().trim().isEmpty()) {
                        totalTasks++;
                        if (task.isCompleted()) {
                            completedTasks++;
                        }
                    }
                }
            }
        }

        Map<String, Object> mainScheduleData = new HashMap<>();
        mainScheduleData.put("title", title);

        String description = totalTasks + " tasks";
        if (startDate != null && endDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            description += " (" + sdf.format(startDate.getTime()) + " - " +
                    sdf.format(endDate.getTime()) + ")";
        }

        if (completedTasks > 0) {
            description += " • " + completedTasks + " completed";
        }

        mainScheduleData.put("description", description);
        mainScheduleData.put("category", "weekly");
        mainScheduleData.put("isCompleted", completedTasks == totalTasks && totalTasks > 0);
        mainScheduleData.put("createdAt", new Timestamp(new Date()));
        mainScheduleData.put("createdAtServer", com.google.firebase.firestore.FieldValue.serverTimestamp());
        if (startDate != null) {
            mainScheduleData.put("date", new Timestamp(startDate.getTime()));
        } else {
            mainScheduleData.put("date", null);
        }

        mainScheduleData.put("time", selectedTime != null ? selectedTime : "");
        mainScheduleData.put("hasReminder", hasReminder);
        mainScheduleData.put("reminderMinutes", reminderMinutes);

        if (isNewPlan) {
            planId = db.collection("users")
                    .document(user.getUid())
                    .collection("weeklyPlans")
                    .document().getId();
        }

        final String finalTitle = title;
        final String finalPlanId = planId;
        final int finalTotalTasks = totalTasks;
        final int finalCompletedTasks = completedTasks;

        Map<String, Object> planData = new HashMap<>();
        planData.put("title", finalTitle);
        planData.put("timestamp", new Timestamp(new Date()));
        planData.put("timestampServer", com.google.firebase.firestore.FieldValue.serverTimestamp());
        planData.put("taskCount", finalTotalTasks);
        planData.put("completedCount", finalCompletedTasks);
        planData.put("time", selectedTime != null ? selectedTime : "");

        if (startDate != null && endDate != null) {
            planData.put("startDate", new Timestamp(startDate.getTime()));
            planData.put("endDate", new Timestamp(endDate.getTime()));
        } else {
            planData.put("startDate", null);
            planData.put("endDate", null);
        }

        // ✅ START SAVE OPERATIONS (will queue offline)
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(finalPlanId)
                .set(planData);

        // Save tasks in background
        saveTasks(user.getUid(), finalPlanId, finalTitle, finalTotalTasks,
                finalCompletedTasks, mainScheduleData);

        // ✅ IMMEDIATELY SHOW TOAST AND FINISH
      //  Toast.makeText(this, "Saving weekly plan...", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void saveTasks(String userId, String planId, String planTitle, int totalTasks,
                           int completedTasks, Map<String, Object> mainScheduleData) {
        db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Cancel old notifications
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String oldTaskId = doc.getId();
                        NotificationHelper.cancelNotification(this, planId + "_" + oldTaskId);
                        doc.getReference().delete();
                    }

                    int tasksToSave = 0;
                    for (String day : days) {
                        List<WeeklyTask> tasks = dayTasks.get(day);
                        if (tasks != null) {
                            for (WeeklyTask task : tasks) {
                                if (!task.getTaskText().trim().isEmpty()) {
                                    tasksToSave++;
                                }
                            }
                        }
                    }

                    if (tasksToSave == 0) {
                        createOrUpdateMainSchedule(userId, planId, mainScheduleData);
                        return;
                    }

                    final int[] savedCount = {0};
                    for (String day : days) {
                        List<WeeklyTask> tasks = dayTasks.get(day);
                        if (tasks != null) {
                            for (WeeklyTask task : tasks) {
                                if (!task.getTaskText().trim().isEmpty()) {
                                    Map<String, Object> taskData = new HashMap<>();
                                    taskData.put("day", task.getDay());
                                    taskData.put("taskText", task.getTaskText());
                                    taskData.put("isCompleted", task.isCompleted());
                                    taskData.put("position", task.getPosition());

                                    if (task.getScheduleDate() != null) {
                                        taskData.put("scheduleDate", new com.google.firebase.Timestamp(task.getScheduleDate()));
                                    }
                                    taskData.put("scheduleTime", task.getScheduleTime());
                                    taskData.put("hasNotification", task.hasNotification());
                                    taskData.put("notificationMinutes", task.getNotificationMinutes());

                                    int finalTasksToSave = tasksToSave;
                                    db.collection("users")
                                            .document(userId)
                                            .collection("weeklyPlans")
                                            .document(planId)
                                            .collection("tasks")
                                            .add(taskData)
                                            .addOnSuccessListener(documentReference -> {
                                                String taskId = documentReference.getId();
                                                task.setId(taskId);

                                                if (task.hasNotification() &&
                                                        !task.isCompleted() &&
                                                        task.getScheduleDate() != null) {

                                                    NotificationHelper.scheduleWeeklyTaskNotification(
                                                            this,
                                                            planId + "_" + taskId,
                                                            planTitle,
                                                            task.getTaskText(),
                                                            task.getDay(),
                                                            task.getScheduleDate(),
                                                            task.getScheduleTime(),
                                                            task.getNotificationMinutes()
                                                    );
                                                }

                                                savedCount[0]++;
                                                if (savedCount[0] == finalTasksToSave) {
                                                    createOrUpdateMainSchedule(userId, planId, mainScheduleData);
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                android.util.Log.e(TAG, "Failed to save task", e);
                                            });
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(TAG, "Failed to delete old tasks", e);
                });
    }
    private void createOrUpdateMainSchedule(String userId, String planId,
                                            Map<String, Object> mainScheduleData) {
        mainScheduleData.put("sourceId", planId);

        db.collection("users")
                .document(userId)
                .collection("schedules")
                .whereEqualTo("sourceId", planId)
                .whereEqualTo("category", "weekly")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String scheduleId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .document(scheduleId)
                                .update(mainScheduleData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Main schedule updated for weekly plan");
                                    if (hasReminder) {
                                        scheduleWeeklyPlanNotification();
                                    }
                                    // ✅ REMOVED finish() - already finished in saveWeeklyPlan()
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update schedule", e);
                                    // ✅ REMOVED finish()
                                });
                    } else {
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .add(mainScheduleData)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d(TAG, "Main schedule created for weekly plan");
                                    if (hasReminder) {
                                        scheduleWeeklyPlanNotification();
                                    }
                                    // ✅ REMOVED finish()
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create schedule", e);
                                    // ✅ REMOVED finish()
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check existing schedule", e);
                    // ✅ REMOVED finish()
                });
    }

    private void setCurrentWeek() {
        Calendar calendar = Calendar.getInstance();
        int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        // Calculate start of current week (MONDAY)
        int daysToMonday = (currentDayOfWeek == Calendar.SUNDAY) ? 6 : currentDayOfWeek - Calendar.MONDAY;
        calendar.add(Calendar.DAY_OF_MONTH, -daysToMonday);

        startDate = (Calendar) calendar.clone();
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);

        // Calculate end of week (SUNDAY) - 6 days from Monday
        calendar.add(Calendar.DAY_OF_MONTH, 6);
        endDate = (Calendar) calendar.clone();
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);

        Log.d(TAG, "✅ Set current week: " +
                new SimpleDateFormat("MMM dd", Locale.getDefault()).format(startDate.getTime()) +
                " - " + new SimpleDateFormat("MMM dd", Locale.getDefault()).format(endDate.getTime()));
    }
    // ✅ NEW: Helper class to store view data
    private static class DayScheduleViewData {
        Calendar selectedDate;
        String selectedTime;
    }
    // ✅ NEW: Update schedule numbers after removal
    private void updateScheduleNumbers(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View scheduleView = container.getChildAt(i);
            TextView scheduleNumberText = scheduleView.findViewById(R.id.scheduleNumberText);
            scheduleNumberText.setText("Schedule " + (i + 1));
        }
    }

    // ✅ ADD helper method to get date for specific day in week
    private Calendar getDateForDayInWeek(String day, Calendar weekStart) {
        int targetDayOfWeek = getDayOfWeekFromName(day);
        if (targetDayOfWeek == -1) return null;

        Calendar result = (Calendar) weekStart.clone();

        // Move to the target day
        while (result.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek) {
            result.add(Calendar.DAY_OF_MONTH, 1);
        }

        return result;
    }

    // ✅ ADD helper method to get full day name
    private String getFullDayName(String shortDay) {
        switch (shortDay) {
            case "MONDAY": return "Monday";
            case "TUESDAY": return "Tuesday";
            case "WEDNESDAY": return "Wednesday";
            case "THURSDAY": return "Thursday";
            case "FRIDAY": return "Friday";
            case "SATURDAY": return "Saturday";
            case "SUNDAY": return "Sunday";
            default: return shortDay;
        }
    }

    // ✅ KEEP the existing getDayOfWeekFromName method but ensure it exists:
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

    // SCHEDULE DIALOG METHODS

    private void showScheduleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_weekly_schedule, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        LinearLayout weekRangePickerButton = dialogView.findViewById(R.id.weekRangePickerButton);
        TextView weekRangeText = dialogView.findViewById(R.id.weekRangeText);
        ImageView clearWeekButton = dialogView.findViewById(R.id.clearWeekButton);

        LinearLayout timePickerButton = dialogView.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = dialogView.findViewById(R.id.selectedTimeText);

        CheckBox notificationCheckbox = dialogView.findViewById(R.id.notificationCheckbox);
        LinearLayout notificationTimeSection = dialogView.findViewById(R.id.notificationTimeSection);
        android.widget.Spinner notificationTimeSpinner = dialogView.findViewById(R.id.notificationTimeSpinner);

        android.widget.Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        android.widget.Button saveScheduleButton = dialogView.findViewById(R.id.saveScheduleButton);

        String[] notificationTimes = {"5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "1 hour", "2 hours", "1 day"};
        int[] notificationMinutes = {5, 10, 15, 30, 60, 120, 1440};

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, notificationTimes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTimeSpinner.setAdapter(adapter);

        if (startDate == null || endDate == null) {
            setCurrentWeek();
        }
        updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);

        notificationCheckbox.setChecked(hasReminder);
        notificationTimeSection.setVisibility(hasReminder ? View.VISIBLE : View.GONE);

        for (int i = 0; i < notificationMinutes.length; i++) {
            if (notificationMinutes[i] == reminderMinutes) {
                notificationTimeSpinner.setSelection(i);
                break;
            }
        }

        if (selectedTime != null && !selectedTime.isEmpty()) {
            selectedTimeText.setText(selectedTime);
        }

        weekRangePickerButton.setOnClickListener(v ->
                showQuickWeekSelectorDialog(weekRangeText, clearWeekButton)
        );

        clearWeekButton.setOnClickListener(v -> {
            setCurrentWeek();
            updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
        });

        timePickerButton.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            android.app.TimePickerDialog timeDialog = new android.app.TimePickerDialog(
                    this,
                    (view, hourOfDay, minuteOfHour) -> {
                        String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
                        selectedTimeText.setText(time);
                    },
                    hour, minute, false
            );
            timeDialog.show();
        });

        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // ✅ MODIFIED: Save button now schedules notification
        saveScheduleButton.setOnClickListener(v -> {
            String selectedTimeValue = selectedTimeText.getText().toString();

            if (selectedTimeValue.equals("Select time")) {
                Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show();
                return;
            }

            // Cancel any existing notification
            NotificationHelper.cancelNotification(this, "weekly_" + planId);

            hasReminder = notificationCheckbox.isChecked();
            if (hasReminder) {
                int selectedPos = notificationTimeSpinner.getSelectedItemPosition();
                reminderMinutes = notificationMinutes[selectedPos];
            }

            selectedTime = selectedTimeValue;

            // ✅ Update BLUE top bar display
            updateScheduleDisplay();

            // Schedule notification if enabled
            if (hasReminder) {
                scheduleWeeklyPlanNotification();
            }

            // ✅ Save WITHOUT exiting
            if (!isNewPlan) {
                saveWeekScheduleOnly(); // Use new method instead of saveWeeklyPlan()
            }

            String message = "Schedule set for " +
                    new SimpleDateFormat("MMM dd", Locale.getDefault()).format(startDate.getTime()) +
                    " - " + new SimpleDateFormat("MMM dd", Locale.getDefault()).format(endDate.getTime()) +
                    " at " + selectedTime;

            if (hasReminder) {
                message += "\n✓ Reminder: " + notificationTimes[notificationTimeSpinner.getSelectedItemPosition()] + " before";
            }

            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            dialog.dismiss();

        });

        dialog.show();
    }
    private void showQuickWeekSelectorDialog(TextView weekRangeText, ImageView clearWeekButton) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Week");

        // Calculate week ranges starting from MONDAY
        Calendar calendar = Calendar.getInstance();
        int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysToMonday = (currentDayOfWeek == Calendar.SUNDAY) ? 6 : currentDayOfWeek - Calendar.MONDAY;

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());

        // This week (Monday to Sunday)
        Calendar thisWeekStart = (Calendar) calendar.clone();
        thisWeekStart.add(Calendar.DAY_OF_MONTH, -daysToMonday);
        Calendar thisWeekEnd = (Calendar) thisWeekStart.clone();
        thisWeekEnd.add(Calendar.DAY_OF_MONTH, 6); // ✅ Monday + 6 = Sunday

        // Next week (Monday to Sunday)
        Calendar nextWeekStart = (Calendar) thisWeekStart.clone();
        nextWeekStart.add(Calendar.DAY_OF_MONTH, 7);
        Calendar nextWeekEnd = (Calendar) nextWeekStart.clone();
        nextWeekEnd.add(Calendar.DAY_OF_MONTH, 6); // ✅ Monday + 6 = Sunday

        // Week after next (Monday to Sunday)
        Calendar afterNextStart = (Calendar) thisWeekStart.clone();
        afterNextStart.add(Calendar.DAY_OF_MONTH, 14);
        Calendar afterNextEnd = (Calendar) afterNextStart.clone();
        afterNextEnd.add(Calendar.DAY_OF_MONTH, 6); // ✅ Monday + 6 = Sunday

        String[] options = {
                "This Week (" + sdf.format(thisWeekStart.getTime()) + " - " + sdf.format(thisWeekEnd.getTime()) + ")",
                "Next Week (" + sdf.format(nextWeekStart.getTime()) + " - " + sdf.format(nextWeekEnd.getTime()) + ")",
                "Week After Next (" + sdf.format(afterNextStart.getTime()) + " - " + sdf.format(afterNextEnd.getTime()) + ")",
                "Custom Date Range..."
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // This Week
                    setWeekFromStartDate(thisWeekStart);
                    updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
                    break;

                case 1: // Next Week
                    setWeekFromStartDate(nextWeekStart);
                    updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
                    break;

                case 2: // Week After Next
                    setWeekFromStartDate(afterNextStart);
                    updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
                    break;

                case 3: // Custom
                    showCustomWeekPickerDialog(weekRangeText, clearWeekButton);
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void setWeekFromStartDate(Calendar start) {
        startDate = (Calendar) start.clone();
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);

        endDate = (Calendar) start.clone();
        endDate.add(Calendar.DAY_OF_MONTH, 6); // Monday + 6 = Sunday
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);
    }

    private void showCustomWeekPickerDialog(TextView weekRangeText, ImageView clearWeekButton) {
        Calendar initialDate = startDate != null ? startDate : Calendar.getInstance();

        DatePickerDialog startDatePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    startDate = Calendar.getInstance();
                    startDate.set(year, month, dayOfMonth);
                    showEndDatePickerDialog(weekRangeText, clearWeekButton);
                },
                initialDate.get(Calendar.YEAR),
                initialDate.get(Calendar.MONTH),
                initialDate.get(Calendar.DAY_OF_MONTH)
        );

        startDatePicker.setTitle("Select START date");
        startDatePicker.show();
    }

    private void showEndDatePickerDialog(TextView weekRangeText, ImageView clearWeekButton) {
        Calendar defaultEnd = (Calendar) startDate.clone();
        defaultEnd.add(Calendar.DAY_OF_MONTH, 6);

        DatePickerDialog endDatePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    endDate = Calendar.getInstance();
                    endDate.set(year, month, dayOfMonth);

                    // Validate: end date must be after start date
                    if (endDate.before(startDate)) {
                        Toast.makeText(this, "End date must be after start date",
                                Toast.LENGTH_SHORT).show();
                        endDate = (Calendar) startDate.clone();
                        endDate.add(Calendar.DAY_OF_MONTH, 6);
                    }

                    updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
                },
                defaultEnd.get(Calendar.YEAR),
                defaultEnd.get(Calendar.MONTH),
                defaultEnd.get(Calendar.DAY_OF_MONTH)
        );

        endDatePicker.getDatePicker().setMinDate(startDate.getTimeInMillis());
        endDatePicker.setTitle("Select END date");
        endDatePicker.show();
    }

    private void updateWeekRangeDisplayInDialog(TextView weekRangeText, ImageView clearWeekButton) {
        if (startDate != null && endDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());

            String startStr = sdf.format(startDate.getTime());
            String endStr = sdf.format(endDate.getTime());
            String year = yearFormat.format(startDate.getTime());

            weekRangeText.setText(startStr + " - " + endStr + ", " + year);
            clearWeekButton.setVisibility(View.VISIBLE);
        } else {
            weekRangeText.setText("Select week");
            clearWeekButton.setVisibility(View.GONE);
        }
    }

    private void setupTasksRealtimeListener(String userId) {
        if (tasksListener != null) {
            tasksListener.remove();
        }

        tasksListener = db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .orderBy("position")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Tasks listener error", e);
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) return;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String taskId = doc.getId();
                        String day = doc.getString("day");
                        Boolean isCompleted = doc.getBoolean("isCompleted");

                        List<WeeklyTask> tasks = dayTasks.get(day);
                        if (tasks != null) {
                            for (WeeklyTask task : tasks) {
                                if (task.getId().equals(taskId)) {
                                    boolean newStatus = isCompleted != null && isCompleted;
                                    if (task.isCompleted() != newStatus) {
                                        task.setCompleted(newStatus);
                                        WeeklyTaskAdapter adapter = dayAdapters.get(day);
                                        if (adapter != null) {
                                            adapter.notifyDataSetChanged();
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksListener != null) {
            tasksListener.remove();
        }

    }
    // ✅ ADD this method in your WeeklyActivity class
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (isDragging) {
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP ||
                    ev.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                // Handle drop
                handleCrossDayDrop(ev.getRawY());
            }
            return true; // Consume event during drag
        }

        return super.dispatchTouchEvent(ev);
    }
    // KEYBOARD TOOLBAR SETUP
    private void setupKeyboardToolbar() {
        // Headings & Font
        headingsAndFont.setOnClickListener(v -> showHeadingOptions());

        // Theme
        addThemeBtn.setOnClickListener(v -> toggleColorPicker());

        // Subpage (create new weekly plan)
        addSubpageBtn.setOnClickListener(v -> createSubWeeklyPlan());
    }

    // HEADINGS & FONTS
    private void showHeadingOptions() {
      //  Toast.makeText(this, "Font styles only apply to the plan title, not tasks",
              //  Toast.LENGTH_LONG).show();
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.headings_fonts_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        // Get all option views
        LinearLayout heading1Option = sheetView.findViewById(R.id.heading1Option);
        LinearLayout heading2Option = sheetView.findViewById(R.id.heading2Option);
        LinearLayout heading3Option = sheetView.findViewById(R.id.heading3Option);
        LinearLayout boldOption = sheetView.findViewById(R.id.boldOption);
        LinearLayout italicOption = sheetView.findViewById(R.id.italicOption);
        LinearLayout boldItalicOption = sheetView.findViewById(R.id.boldItalicOption);
        LinearLayout normalOption = sheetView.findViewById(R.id.normalOption);

        // Font color options
        LinearLayout fontColorDefault = sheetView.findViewById(R.id.fontColorDefault);
        LinearLayout fontColorRed = sheetView.findViewById(R.id.fontColorRed);
        LinearLayout fontColorOrange = sheetView.findViewById(R.id.fontColorOrange);
        LinearLayout fontColorYellow = sheetView.findViewById(R.id.fontColorYellow);
        LinearLayout fontColorGreen = sheetView.findViewById(R.id.fontColorGreen);
        LinearLayout fontColorBlue = sheetView.findViewById(R.id.fontColorBlue);
        LinearLayout fontColorPurple = sheetView.findViewById(R.id.fontColorPurple);
        LinearLayout fontColorPink = sheetView.findViewById(R.id.fontColorPink);
        LinearLayout fontColorBrown = sheetView.findViewById(R.id.fontColorBrown);
        LinearLayout fontColorGray = sheetView.findViewById(R.id.fontColorGray);

        // Heading listeners
        if (heading1Option != null) {
            heading1Option.setOnClickListener(v -> {
                applyTextStyle("heading1");
                bottomSheet.dismiss();
            });
        }

        if (heading2Option != null) {
            heading2Option.setOnClickListener(v -> {
                applyTextStyle("heading2");
                bottomSheet.dismiss();
            });
        }

        if (heading3Option != null) {
            heading3Option.setOnClickListener(v -> {
                applyTextStyle("heading3");
                bottomSheet.dismiss();
            });
        }

        // Font style listeners
        if (boldOption != null) {
            boldOption.setOnClickListener(v -> {
                applyTextStyle("bold");
                bottomSheet.dismiss();
            });
        }

        if (italicOption != null) {
            italicOption.setOnClickListener(v -> {
                applyTextStyle("italic");
                bottomSheet.dismiss();
            });
        }

        if (boldItalicOption != null) {
            boldItalicOption.setOnClickListener(v -> {
                applyTextStyle("boldItalic");
                bottomSheet.dismiss();
            });
        }

        if (normalOption != null) {
            normalOption.setOnClickListener(v -> {
                applyTextStyle("normal");
                bottomSheet.dismiss();
            });
        }

        // Font color listeners
        if (fontColorDefault != null) {
            fontColorDefault.setOnClickListener(v -> {
                applyFontColor("#333333");
                bottomSheet.dismiss();
            });
        }

        if (fontColorRed != null) {
            fontColorRed.setOnClickListener(v -> {
                applyFontColor("#E53935");
                bottomSheet.dismiss();
            });
        }

        if (fontColorOrange != null) {
            fontColorOrange.setOnClickListener(v -> {
                applyFontColor("#FB8C00");
                bottomSheet.dismiss();
            });
        }

        if (fontColorYellow != null) {
            fontColorYellow.setOnClickListener(v -> {
                applyFontColor("#FDD835");
                bottomSheet.dismiss();
            });
        }

        if (fontColorGreen != null) {
            fontColorGreen.setOnClickListener(v -> {
                applyFontColor("#43A047");
                bottomSheet.dismiss();
            });
        }

        if (fontColorBlue != null) {
            fontColorBlue.setOnClickListener(v -> {
                applyFontColor("#1E88E5");
                bottomSheet.dismiss();
            });
        }

        if (fontColorPurple != null) {
            fontColorPurple.setOnClickListener(v -> {
                applyFontColor("#8E24AA");
                bottomSheet.dismiss();
            });
        }

        if (fontColorPink != null) {
            fontColorPink.setOnClickListener(v -> {
                applyFontColor("#D81B60");
                bottomSheet.dismiss();
            });
        }

        if (fontColorBrown != null) {
            fontColorBrown.setOnClickListener(v -> {
                applyFontColor("#6D4C41");
                bottomSheet.dismiss();
            });
        }

        if (fontColorGray != null) {
            fontColorGray.setOnClickListener(v -> {
                applyFontColor("#757575");
                bottomSheet.dismiss();
            });
        }

        bottomSheet.show();
    }

    private void loadTitleFormatting() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || planId == null || planId.isEmpty()) {
            Log.d(TAG, "⚠️ Cannot load formatting - no user or planId");
            return;
        }

        db.collection("users").document(user.getUid())
                .collection("weeklyPlans").document(planId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String savedFontStyle = doc.getString("titleFontStyle");
                        titleFontStyle = (savedFontStyle != null) ? savedFontStyle : "normal";

                        Long fontSize = doc.getLong("titleFontSize");
                        titleFontSize = (fontSize != null) ? fontSize.intValue() : 16;

                        String savedFontColor = doc.getString("titleFontColor");
                        titleFontColor = (savedFontColor != null) ? savedFontColor : "#000000";

                        applyTitleFormattingFromData();
                        Log.d(TAG, "✅ Title formatting loaded");
                    } else {
                        Log.d(TAG, "📄 Document doesn't exist yet");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load title formatting", e);
                });
    }

    // 4. ADD NEW METHOD - Apply formatting:
    private void applyTitleFormattingFromData() {
        weeklyTitle.setTextSize(titleFontSize);
        weeklyTitle.setTextColor(Color.parseColor(titleFontColor));

        switch (titleFontStyle) {
            case "bold":
                weeklyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case "italic":
                weeklyTitle.setTypeface(null, android.graphics.Typeface.ITALIC);
                break;
            case "boldItalic":
                weeklyTitle.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
                break;
            default:
                weeklyTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
                break;
        }
    }

    // 5. REPLACE applyTextStyle():
    private void applyTextStyle(String style) {
        titleFontStyle = style;

        switch (style) {
            case "heading1":
                titleFontSize = 32;
                weeklyTitle.setTextSize(32);
                weeklyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case "heading2":
                titleFontSize = 24;
                weeklyTitle.setTextSize(24);
                weeklyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case "heading3":
                titleFontSize = 20;
                weeklyTitle.setTextSize(20);
                weeklyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case "bold":
                weeklyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case "italic":
                weeklyTitle.setTypeface(null, android.graphics.Typeface.ITALIC);
                break;
            case "boldItalic":
                weeklyTitle.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
                break;
            case "normal":
                titleFontSize = 16;
                weeklyTitle.setTextSize(16);
                weeklyTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
                break;
        }

        saveTitleFormatting();
      //  Toast.makeText(this, "Title style applied", Toast.LENGTH_SHORT).show();
    }

    // 6. REPLACE applyFontColor():
    private void applyFontColor(String color) {
        titleFontColor = color;
        weeklyTitle.setTextColor(Color.parseColor(color));
        saveTitleFormatting();
      //  Toast.makeText(this, "Color applied", Toast.LENGTH_SHORT).show();
    }
    private void saveTitleFormatting() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (isNewPlan || planId == null || planId.isEmpty()) {
            planId = db.collection("users")
                    .document(user.getUid())
                    .collection("weeklyPlans")
                    .document().getId();
            isNewPlan = false;
            Log.d(TAG, "✅ Generated new planId for formatting: " + planId);
        }

        Map<String, Object> formatting = new HashMap<>();
        formatting.put("titleFontStyle", titleFontStyle);
        formatting.put("titleFontSize", titleFontSize);
        formatting.put("titleFontColor", titleFontColor);
        formatting.put("title", weeklyTitle.getText().toString().trim());
        formatting.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("users").document(user.getUid())
                .collection("weeklyPlans").document(planId)
                .set(formatting, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Title formatting saved");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save title formatting", e);
                });
    }

    // COLOR PICKER / THEMES
    private void setupColorPicker() {
        findViewById(R.id.colorDefault).setOnClickListener(v -> changeBackgroundColor("#FAFAFA"));
        findViewById(R.id.colorRed).setOnClickListener(v -> changeBackgroundColor("#FFCDD2"));
        findViewById(R.id.colorPink).setOnClickListener(v -> changeBackgroundColor("#F8BBD0"));
        findViewById(R.id.colorPurple).setOnClickListener(v -> changeBackgroundColor("#E1BEE7"));
        findViewById(R.id.colorBlue).setOnClickListener(v -> changeBackgroundColor("#BBDEFB"));
        findViewById(R.id.colorCyan).setOnClickListener(v -> changeBackgroundColor("#B2EBF2"));
        findViewById(R.id.colorGreen).setOnClickListener(v -> changeBackgroundColor("#C8E6C9"));
        findViewById(R.id.colorYellow).setOnClickListener(v -> changeBackgroundColor("#FFF9C4"));
        findViewById(R.id.colorOrange).setOnClickListener(v -> changeBackgroundColor("#FFE0B2"));
        findViewById(R.id.colorBrown).setOnClickListener(v -> changeBackgroundColor("#D7CCC8"));
        findViewById(R.id.colorGrey).setOnClickListener(v -> changeBackgroundColor("#CFD8DC"));
    }

    private void toggleColorPicker() {
        if (colorPickerPanel.getVisibility() == View.VISIBLE) {
            colorPickerPanel.setVisibility(View.GONE);
        } else {
            colorPickerPanel.setVisibility(View.VISIBLE);
        }
    }

    private void changeBackgroundColor(String color) {
        mainLayout.setBackgroundColor(Color.parseColor(color));
        currentBgColor = color;
        colorPickerPanel.setVisibility(View.GONE);

        // ✅ Save immediately
        saveBackgroundColor(color);
    }

    private void saveBackgroundColor(String color) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (isNewPlan || planId == null || planId.isEmpty()) {
            planId = db.collection("users")
                    .document(user.getUid())
                    .collection("weeklyPlans")
                    .document().getId();
            isNewPlan = false;
            Log.d(TAG, "✅ Generated new planId for background: " + planId);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("backgroundColor", color);
        updates.put("title", weeklyTitle.getText().toString().trim());
        updates.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("users").document(user.getUid())
                .collection("weeklyPlans").document(planId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Background color saved: " + color);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save background color", e);
                });
    }

    // ✅ FIXED loadBackgroundColor()
    private void loadBackgroundColor() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || planId == null || planId.isEmpty()) {
            Log.d(TAG, "⚠️ Cannot load background - no user or planId");
            return;
        }

        db.collection("users").document(user.getUid())
                .collection("weeklyPlans").document(planId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.contains("backgroundColor")) {
                        String color = doc.getString("backgroundColor");
                        if (color != null && !color.isEmpty()) {
                            currentBgColor = color;
                            mainLayout.setBackgroundColor(Color.parseColor(color));
                            Log.d(TAG, "✅ Background color loaded: " + color);
                        }
                    } else {
                        Log.d(TAG, "📄 Document exists but no backgroundColor field");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load background color", e);
                });
    }


    // SUBPAGE (Create new linked weekly plan)
    private void createSubWeeklyPlan() {
        if (isNewPlan) {
        //    Toast.makeText(this, "Please save this plan first", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String newPlanId = db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document().getId();

        Map<String, Object> subplanData = new HashMap<>();
        subplanData.put("title", "Sub-plan");
        subplanData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        subplanData.put("parentPlanId", planId);
        subplanData.put("taskCount", 0);
        subplanData.put("completedCount", 0);

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(newPlanId)
                .set(subplanData)
                .addOnSuccessListener(aVoid -> {
                    Map<String, Object> subpageRef = new HashMap<>();
                    subpageRef.put("subpageId", newPlanId);
                    subpageRef.put("title", "Sub-plan");
                    subpageRef.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

                    db.collection("users")
                            .document(user.getUid())
                            .collection("weeklyPlans")
                            .document(planId)
                            .collection("subpages")
                            .add(subpageRef)
                            .addOnSuccessListener(docRef -> {
                             //   Toast.makeText(this, "Sub-plan created", Toast.LENGTH_SHORT).show();

                                loadSubpages();
                                Intent intent = new Intent(this, WeeklyActivity.class);
                                intent.putExtra("planId", newPlanId);
                                startActivity(intent);
                            });
                });
    }
    private void loadSubpages() {
        if (isNewPlan || planId == null || planId.isEmpty()) {
            Log.d(TAG, "⚠️ Skipping subpages - new plan or no planId");
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        LinearLayout mainContainer = findViewById(R.id.mainLayout);

        // ✅ FIX: Remove old subpage views first
        List<View> subpageViews = new ArrayList<>();
        for (int i = 0; i < mainContainer.getChildCount(); i++) {
            View child = mainContainer.getChildAt(i);
            if (child.findViewById(R.id.subpageTitle) != null) {
                subpageViews.add(child);
            }
        }
        for (View view : subpageViews) {
            mainContainer.removeView(view);
        }

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .collection("subpages")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "📄 Found " + queryDocumentSnapshots.size() + " subpages");

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String subpageId = doc.getString("subpageId");
                        String subpageTitle = doc.getString("title");

                        View subpageView = LayoutInflater.from(this)
                                .inflate(R.layout.item_subpage, null, false);

                        TextView titleText = subpageView.findViewById(R.id.subpageTitle);
                        titleText.setText(subpageTitle != null ? subpageTitle : "Sub-plan");

                        subpageView.setOnClickListener(v -> {
                            Intent intent = new Intent(this, WeeklyActivity.class);
                            intent.putExtra("planId", subpageId);
                            startActivity(intent);
                        });

                        int insertIndex = mainContainer.getChildCount() - 2;
                        mainContainer.addView(subpageView, insertIndex);

                        Log.d(TAG, "✅ Added subpage view: " + subpageTitle);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load subpages", e);
                });
    }
    // ✅ ADD this method to WeeklyActivity class (add anywhere in the class)

    private String formatTimeForDisplay(String time24) {
        if (time24 == null || time24.isEmpty()) {
            return "";
        }

        // Get user's time format preference
        String timeFormat = Settings.getTimeFormat(this);

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
            android.util.Log.e(TAG, "Error formatting time", e);
            return time24;
        }
    }

    // ✅ REPLACE your existing updateScheduleDisplay() method with this:
    private void updateScheduleDisplay() {
        // Only display if time is set
        if (startDate != null && endDate != null && selectedTime != null && !selectedTime.isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            String displayText = sdf.format(startDate.getTime()) + " - " + sdf.format(endDate.getTime());

            // ✅ Format time according to user preference
            String formattedTime = formatTimeForDisplay(selectedTime);
            displayText += ", " + formattedTime;

            dueDateText.setText(displayText);
            dueDateDisplay.setVisibility(View.VISIBLE);
        } else {
            // Hide if no time is set
            dueDateDisplay.setVisibility(View.GONE);
        }
    }

    // ✅ NEW METHOD: Clear schedule
    // ✅ FIXED: Clear schedule without exiting
    // ✅ FIXED: Clear display but keep week range
    private void clearSchedule() {
        // Cancel weekly notification
        if (!isNewPlan && planId != null) {
            NotificationHelper.cancelNotification(this, "weekly_" + planId);
        }

        // Reset to current week (don't set to null)
        setCurrentWeek();

        // Clear time and reminder settings
        selectedTime = "";
        hasReminder = false;
        reminderMinutes = 60;

        // Hide display
        dueDateDisplay.setVisibility(View.GONE);

        // ✅ Save WITHOUT exiting
        if (!isNewPlan) {
            saveWeekScheduleOnly();
        }

       // Toast.makeText(this, "Schedule cleared", Toast.LENGTH_SHORT).show();
    }
    private void saveWeekScheduleOnly() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || planId == null || planId.isEmpty()) return;

        Map<String, Object> scheduleData = new HashMap<>();

        // Keep the week range dates
        if (startDate != null && endDate != null) {
            scheduleData.put("startDate", new Timestamp(startDate.getTime()));
            scheduleData.put("endDate", new Timestamp(endDate.getTime()));
        }

        // Save time and notification
        scheduleData.put("time", selectedTime != null ? selectedTime : "");
        scheduleData.put("hasReminder", hasReminder);
        scheduleData.put("reminderMinutes", reminderMinutes);

        // Update weeklyPlans
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .update(scheduleData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Week schedule saved");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save week schedule", e);
                });

        // Update schedules collection
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .whereEqualTo("sourceId", planId)
                .whereEqualTo("category", "weekly")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String scheduleId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("users")
                                .document(user.getUid())
                                .collection("schedules")
                                .document(scheduleId)
                                .update(scheduleData);
                    }
                });
    }

    // ✅ NEW: Save cleared schedule (no time, but keep week range)
    private void saveClearedSchedule() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || planId == null || planId.isEmpty()) return;

        Map<String, Object> scheduleData = new HashMap<>();

        // ✅ Keep the week range dates
        if (startDate != null && endDate != null) {
            scheduleData.put("startDate", new Timestamp(startDate.getTime()));
            scheduleData.put("endDate", new Timestamp(endDate.getTime()));
        }

        // ✅ Clear the time and notification settings
        scheduleData.put("time", "");
        scheduleData.put("hasReminder", false);
        scheduleData.put("reminderMinutes", 60);

        // Update weeklyPlans document
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .update(scheduleData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Schedule cleared (dates kept)");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to clear schedule", e);
                });

        // Update schedules document
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .whereEqualTo("sourceId", planId)
                .whereEqualTo("category", "weekly")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String scheduleId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("users")
                                .document(user.getUid())
                                .collection("schedules")
                                .document(scheduleId)
                                .update(scheduleData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✅ Schedule cleared in schedules collection");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Failed to clear schedule in schedules", e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to query schedules", e);
                });
    }
    // ✅ NEW: Save schedule data without finishing activity
    private void saveScheduleDataOnly() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || planId == null || planId.isEmpty()) return;

        // Update both weeklyPlans and schedules collections
        Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("startDate", null);
        scheduleData.put("endDate", null);
        scheduleData.put("time", "");
        scheduleData.put("hasReminder", false);
        scheduleData.put("reminderMinutes", 60);

        // Update weeklyPlans document
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .update(scheduleData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Schedule cleared in weeklyPlans");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to clear schedule in weeklyPlans", e);
                });

        // Update schedules document
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .whereEqualTo("sourceId", planId)
                .whereEqualTo("category", "weekly")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String scheduleId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("users")
                                .document(user.getUid())
                                .collection("schedules")
                                .document(scheduleId)
                                .update(scheduleData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✅ Schedule cleared in schedules");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Failed to clear schedule in schedules", e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to query schedules", e);
                });
    }
    private void scheduleWeeklyPlanNotification() {
        if (!hasReminder || selectedTime == null || selectedTime.isEmpty() || startDate == null) {
            Log.d(TAG, "⚠️ Cannot schedule weekly notification - missing data");
            return;
        }

        // Parse the selected time
        String[] timeParts = selectedTime.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        // ✅ FIX: Use TODAY's date instead of startDate
        Calendar notificationTime = Calendar.getInstance();
        notificationTime.set(Calendar.HOUR_OF_DAY, hour);
        notificationTime.set(Calendar.MINUTE, minute);
        notificationTime.set(Calendar.SECOND, 0);
        notificationTime.set(Calendar.MILLISECOND, 0);

        // Subtract reminder minutes
        notificationTime.add(Calendar.MINUTE, -reminderMinutes);

        // ✅ If the notification time has passed today, schedule for tomorrow
        if (notificationTime.getTimeInMillis() <= System.currentTimeMillis()) {
            notificationTime.add(Calendar.DAY_OF_MONTH, 1);
            Log.d(TAG, "⏭️ Notification time passed today, scheduling for tomorrow");
        }

        // ✅ Check if notification time is still within the week range
        if (endDate != null && notificationTime.getTimeInMillis() > endDate.getTimeInMillis()) {
            Log.d(TAG, "⚠️ Notification would be after week end date, skipping");
            Toast.makeText(this, "Week has ended - notification not scheduled", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = weeklyTitle.getText().toString().trim();
        if (title.isEmpty()) {
            title = "Weekly Plan";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
        String weekRange = sdf.format(startDate.getTime()) + " - " + sdf.format(endDate.getTime());
        String notificationBody = "Your weekly plan (" + weekRange + ") reminder at " + selectedTime;

        // Schedule the notification
        NotificationHelper.scheduleNotification(
                this,
                "weekly_" + planId,
                title,
                notificationBody,
                notificationTime.getTimeInMillis(),
                "weekly",
                planId
        );

        SimpleDateFormat fullDateFormat = new SimpleDateFormat("MMM dd 'at' h:mm a", Locale.getDefault());
        Log.d(TAG, "✅ Scheduled weekly notification for: " + fullDateFormat.format(notificationTime.getTime()));
        Toast.makeText(this, "Reminder set for " + fullDateFormat.format(notificationTime.getTime()),
                Toast.LENGTH_SHORT).show();
    }
}