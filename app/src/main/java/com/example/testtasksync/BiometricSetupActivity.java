package com.example.testtasksync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class BiometricSetupActivity extends AppCompatActivity {

    private static final String TAG = "BiometricSetup";
    private static final String PREFS_NAME = "NoteSecurityPrefs";
    private static final String MASTER_PASSWORD_KEY = "master_password";
    private static final String BIOMETRIC_ENABLED_KEY = "biometric_enabled";
    private static final String SECURITY_SETUP_COMPLETE = "security_setup_complete";

    private EditText passwordInput, confirmPasswordInput;
    private Button btnSetupFingerprint, btnSkipFingerprint, btnSavePassword;
    private TextView tvBiometricStatus, tvTitle;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String temporaryPassword = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_biometric_setup);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#f4e8df"));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // This makes the status bar icons dark
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }

        Log.d(TAG, "🚀 BiometricSetupActivity started");

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        tvTitle = findViewById(R.id.tvSetupTitle);
        tvBiometricStatus = findViewById(R.id.tvBiometricStatus);
        passwordInput = findViewById(R.id.etMasterPassword);
        confirmPasswordInput = findViewById(R.id.etConfirmPassword);
        btnSetupFingerprint = findViewById(R.id.btnSetupFingerprint);
        btnSkipFingerprint = findViewById(R.id.btnSkipFingerprint);
        btnSavePassword = findViewById(R.id.btnSavePassword);

        // ✅ Verify all views are found
        if (btnSetupFingerprint == null) Log.e(TAG, "❌ btnSetupFingerprint is NULL!");
        if (btnSkipFingerprint == null) Log.e(TAG, "❌ btnSkipFingerprint is NULL!");
        if (btnSavePassword == null) Log.e(TAG, "❌ btnSavePassword is NULL!");

        // Show password setup first
        showPasswordSetup();
    }

    private String getUserKey(String baseKey) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return user.getUid() + "_" + baseKey;
        }
        return baseKey;
    }

    // ✅ Step 1: Show password setup screen
    private void showPasswordSetup() {
        Log.d(TAG, "📝 Showing password setup screen");

        // Hide biometric buttons
        if (btnSetupFingerprint != null) btnSetupFingerprint.setVisibility(android.view.View.GONE);
        if (btnSkipFingerprint != null) btnSkipFingerprint.setVisibility(android.view.View.GONE);

        // Show password fields
        tvTitle.setText("Create Master Password");
        tvBiometricStatus.setText("Set a master password to lock your notes");
        passwordInput.setVisibility(android.view.View.VISIBLE);
        confirmPasswordInput.setVisibility(android.view.View.VISIBLE);
        btnSavePassword.setVisibility(android.view.View.VISIBLE);

        // ✅ IMPORTANT: Set click listener HERE
        btnSavePassword.setOnClickListener(v -> {
            Log.d(TAG, "💾 Save Password button clicked");
            validatePassword();
        });
    }

    // ✅ Step 2: Validate password
    private void validatePassword() {
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        Log.d(TAG, "Validating password (length: " + password.length() + ")");

        if (password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please enter password", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 4) {
            Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show();
            return;
        }

        // Store password temporarily
        temporaryPassword = password;
        Log.d(TAG, "Password validated successfully");

        // Check if biometric is available
        checkBiometricAvailability();
    }

    // ✅ Step 3: Check if biometric is available
    private void checkBiometricAvailability() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int status = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        Log.d(TAG, "🔍 Biometric status code: " + status);

        switch (status) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                Log.d(TAG, "✅ Biometric available - showing fingerprint option");
                showFingerprintOption();
                break;

            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                Log.d(TAG, "❌ No biometric hardware");
                Toast.makeText(this, "No fingerprint sensor on this device", Toast.LENGTH_SHORT).show();
                saveMasterPassword(false);
                break;

            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                Log.d(TAG, "⚠️ No fingerprints enrolled");
                Toast.makeText(this, "No fingerprints enrolled. Please add fingerprint in device settings first.", Toast.LENGTH_LONG).show();
                saveMasterPassword(false);
                break;

            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                Log.d(TAG, "❌ Biometric hardware unavailable");
                saveMasterPassword(false);
                break;

            default:
                Log.d(TAG, "❌ Biometric not available (status: " + status + ")");
                saveMasterPassword(false);
                break;
        }
    }

    // ✅ Step 4: Show fingerprint enrollment option
    private void showFingerprintOption() {
        Log.d(TAG, "📱 Showing fingerprint option screen");

        runOnUiThread(() -> {
            // Hide password fields
            passwordInput.setVisibility(android.view.View.GONE);
            confirmPasswordInput.setVisibility(android.view.View.GONE);
            btnSavePassword.setVisibility(android.view.View.GONE);

            // Update text
            tvTitle.setText("Enable Fingerprint?");
            tvBiometricStatus.setText("Would you like to use your fingerprint to unlock notes on this device?\n\n💡 You can change this later in Settings");

            // Show biometric buttons
            if (btnSetupFingerprint != null) {
                btnSetupFingerprint.setVisibility(android.view.View.VISIBLE);
                btnSetupFingerprint.setEnabled(true);
                Log.d(TAG, "✅ Enable Fingerprint button is now visible and enabled");
            } else {
                Log.e(TAG, "❌ btnSetupFingerprint is NULL!");
            }

            if (btnSkipFingerprint != null) {
                btnSkipFingerprint.setVisibility(android.view.View.VISIBLE);
                btnSkipFingerprint.setEnabled(true);
                Log.d(TAG, "✅ Skip Fingerprint button is now visible and enabled");
            } else {
                Log.e(TAG, "❌ btnSkipFingerprint is NULL!");
            }

            // ✅ CRITICAL: Set click listeners AFTER making visible
            if (btnSetupFingerprint != null) {
                btnSetupFingerprint.setOnClickListener(v -> {
                    Log.d(TAG, "🔘 Enable Fingerprint button CLICKED!");
                    Toast.makeText(this, "Starting fingerprint enrollment...", Toast.LENGTH_SHORT).show();
                    enrollFingerprint();
                });
            }

            if (btnSkipFingerprint != null) {
                btnSkipFingerprint.setOnClickListener(v -> {
                    Log.d(TAG, "⏭️ Skip Fingerprint button CLICKED!");
                    Toast.makeText(this, "Skipping fingerprint setup...", Toast.LENGTH_SHORT).show();
                    saveMasterPassword(false);
                });
            }
        });
    }

    // ✅ Step 5: Enroll fingerprint
    private void enrollFingerprint() {
        Log.d(TAG, "👆 Starting fingerprint enrollment...");

        try {
            Executor executor = ContextCompat.getMainExecutor(this);

            BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            Log.d(TAG, "✅ Fingerprint authentication SUCCEEDED!");
                            Toast.makeText(BiometricSetupActivity.this, "Fingerprint verified!", Toast.LENGTH_SHORT).show();
                            saveMasterPassword(true);
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Log.d(TAG, "❌ Fingerprint authentication FAILED");
                            Toast.makeText(BiometricSetupActivity.this, "Fingerprint not recognized. Try again.", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            Log.e(TAG, "❌ Authentication ERROR: " + errorCode + " - " + errString);

                            // User cancelled
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                    errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                                Log.d(TAG, "User cancelled - falling back to password only");
                                Toast.makeText(BiometricSetupActivity.this, "Fingerprint setup cancelled", Toast.LENGTH_SHORT).show();
                                saveMasterPassword(false);
                            } else {
                                Toast.makeText(BiometricSetupActivity.this, "Error: " + errString, Toast.LENGTH_SHORT).show();
                                saveMasterPassword(false);
                            }
                        }
                    });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Enroll Fingerprint")
                    .setSubtitle("Scan your fingerprint to enable it on this device")
                    .setDescription("This will allow you to unlock notes using your fingerprint")
                    .setNegativeButtonText("Cancel")
                    .build();

            Log.d(TAG, "📱 Showing biometric prompt...");
            biometricPrompt.authenticate(promptInfo);

        } catch (Exception e) {
            Log.e(TAG, "❌ EXCEPTION while showing biometric prompt", e);
            Toast.makeText(this, "Failed to show fingerprint prompt: " + e.getMessage(), Toast.LENGTH_LONG).show();
            saveMasterPassword(false);
        }
    }

    // ✅ Step 6: Save password and biometric preference
    private void saveMasterPassword(boolean enableBiometric) {
        Log.d(TAG, "💾 Saving master password (biometric: " + enableBiometric + ")");

        if (temporaryPassword == null) {
            Log.e(TAG, "❌ temporaryPassword is NULL!");
            Toast.makeText(this, "Error: Password not set", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Log.e(TAG, "❌ User not logged in");
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable buttons during save
        if (btnSetupFingerprint != null) btnSetupFingerprint.setEnabled(false);
        if (btnSkipFingerprint != null) btnSkipFingerprint.setEnabled(false);
        tvBiometricStatus.setText("Saving your security settings...");

        // Save to Firestore
        Map<String, Object> securityData = new HashMap<>();
        securityData.put("masterPassword", temporaryPassword);  // ⚠️ In production, hash this!
        securityData.put("securitySetupComplete", true);
        securityData.put("updatedAt", System.currentTimeMillis());

        Log.d(TAG, "☁️ Saving to Firestore...");

        db.collection("users")
                .document(user.getUid())
                .collection("security")
                .document("settings")
                .set(securityData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Firestore save successful");

                    // Save locally
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit()
                            .putString(getUserKey(MASTER_PASSWORD_KEY), temporaryPassword)
                            .putBoolean(getUserKey(SECURITY_SETUP_COMPLETE), true)
                            .putBoolean(getUserKey(BIOMETRIC_ENABLED_KEY), enableBiometric)
                            .apply();

                    Log.d(TAG, "✅ Local save successful");
                    Log.d(TAG, "📊 Final state - biometric_enabled: " + enableBiometric);

                    String message = enableBiometric ?
                            "✅ Security setup complete!\n Password + Fingerprint enabled" :
                            "✅ Security setup complete!\n Password authentication enabled";

                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    // Clear temporary password
                    temporaryPassword = null;

                    // Go to main app
                    Log.d(TAG, "🚀 Redirecting to MainActivity");
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Firestore save FAILED", e);

                    if (btnSetupFingerprint != null) btnSetupFingerprint.setEnabled(true);
                    if (btnSkipFingerprint != null) btnSkipFingerprint.setEnabled(true);
                    tvBiometricStatus.setText("Failed to save. Please try again.");
                    Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}