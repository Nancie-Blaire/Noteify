package com.example.testtasksync;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String KEY_SELECTED_NAV = "selected_nav_item";

    // Navigation icons
    private ImageView Home, Calendar, Notifs, Settings;

    // FABs
    private ExtendedFloatingActionButton fabNote, fabTodo, fabWeekly;
    private FloatingActionButton fabMain;
    private boolean isFabOpen = false;

    // Overlay view for dimmed background
    private View overlayView;

    // Track which nav item is selected
    private int selectedNavId = R.id.Home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity onCreate started");

        // Initialize navigation icons
        Home = findViewById(R.id.Home);
        Calendar = findViewById(R.id.Calendar);
        Notifs = findViewById(R.id.Notifs);
        Settings = findViewById(R.id.Settings);

        // Initialize FABs
        fabMain = findViewById(R.id.fabMain);
        fabNote = findViewById(R.id.fabNote);
        fabTodo = findViewById(R.id.fabTodo);
        fabWeekly = findViewById(R.id.fabWeekly);

        // Initialize overlay
        overlayView = findViewById(R.id.overlayView);

        // Verify FABs are found
        if (fabMain == null || fabNote == null || fabTodo == null || fabWeekly == null) {
            Toast.makeText(this, "ERROR: FABs not found!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "FABs not found in layout!");
            return;
        }

        if (overlayView == null) {
            Log.e(TAG, "Overlay view not found! Make sure to add it to activity_main.xml");
        }

        if (Home == null || Calendar == null || Notifs == null || Settings == null) {
            Log.e(TAG, "One or more nav icons are null. Check activity_main.xml IDs.");
            return;
        }

        // Set up overlay click listener to close FAB menu
        if (overlayView != null) {
            overlayView.setOnClickListener(v -> {
                Log.d(TAG, "Overlay clicked - closing FAB menu");
                closeFABMenu();
            });
        }

        // Set up navigation click listeners
        Home.setOnClickListener(v -> {
            Log.d(TAG, "Home icon clicked");
            closeFABMenu(); // Close FAB menu when navigating
            loadFragment(new Notes());
            updateSelectedIcon(Home);
            selectedNavId = R.id.Home;
        });

        Calendar.setOnClickListener(v -> {
            Log.d(TAG, "Calendar icon clicked");
            closeFABMenu(); // Close FAB menu when navigating
            loadFragment(new CalendarFragment());
            updateSelectedIcon(Calendar);
            selectedNavId = R.id.Calendar;
        });

        Notifs.setOnClickListener(v -> {
            Log.d(TAG, "Notifications icon clicked");
            closeFABMenu(); // Close FAB menu when navigating
            loadFragment(new Notifications());
            updateSelectedIcon(Notifs);
            selectedNavId = R.id.Notifs;
        });

        Settings.setOnClickListener(v -> {
            Log.d(TAG, "Settings icon clicked");
            closeFABMenu(); // Close FAB menu when navigating
            loadFragment(new Settings());
            updateSelectedIcon(Settings);
            selectedNavId = R.id.Settings;
        });

        // Set up FAB functionality
        setupFAB();

        // Handle back button with modern API
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFabOpen) {
                    closeFABMenu();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Load default fragment (NotesFragment/Home) on app start
        if (savedInstanceState == null) {
            loadFragment(new Notes());
            updateSelectedIcon(Home);
        } else {
            // Restore selected navigation item after rotation
            selectedNavId = savedInstanceState.getInt(KEY_SELECTED_NAV, R.id.Home);
            restoreSelectedFragment();
        }

        /**LinearLayout bottomNav = findViewById(R.id.bottomNavRoot);
        bottomNav.setBackgroundResource(R.drawable.curved_navbar);**/
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Close FAB menu when activity goes to background or another activity starts
        closeFABMenu();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save which nav item is selected
        outState.putInt(KEY_SELECTED_NAV, selectedNavId);
    }

    /**
     * Restores the selected fragment after configuration change (like rotation)
     */
    private void restoreSelectedFragment() {
        ImageView selectedIcon;
        Fragment fragment;

        if (selectedNavId == R.id.Calendar) {
            fragment = new CalendarFragment();
            selectedIcon = Calendar;
        } else if (selectedNavId == R.id.Notifs) {
            fragment = new Notifications();
            selectedIcon = Notifs;
        } else if (selectedNavId == R.id.Settings) {
            fragment = new Settings();
            selectedIcon = Settings;
        } else {
            // Default to Home
            fragment = new Notes();
            selectedIcon = Home;
        }

        loadFragment(fragment);
        updateSelectedIcon(selectedIcon);
    }

    /**
     * Loads a Fragment into the fragmentContainer
     */
    private void loadFragment(Fragment fragment) {
        if (findViewById(R.id.fragmentContainer) == null) {
            Log.e(TAG, "fragmentContainer not found!");
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    /**
     * Updates the circle background indicator
     */
    private void updateSelectedIcon(ImageView selectedIcon) {
        // Remove circle from all icons
        Home.setBackgroundResource(0);
        Calendar.setBackgroundResource(0);
        Notifs.setBackgroundResource(0);
        Settings.setBackgroundResource(0);

        // Clear tints
        Home.setBackgroundTintList(null);
        Calendar.setBackgroundTintList(null);
        Notifs.setBackgroundTintList(null);
        Settings.setBackgroundTintList(null);

        // Add circle to selected icon
        selectedIcon.setBackgroundResource(R.drawable.circle_bg);
        selectedIcon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
    }

    /**
     * Sets up all FAB functionality
     */
    private void setupFAB() {
        // Ensure sub-FABs start hidden
        fabNote.setVisibility(View.GONE);
        fabTodo.setVisibility(View.GONE);
        fabWeekly.setVisibility(View.GONE);

        // Ensure overlay starts hidden
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }

        // Main FAB toggles the menu
        fabMain.setOnClickListener(v -> {
            Log.d(TAG, "FAB Main clicked! Current state: " + isFabOpen);
            if (isFabOpen) {
                closeFABMenu();
            } else {
                openFABMenu();
            }
        });

        // Note FAB opens NoteActivity
        fabNote.setOnClickListener(v -> {
            Log.d(TAG, "FAB Note clicked");
            Intent intent = new Intent(MainActivity.this, NoteActivity.class);
            startActivity(intent);
            closeFABMenu();
        });

        // TodoActivity FAB (disabled for now)
        fabTodo.setOnClickListener(v -> {
            Log.d(TAG, "FAB clicked");
            Intent intent = new Intent(MainActivity.this, TodoActivity.class);
            startActivity(intent);
            closeFABMenu();
        });

        // Weekly FAB (disabled for now)
        fabWeekly.setOnClickListener(v -> {
            Log.d(TAG, "FAB Weekly clicked");
            Intent intent = new Intent(MainActivity.this, WeeklyActivity.class);
            startActivity(intent);
            closeFABMenu();
        });
    }

    /**
     * Opens the FAB menu with overlay
     */
    private void openFABMenu() {
        isFabOpen = true;
        Log.d(TAG, "Opening FAB menu");

        // Show overlay with fade-in animation
        if (overlayView != null) {
            overlayView.setVisibility(View.VISIBLE);
            overlayView.setAlpha(0f);
            overlayView.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
        }

        // Show sub-FABs
        fabNote.setVisibility(View.VISIBLE);
        fabTodo.setVisibility(View.VISIBLE);
        fabWeekly.setVisibility(View.VISIBLE);

        // Change main FAB to red with minus icon
        fabMain.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF5757"))); // Red
        fabMain.setImageResource(R.drawable.ic_fab_remove); // Change to minus icon

        // Optional: Rotate icon for smooth transition
        fabMain.animate().rotation(180f).setDuration(200).start();
    }

    /**
     * Closes the FAB menu and hides overlay
     */
    private void closeFABMenu() {
        if (!isFabOpen) return; // Already closed

        isFabOpen = false;
        Log.d(TAG, "Closing FAB menu");

        // Hide overlay with fade-out animation
        if (overlayView != null) {
            overlayView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> overlayView.setVisibility(View.GONE))
                    .start();
        }

        // Hide sub-FABs
        fabNote.setVisibility(View.GONE);
        fabTodo.setVisibility(View.GONE);
        fabWeekly.setVisibility(View.GONE);

        // Change main FAB back to cyan with plus icon
        fabMain.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#26E9D8"))); // Cyan
        fabMain.setImageResource(R.drawable.ic_fab_add); // Change back to plus icon

        // Optional: Reset rotation
        fabMain.animate().rotation(0f).setDuration(200).start();
    }
}