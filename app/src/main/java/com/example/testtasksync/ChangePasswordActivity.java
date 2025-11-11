package com.example.testtasksync;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText etCurrentPassword, etNewPassword, etConfirmPassword;
    private Button btnUpdatePassword;
    private TextView btnCancel;
    private ImageView ivBack, ivToggleCurrentPassword, ivToggleNewPassword, ivToggleConfirmPassword;
    private FirebaseAuth auth;
    private ProgressDialog progressDialog;

    private boolean isCurrentPasswordVisible = false;
    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();

        // Initialize views
        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);
        btnCancel = findViewById(R.id.btnCancel);
        ivBack = findViewById(R.id.ivBack);
        ivToggleCurrentPassword = findViewById(R.id.ivToggleCurrentPassword);
        ivToggleNewPassword = findViewById(R.id.ivToggleNewPassword);
        ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword);

        // Progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Updating password...");
        progressDialog.setCancelable(false);

        // Back button
        ivBack.setOnClickListener(v -> finish());

        // Cancel button
        btnCancel.setOnClickListener(v -> finish());

        // Toggle password visibility
        ivToggleCurrentPassword.setOnClickListener(v -> togglePasswordVisibility(
                etCurrentPassword, ivToggleCurrentPassword, "current"
        ));

        ivToggleNewPassword.setOnClickListener(v -> togglePasswordVisibility(
                etNewPassword, ivToggleNewPassword, "new"
        ));

        ivToggleConfirmPassword.setOnClickListener(v -> togglePasswordVisibility(
                etConfirmPassword, ivToggleConfirmPassword, "confirm"
        ));

        // Update password button
        btnUpdatePassword.setOnClickListener(v -> validateAndUpdatePassword());
    }

    private void togglePasswordVisibility(EditText editText, ImageView imageView, String type) {
        boolean isVisible = false;

        switch (type) {
            case "current":
                isCurrentPasswordVisible = !isCurrentPasswordVisible;
                isVisible = isCurrentPasswordVisible;
                break;
            case "new":
                isNewPasswordVisible = !isNewPasswordVisible;
                isVisible = isNewPasswordVisible;
                break;
            case "confirm":
                isConfirmPasswordVisible = !isConfirmPasswordVisible;
                isVisible = isConfirmPasswordVisible;
                break;
        }

        if (isVisible) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            imageView.setImageResource(R.drawable.ic_password_eye_on);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            imageView.setImageResource(R.drawable.ic_password_eye_off);
        }

        // Move cursor to end
        editText.setSelection(editText.getText().length());
    }

    private void validateAndUpdatePassword() {
        String currentPassword = etCurrentPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validation
        if (currentPassword.isEmpty()) {
            etCurrentPassword.setError("Please enter your current password");
            etCurrentPassword.requestFocus();
            return;
        }

        if (newPassword.isEmpty()) {
            etNewPassword.setError("Please enter a new password");
            etNewPassword.requestFocus();
            return;
        }

        if (newPassword.length() < 6) {
            etNewPassword.setError("Password must be at least 6 characters");
            etNewPassword.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your new password");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        if (currentPassword.equals(newPassword)) {
            etNewPassword.setError("New password must be different from current password");
            etNewPassword.requestFocus();
            return;
        }

        // Proceed with password update
        updatePassword(currentPassword, newPassword);
    }

    private void updatePassword(String currentPassword, String newPassword) {
        progressDialog.show();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            progressDialog.dismiss();
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Re-authenticate user before changing password
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);

        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // Re-authentication successful, now update password
                    user.updatePassword(newPassword)
                            .addOnSuccessListener(aVoid1 -> {
                                progressDialog.dismiss();
                                Toast.makeText(ChangePasswordActivity.this,
                                        "Password updated successfully", Toast.LENGTH_LONG).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                progressDialog.dismiss();
                                Toast.makeText(ChangePasswordActivity.this,
                                        "Failed to update password: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    // Re-authentication failed - current password is wrong
                    etCurrentPassword.setError("Current password is incorrect");
                    etCurrentPassword.requestFocus();
                    Toast.makeText(ChangePasswordActivity.this,
                            "Current password is incorrect", Toast.LENGTH_SHORT).show();
                });
    }
}