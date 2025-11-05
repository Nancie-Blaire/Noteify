package com.example.testtasksync;

import android.app.AlertDialog;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class SecuritySettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "NoteSecurityPrefs";
    private static final String MASTER_PASSWORD_KEY = "master_password";
    private static final String BIOMETRIC_ENABLED_KEY = "biometric_enabled";
    private static final String SECURITY_SETUP_COMPLETE = "security_setup_complete";

    private TextView tvSecurityStatus, tvBiometricStatus, tvPasswordStatus;
    private Button btnEnableFingerprint, btnChangePassword, btnBack;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_settings);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        tvSecurityStatus = findViewById(R.id.tvSecurityStatus);
        tvBiometricStatus = findViewById(R.id.tvBiometricStatus);
        tvPasswordStatus = findViewById(R.id.tvPasswordStatus);
        btnEnableFingerprint = findViewById(R.id.btnEnableFingerprint);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnBack = findViewById(R.id.btnBack);

        syncMasterPasswordFromFirebase(); // 🔁 sync on open

        btnEnableFingerprint.setOnClickListener(v -> enableFingerprintOnThisDevice());
        btnChangePassword.setOnClickListener(v -> changePassword());
        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncMasterPasswordFromFirebase(); // 🔁 sync again when returning
    }

    private String getUserKey(String baseKey) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return user.getUid() + "_" + baseKey;
        }
        return baseKey;
    }

    private void syncMasterPasswordFromFirebase() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ FIXED: Read from correct path - /users/{uid}/security/settings
        firestore.collection("users")
                .document(user.getUid())
                .collection("security")  // ✅ Added subcollection
                .document("settings")     // ✅ Added document
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String cloudPassword = documentSnapshot.getString("masterPassword");
                        Boolean setupComplete = documentSnapshot.getBoolean("securitySetupComplete");

                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        String localPassword = prefs.getString(getUserKey(MASTER_PASSWORD_KEY), null);

                        if (cloudPassword != null && !cloudPassword.isEmpty()) {
                            // Sync if different or not present locally
                            if (!cloudPassword.equals(localPassword)) {
                                prefs.edit()
                                        .putString(getUserKey(MASTER_PASSWORD_KEY), cloudPassword)
                                        .putBoolean(getUserKey(SECURITY_SETUP_COMPLETE),
                                                setupComplete != null ? setupComplete : true)
                                        .apply();

                                Toast.makeText(this, "🔄 Master password synced from cloud",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        android.util.Log.d("SecuritySettings", "No cloud security settings found");
                    }
                    updateSecurityStatus();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "⚠️ Failed to sync password: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    updateSecurityStatus();
                });
    }

    private void updateSecurityStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean setupComplete = prefs.getBoolean(getUserKey(SECURITY_SETUP_COMPLETE), false);
        String savedPassword = prefs.getString(getUserKey(MASTER_PASSWORD_KEY), null);

        if (!setupComplete || savedPassword == null) {
            tvSecurityStatus.setText("⚠️ Security not setup");
            tvPasswordStatus.setText("❌ No master password");
            tvBiometricStatus.setText("❌ Not available");
            btnEnableFingerprint.setEnabled(false);
            btnChangePassword.setEnabled(false);
            return;
        }

        tvPasswordStatus.setText("✅ Master password set");

        boolean biometricEnabled = prefs.getBoolean(getUserKey(BIOMETRIC_ENABLED_KEY), false);
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        if (biometricEnabled) {
            tvBiometricStatus.setText("✅ Fingerprint enabled on this device");
            tvSecurityStatus.setText("🔒 Security: Active");
            btnEnableFingerprint.setText("Disable Fingerprint");
            btnEnableFingerprint.setEnabled(true);
        } else {
            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                tvBiometricStatus.setText("⚠️ Fingerprint available but not enabled");
                btnEnableFingerprint.setText("Enable Fingerprint");
                btnEnableFingerprint.setEnabled(true);
            } else if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
                tvBiometricStatus.setText("❌ No fingerprint sensor on this device");
                btnEnableFingerprint.setEnabled(false);
            } else if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                tvBiometricStatus.setText("⚠️ No fingerprints enrolled in device settings");
                btnEnableFingerprint.setEnabled(false);
            } else {
                tvBiometricStatus.setText("❌ Fingerprint not available");
                btnEnableFingerprint.setEnabled(false);
            }
            tvSecurityStatus.setText("🔒 Security: Password Only");
        }

        btnChangePassword.setEnabled(true);
    }

    private void enableFingerprintOnThisDevice() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean biometricEnabled = prefs.getBoolean(getUserKey(BIOMETRIC_ENABLED_KEY), false);

        if (biometricEnabled) {
            showDisableFingerprintDialog();
        } else {
            showPasswordVerificationDialog(this::enrollFingerprint);
        }
    }

    private void showDisableFingerprintDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Disable Fingerprint?")
                .setMessage("You will need to use your master password to unlock notes on this device.")
                .setPositiveButton("Disable", (dialog, which) -> {
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putBoolean(getUserKey(BIOMETRIC_ENABLED_KEY), false).apply();
                    Toast.makeText(this, "Fingerprint disabled", Toast.LENGTH_SHORT).show();
                    updateSecurityStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPasswordVerificationDialog(Runnable onSuccess) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Verify Master Password");
        builder.setMessage("Enter your master password to enable fingerprint on this device");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Master Password");
        input.setPadding(50, 40, 50, 40);
        builder.setView(input);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String enteredPassword = input.getText().toString();
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String savedPassword = prefs.getString(getUserKey(MASTER_PASSWORD_KEY), null);

            if (enteredPassword.equals(savedPassword)) {
                Toast.makeText(this, "✅ Password verified!", Toast.LENGTH_SHORT).show();
                onSuccess.run();
            } else {
                Toast.makeText(this, "❌ Incorrect password", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void enrollFingerprint() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);

                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        prefs.edit().putBoolean(getUserKey(BIOMETRIC_ENABLED_KEY), true).apply();

                        Toast.makeText(SecuritySettingsActivity.this, "✅ Fingerprint enabled on this device!", Toast.LENGTH_LONG).show();
                        updateSecurityStatus();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(SecuritySettingsActivity.this, "Fingerprint not recognized. Try again.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(SecuritySettingsActivity.this, "Error: " + errString, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enroll Fingerprint")
                .setSubtitle("Scan your fingerprint to enable it on this device")
                .setDescription("This will allow you to unlock notes using your fingerprint")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void changePassword() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Master Password");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText oldPassword = new EditText(this);
        oldPassword.setHint("Current Password");
        oldPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final EditText newPassword = new EditText(this);
        newPassword.setHint("New Password");
        newPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final EditText confirmPassword = new EditText(this);
        confirmPassword.setHint("Confirm New Password");
        confirmPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(oldPassword);
        layout.addView(newPassword);
        layout.addView(confirmPassword);
        builder.setView(layout);

        builder.setPositiveButton("Change", (dialog, which) -> {
            String old = oldPassword.getText().toString();
            String newPass = newPassword.getText().toString();
            String confirm = confirmPassword.getText().toString();

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String savedPassword = prefs.getString(getUserKey(MASTER_PASSWORD_KEY), null);

            if (old.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!old.equals(savedPassword)) {
                Toast.makeText(this, "❌ Current password is incorrect", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPass.length() < 4) {
                Toast.makeText(this, "New password must be at least 4 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirm)) {
                Toast.makeText(this, "New passwords don't match", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                // ✅ FIXED: Save to correct path - /users/{uid}/security/settings
                Map<String, Object> securityData = new HashMap<>();
                securityData.put("masterPassword", newPass);
                securityData.put("securitySetupComplete", true);
                securityData.put("updatedAt", System.currentTimeMillis());

                firestore.collection("users")
                        .document(user.getUid())
                        .collection("security")  // ✅ Added subcollection
                        .document("settings")     // ✅ Added document
                        .set(securityData)
                        .addOnSuccessListener(aVoid -> {
                            // Also update local storage
                            prefs.edit()
                                    .putString(getUserKey(MASTER_PASSWORD_KEY), newPass)
                                    .putBoolean(getUserKey(SECURITY_SETUP_COMPLETE), true)
                                    .apply();

                            Toast.makeText(this, "✅ Password changed & synced to cloud!", Toast.LENGTH_LONG).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "❌ Failed to update password: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
            } else {
                Toast.makeText(this, "❌ User not logged in", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
