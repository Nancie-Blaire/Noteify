package com.example.testtasksync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;
import android.text.method.PasswordTransformationMethod;
import android.graphics.Rect;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUp extends AppCompatActivity {

    private static final String TAG = "SignUp";
    private EditText etEmail, etPassword, etConfirmPassword;
    private ImageView ivTogglePassword, ivToggleConfirmPassword;
    private Button btnSignUp;
    private TextView tvLoginRedirect;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private ScrollView scrollView;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        //request adjustResize at runtime
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);


        // Initialize views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        tvLoginRedirect = findViewById(R.id.tvLoginRedirect);
        progressBar = findViewById(R.id.progressBar);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword);
        scrollView = findViewById(R.id.scrollView);


        // Get parent LinearLayouts
        View emailContainer = (View) etEmail.getParent();
        View passwordContainer = (View) etPassword.getParent();
        View confirmContainer = (View) etConfirmPassword.getParent();

        // Sign up button click
        btnSignUp.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (validateInput(email, password, confirmPassword)) {
                signUpUser(email, password);
            }
        });

        // Redirect to Login
        tvLoginRedirect.setOnClickListener(v -> {
            startActivity(new Intent(SignUp.this, Login.class));
            finish();
        });

        // Setup password toggle
        if (ivTogglePassword != null && etPassword != null) {
            ivTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        }

        // Setup password toggle
        if (ivToggleConfirmPassword != null && etConfirmPassword != null) {
            ivToggleConfirmPassword.setOnClickListener(v -> toggleConfirmPasswordVisibility());
        }

        // Setup focus listeners for Email
        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollToView(v);
                emailContainer.setBackgroundResource(R.drawable.input_border_focused);
            } else {
                emailContainer.setBackgroundResource(R.drawable.input_border_default);
            }
        });


        // Setup focus listeners for Password
        etPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollToView(v);
                passwordContainer.setBackgroundResource(R.drawable.input_border_focused);
            } else {
                passwordContainer.setBackgroundResource(R.drawable.input_border_default);
            }
        });

        etConfirmPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollToView(v);
                confirmContainer.setBackgroundResource(R.drawable.input_border_focused);
            } else {
                confirmContainer.setBackgroundResource(R.drawable.input_border_default);
            }
        });
    }

    private void togglePasswordVisibility() {
        if (etPassword == null || ivTogglePassword == null) return;

        if (isPasswordVisible) {
            // Hide password
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivTogglePassword.setImageResource(R.drawable.ic_password_eye_off);
            isPasswordVisible = false;
        } else {
            // Show password
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivTogglePassword.setImageResource(R.drawable.ic_password_eye_on);
            isPasswordVisible = true;
        }

        // Move cursor to end of text
        etPassword.setSelection(etPassword.getText().length());
    }

    private void toggleConfirmPasswordVisibility() {
        if (etConfirmPassword == null || ivToggleConfirmPassword == null) return;

        if (isConfirmPasswordVisible) {
            // Hide confirm password
            etConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivToggleConfirmPassword.setImageResource(R.drawable.ic_password_eye_off);
            isConfirmPasswordVisible = false;
        } else {
            // Show confirm password
            etConfirmPassword.setTransformationMethod(null);
            etConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivToggleConfirmPassword.setImageResource(R.drawable.ic_password_eye_on);
            isConfirmPasswordVisible = true;
        }

        // Move cursor to end of text
        etConfirmPassword.setSelection(etConfirmPassword.getText().length());
    }


    private void scrollToView(View view) {
        if (scrollView == null || view == null) return;

        scrollView.post(() -> {
            // Compute the rectangle of the view relative to the ScrollView
            Rect rect = new Rect();
            view.getDrawingRect(rect);
            scrollView.offsetDescendantRectToMyCoords(view, rect);

            // Optionally add a small offset so the field isn't flush to the keyboard
            int extraOffset = 32; // px, adjust as needed
            scrollView.smoothScrollTo(0, Math.max(0, rect.top - extraOffset));
        });
    }


    private boolean validateInput(String email, String password, String confirmPassword) {
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            etEmail.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return false;
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your password");
            etConfirmPassword.requestFocus();
            return false;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return false;
        }

        return true;
    }

    private void signUpUser(String email, String password) {
        progressBar.setVisibility(View.VISIBLE);
        btnSignUp.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            // Save user data to Firestore
                            saveUserToFirestore(user.getUid(), email);
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnSignUp.setEnabled(true);
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Sign up failed";
                        Toast.makeText(SignUp.this, errorMessage, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Sign up failed", task.getException());
                    }
                });
    }

    private void saveUserToFirestore(String userId, String email) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("createdAt", System.currentTimeMillis());

        db.collection("users")
                .document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SignUp.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "User data saved to Firestore");

                    // ✅ Mark that user has seen the biometric setup
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        android.content.SharedPreferences prefs = getSharedPreferences("NoteSecurityPrefs", MODE_PRIVATE);
                        prefs.edit().putBoolean(user.getUid() + "_has_seen_biometric_setup", true).apply();
                    }

                    // ✅ Redirect to BiometricSetupActivity for first-time setup only
                    Intent intent = new Intent(SignUp.this, BiometricSetupActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSignUp.setEnabled(true);
                    Toast.makeText(SignUp.this, "Failed to save user data", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error saving user data", e);
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already logged in
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            // ✅ Always go to Notes if already logged in
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }
}