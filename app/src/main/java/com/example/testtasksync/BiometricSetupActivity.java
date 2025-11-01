package com.example.testtasksync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

import java.util.concurrent.Executor;

public class BiometricSetupActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "NoteSecurityPrefs";
    private static final String MASTER_PASSWORD_KEY = "master_password";
    private static final String BIOMETRIC_ENABLED_KEY = "biometric_enabled";
    private static final String SECURITY_SETUP_COMPLETE = "security_setup_complete";

    private EditText passwordInput, confirmPasswordInput;
    private Button btnSetupFingerprint, btnSkipFingerprint, btnSavePassword;
    private TextView tvBiometricStatus, tvTitle;
    private boolean biometricAvailable = false;
    private boolean biometricVerified = false;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_biometric_setup);

        auth = FirebaseAuth.getInstance();

        tvTitle = findViewById(R.id.tvSetupTitle);
        tvBiometricStatus = findViewById(R.id.tvBiometricStatus);
        passwordInput = findViewById(R.id.etMasterPassword);
        confirmPasswordInput = findViewById(R.id.etConfirmPassword);
        btnSetupFingerprint = findViewById(R.id.btnSetupFingerprint);
        btnSkipFingerprint = findViewById(R.id.btnSkipFingerprint);
        btnSavePassword = findViewById(R.id.btnSavePassword);

        // Check if already setup - if coming from Notes activity
        boolean alreadySetup = isSecuritySetupComplete();

        if (alreadySetup) {
            // Already setup, just show the password setup (they might want to reset)
            proceedToPasswordSetup();
        } else {
            // First time setup - check biometric
            checkBiometricAvailability();
        }

        btnSetupFingerprint.setOnClickListener(v -> {
            if (biometricAvailable) {
                verifyBiometric();
            } else {
                Toast.makeText(this, "Fingerprint not available on this device",
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnSkipFingerprint.setOnClickListener(v -> {
            // Skip biometric, just set password
            proceedToPasswordSetup();
        });

        btnSavePassword.setOnClickListener(v -> {
            saveMasterPassword();
        });

        // ✅ REMOVED: No more "Skip for Now" button functionality
        // Button btnSkipSetup = findViewById(R.id.btnSkipSetup);
        // btnSkipSetup.setOnClickListener(v -> skipSetupAndGoToNotes());
    }

    // Helper method to get user-specific preference key
    private String getUserKey(String baseKey) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return user.getUid() + "_" + baseKey;
        }
        return baseKey; // Fallback (shouldn't happen)
    }

    // Helper method to check if security setup is complete for current user
    private boolean isSecuritySetupComplete() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(getUserKey(SECURITY_SETUP_COMPLETE), false);
    }

    private void checkBiometricAvailability() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG);

        switch (canAuthenticate) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                biometricAvailable = true;
                tvBiometricStatus.setText("✓ Fingerprint sensor detected");
                tvBiometricStatus.setTextColor(getColor(android.R.color.holo_green_dark));
                btnSetupFingerprint.setEnabled(true);
                break;

            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                tvBiometricStatus.setText("✗ No fingerprint sensor on this device");
                tvBiometricStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                btnSetupFingerprint.setEnabled(false);
                // DON'T auto-proceed - let user click Skip button
                break;

            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                tvBiometricStatus.setText("✗ No fingerprints registered. Please add fingerprints in your phone Settings first.");
                tvBiometricStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
                btnSetupFingerprint.setEnabled(false);
                break;

            default:
                tvBiometricStatus.setText("✗ Fingerprint not available");
                tvBiometricStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                btnSetupFingerprint.setEnabled(false);
                break;
        }
    }

    private void verifyBiometric() {
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        biometricVerified = true;

                        // Save biometric preference with user-specific key
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        prefs.edit().putBoolean(getUserKey(BIOMETRIC_ENABLED_KEY), true).apply();

                        Toast.makeText(BiometricSetupActivity.this,
                                "✓ Fingerprint enabled!", Toast.LENGTH_SHORT).show();

                        tvBiometricStatus.setText("✓ Fingerprint successfully verified!");
                        tvBiometricStatus.setTextColor(getColor(android.R.color.holo_green_dark));

                        // Proceed to password setup
                        proceedToPasswordSetup();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(BiometricSetupActivity.this,
                                "Fingerprint not recognized", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(BiometricSetupActivity.this,
                                "Error: " + errString, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify Your Fingerprint")
                .setSubtitle("We'll use this to unlock your private notes")
                .setDescription("Touch the fingerprint sensor")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void proceedToPasswordSetup() {
        // Hide biometric buttons, show password fields
        btnSetupFingerprint.setEnabled(false);
        btnSkipFingerprint.setEnabled(false);

        tvTitle.setText("Create Master Password");

        // ✅ FIXED: Different message based on context
        if (isSecuritySetupComplete()) {
            tvBiometricStatus.setText("Resetting your security settings");
        } else {
            tvBiometricStatus.setText("Set a master password to lock your notes");
        }

        passwordInput.setVisibility(android.view.View.VISIBLE);
        confirmPasswordInput.setVisibility(android.view.View.VISIBLE);
        btnSavePassword.setVisibility(android.view.View.VISIBLE);

        // ✅ Hide the "Skip for Now" button when in password setup
      //  Button btnSkipSetup = findViewById(R.id.btnSkipSetup);
       // if (btnSkipSetup != null) {
        //    btnSkipSetup.setVisibility(android.view.View.GONE);
       // }
    }

    private void saveMasterPassword() {
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        if (password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please enter password", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 4) {
            Toast.makeText(this, "Password must be at least 4 characters",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save master password AND mark setup as complete with user-specific keys
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String passwordKey = getUserKey(MASTER_PASSWORD_KEY);
        String setupCompleteKey = getUserKey(SECURITY_SETUP_COMPLETE);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(passwordKey, password);
        editor.putBoolean(setupCompleteKey, true);
        editor.apply(); // ✅ Make sure this is called!

        // ✅ DEBUG: Verify what was saved AFTER apply()
        android.util.Log.d("BiometricSetup", "=== SAVING SECURITY SETUP ===");
        android.util.Log.d("BiometricSetup", "User ID: " + (auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "NULL"));
        android.util.Log.d("BiometricSetup", "Password key: " + passwordKey);
        android.util.Log.d("BiometricSetup", "Setup complete key: " + setupCompleteKey);

        // Wait a moment then verify
        new android.os.Handler().postDelayed(() -> {
            String savedPassword = prefs.getString(passwordKey, null);
            boolean savedSetup = prefs.getBoolean(setupCompleteKey, false);

            android.util.Log.d("BiometricSetup", "Verification - Password exists: " + (savedPassword != null));
            android.util.Log.d("BiometricSetup", "Verification - Setup complete: " + savedSetup);

            if (savedPassword != null && savedSetup) {
                android.util.Log.d("BiometricSetup", "✅ SAVE SUCCESSFUL!");
            } else {
                android.util.Log.e("BiometricSetup", "❌ SAVE FAILED!");
            }
        }, 100);

        Toast.makeText(this, "✓ Security setup complete!", Toast.LENGTH_SHORT).show();

        // Show summary
        String summary = biometricVerified
                ? "✓ Fingerprint enabled\n✓ Master password set"
                : "✓ Master password set\n(Fingerprint not enabled)";

        Toast.makeText(this, summary, Toast.LENGTH_LONG).show();

        // Go to main app
        Intent intent = new Intent(this, Notes.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ✅ UPDATED: Clearer message when skipping
    private void skipSetupAndGoToNotes() {
        Toast.makeText(this, "Security setup skipped. You can set it up later when you lock a note.",
                Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, Notes.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}