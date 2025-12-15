package com.example.testtasksync;

import static androidx.core.graphics.drawable.DrawableCompat.applyTheme;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

public class Settings extends Fragment {

    private LinearLayout userProfileIcon, btnTheme, btnSecurity, btnBin;
    private LinearLayout timeFormatSection, notificationSection;
    private LinearLayout timeFormatContent, notificationContent;
    private ImageView timeFormatArrow, notificationArrow;
    private RadioGroup timeFormatRadioGroup, notificationRadioGroup;
    private RadioButton rbMilitary, rbCivilian, rbNotificationOn, rbNotificationOff;
    private ImageButton btnBack;

    private LinearLayout themeContent;
    private ImageView themeArrow;
    private RadioGroup themeRadioGroup;
    private RadioButton rbLightTheme, rbDarkTheme, rbSystemTheme;

    private SharedPreferences preferences;
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_TIME_FORMAT = "time_format";
    private static final String KEY_NOTIFICATION = "notification_enabled";
    private static final String KEY_THEME = "theme";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Initialize SharedPreferences
        preferences = requireContext().getSharedPreferences(PREFS_NAME, requireContext().MODE_PRIVATE);

        // Initialize views
        initializeViews(view);

        // Setup expandable sections
        setupExpandableSections();

        // Setup navigation
        setupNavigation();

        // Load saved preferences
        loadSavedPreferences();

        return view;
    }

    private void initializeViews(View view) {

        // Expandable sections
        timeFormatSection = view.findViewById(R.id.timeFormatSection);
        timeFormatContent = view.findViewById(R.id.timeFormatContent);
        timeFormatArrow = view.findViewById(R.id.timeFormatArrow);
        timeFormatRadioGroup = view.findViewById(R.id.timeFormatRadioGroup);
        rbMilitary = view.findViewById(R.id.rbMilitary);
        rbCivilian = view.findViewById(R.id.rbCivilian);

        notificationSection = view.findViewById(R.id.notificationSection);
        notificationContent = view.findViewById(R.id.notificationContent);
        notificationArrow = view.findViewById(R.id.notificationArrow);
        notificationRadioGroup = view.findViewById(R.id.notificationRadioGroup);
        rbNotificationOn = view.findViewById(R.id.rbNotificationOn);
        rbNotificationOff = view.findViewById(R.id.rbNotificationOff);

        btnTheme = view.findViewById(R.id.btnTheme);
        themeContent = view.findViewById(R.id.themeContent);
        themeArrow = view.findViewById(R.id.themeArrow);
        themeRadioGroup = view.findViewById(R.id.themeRadioGroup);
        rbLightTheme = view.findViewById(R.id.rbLightTheme);
        rbDarkTheme = view.findViewById(R.id.rbDarkTheme);
        rbSystemTheme = view.findViewById(R.id.rbSystemTheme);

        // Navigation sections
        userProfileIcon = view.findViewById(R.id.userProfileIcon);
        btnTheme = view.findViewById(R.id.btnTheme);
        btnBin = view.findViewById(R.id.btnBin);
        btnSecurity = view.findViewById(R.id.btnSecurity);
    }

    private void setupExpandableSections() {
        // Back button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }

        // Time Format Section
        if (timeFormatSection != null && timeFormatContent != null && timeFormatArrow != null) {
            timeFormatSection.setOnClickListener(v -> {
                toggleSection(timeFormatContent, timeFormatArrow);
            });
        }

        // Notification Section
        if (notificationSection != null && notificationContent != null && notificationArrow != null) {
            notificationSection.setOnClickListener(v -> {
                toggleSection(notificationContent, notificationArrow);
            });
        }
// ✅ ADD THEME SECTION
        if (btnTheme != null && themeContent != null && themeArrow != null) {
            btnTheme.setOnClickListener(v -> {
                toggleSection(themeContent, themeArrow);
            });
        }
        // Time Format Radio Group Listener
        if (timeFormatRadioGroup != null) {
            timeFormatRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rbCivilian) {
                    saveTimeFormat("civilian");
                } else if (checkedId == R.id.rbMilitary) {
                    saveTimeFormat("military");
                }
            });
        }

        // ✅ UPDATED: Notification Radio Group Listener
        if (notificationRadioGroup != null) {
            notificationRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rbNotificationOn) {
                    saveNotificationSetting(true);
                    // ✅ NEW: Re-schedule all existing tasks when enabling notifications
                    rescheduleAllNotifications();
                    Toast.makeText(requireContext(),
                            "Notifications enabled. Scheduling reminders...",
                            Toast.LENGTH_SHORT).show();
                } else if (checkedId == R.id.rbNotificationOff) {
                    saveNotificationSetting(false);
                    // ✅ Cancel all existing notifications when disabled
                    NotificationHelper.cancelAllNotifications(requireContext());
                    Toast.makeText(requireContext(),
                            "Notifications disabled. All scheduled reminders cancelled.",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (themeRadioGroup != null) {
            themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                String theme;
                if (checkedId == R.id.rbLightTheme) {
                    theme = "light";
                } else if (checkedId == R.id.rbDarkTheme) {
                    theme = "dark";
                } else {
                    theme = "system";
                }
                saveTheme(theme);
                applyTheme(theme);
            });
        }
    }

    private void setupNavigation() {
        // Bin
        if (btnBin != null) {
            btnBin.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), Bin.class);
                startActivity(intent);
            });
        }

        // Profile
        if (userProfileIcon != null) {
            userProfileIcon.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), Account.class);
                startActivity(intent);
            });
        }

        // Security/Privacy
        if (btnSecurity != null) {
            btnSecurity.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), SecuritySettingsActivity.class);
                startActivity(intent);
            });
        }
    }

    private void toggleSection(LinearLayout content, ImageView arrow) {
        if (content.getVisibility() == View.GONE) {
            // Expand
            content.setVisibility(View.VISIBLE);
            rotateArrow(arrow, 0, 180);
        } else {
            // Collapse
            content.setVisibility(View.GONE);
            rotateArrow(arrow, 180, 0);
        }
    }

    private void rotateArrow(ImageView arrow, float fromDegrees, float toDegrees) {
        RotateAnimation rotate = new RotateAnimation(
                fromDegrees, toDegrees,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        rotate.setDuration(200);
        rotate.setFillAfter(true);
        arrow.startAnimation(rotate);
    }

    private void loadSavedPreferences() {
        // Load time format
        String timeFormat = preferences.getString(KEY_TIME_FORMAT, "civilian");
        if (timeFormat.equals("military")) {
            if (rbMilitary != null) rbMilitary.setChecked(true);
        } else {
            if (rbCivilian != null) rbCivilian.setChecked(true);
        }

        // Load notification setting
        boolean notificationEnabled = preferences.getBoolean(KEY_NOTIFICATION, true);
        if (notificationEnabled) {
            if (rbNotificationOn != null) rbNotificationOn.setChecked(true);
        } else {
            if (rbNotificationOff != null) rbNotificationOff.setChecked(true);
        }
        String theme = preferences.getString(KEY_THEME, "system");
        if (theme.equals("light")) {
            if (rbLightTheme != null) rbLightTheme.setChecked(true);
        } else if (theme.equals("dark")) {
            if (rbDarkTheme != null) rbDarkTheme.setChecked(true);
        } else {
            if (rbSystemTheme != null) rbSystemTheme.setChecked(true);
        }

    }

    private void saveTimeFormat(String format) {
        preferences.edit().putString(KEY_TIME_FORMAT, format).apply();
        // TODO: Apply time format change throughout the app
        // You might want to broadcast this change or use LiveData/ViewModel
    }

    private void saveNotificationSetting(boolean enabled) {
        preferences.edit().putBoolean(KEY_NOTIFICATION, enabled).apply();
    }
    private void saveTheme(String theme) {
        preferences.edit().putString(KEY_THEME, theme).apply();
    }

    private void applyTheme(String theme) {
        if (getActivity() != null) {
            switch (theme) {
                case "light":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    break;
                case "dark":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    break;
                case "system":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    break;
            }

        }
    }

    private void rescheduleAllNotifications() {
        NotificationScheduler.rescheduleAllNotifications(requireContext());
    }

    // Public method to get time format (can be called from other activities)
    public static String getTimeFormat(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, context.MODE_PRIVATE);
        return prefs.getString(KEY_TIME_FORMAT, "civilian");
    }

    // Public method to check if notifications are enabled
    public static boolean areNotificationsEnabled(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_NOTIFICATION, true);
    }

    public static String getTheme(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME, "system");
    }
}