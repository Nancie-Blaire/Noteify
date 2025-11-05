package com.example.testtasksync;

import android.content.Intent;
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
import android.graphics.Rect;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUp extends AppCompatActivity {

    private static final String TAG = "SignUp";
    private static final int RC_SIGN_IN = 9001;

    private EditText etFullName, etEmail, etPassword, etConfirmPassword;
    private ImageView ivTogglePassword, ivToggleConfirmPassword;
    private Button btnSignUp, btnGoogleSignIn;
    private TextView tvLoginRedirect;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
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

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Initialize views
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvLoginRedirect = findViewById(R.id.tvLoginRedirect);
        progressBar = findViewById(R.id.progressBar);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword);
        scrollView = findViewById(R.id.scrollView);

        // Get parent LinearLayouts
        View nameContainer = (View) etFullName.getParent();
        View emailContainer = (View) etEmail.getParent();
        View passwordContainer = (View) etPassword.getParent();
        View confirmContainer = (View) etConfirmPassword.getParent();

        // Google Sign-In button
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());

        // Email Sign up button
        btnSignUp.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (validateInput(fullName, email, password, confirmPassword)) {
                signUpUser(fullName, email, password);
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

        if (ivToggleConfirmPassword != null && etConfirmPassword != null) {
            ivToggleConfirmPassword.setOnClickListener(v -> toggleConfirmPasswordVisibility());
        }

        // Setup focus listeners
        etFullName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollToView(v);
                nameContainer.setBackgroundResource(R.drawable.input_border_focused);
            } else {
                nameContainer.setBackgroundResource(R.drawable.input_border_default);
            }
        });

        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollToView(v);
                emailContainer.setBackgroundResource(R.drawable.input_border_focused);
            } else {
                emailContainer.setBackgroundResource(R.drawable.input_border_default);
            }
        });

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

    // Google Sign-In
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        progressBar.setVisibility(View.VISIBLE);

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            saveGoogleUserToFirestore(user);
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(SignUp.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "signInWithCredential:failure", task.getException());
                    }
                });
    }

    private void saveGoogleUserToFirestore(FirebaseUser user) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", user.getEmail());
        userData.put("displayName", user.getDisplayName());
        userData.put("photoUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null);
        userData.put("authProvider", "google");
        userData.put("createdAt", System.currentTimeMillis());

        db.collection("users")
                .document(user.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SignUp.this, "Google Sign-In Successful!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Google user data saved");

                    // Mark biometric setup as seen
                    android.content.SharedPreferences prefs = getSharedPreferences("NoteSecurityPrefs", MODE_PRIVATE);
                    prefs.edit().putBoolean(user.getUid() + "_has_seen_biometric_setup", true).apply();

                    Intent intent = new Intent(SignUp.this, BiometricSetupActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SignUp.this, "Failed to save user data", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error saving Google user data", e);
                });
    }

    private void togglePasswordVisibility() {
        if (etPassword == null || ivTogglePassword == null) return;

        if (isPasswordVisible) {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivTogglePassword.setImageResource(R.drawable.ic_password_eye_off);
            isPasswordVisible = false;
        } else {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivTogglePassword.setImageResource(R.drawable.ic_password_eye_on);
            isPasswordVisible = true;
        }
        etPassword.setSelection(etPassword.getText().length());
    }

    private void toggleConfirmPasswordVisibility() {
        if (etConfirmPassword == null || ivToggleConfirmPassword == null) return;

        if (isConfirmPasswordVisible) {
            etConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivToggleConfirmPassword.setImageResource(R.drawable.ic_password_eye_off);
            isConfirmPasswordVisible = false;
        } else {
            etConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivToggleConfirmPassword.setImageResource(R.drawable.ic_password_eye_on);
            isConfirmPasswordVisible = true;
        }
        etConfirmPassword.setSelection(etConfirmPassword.getText().length());
    }

    private void scrollToView(View view) {
        if (scrollView == null || view == null) return;

        scrollView.post(() -> {
            Rect rect = new Rect();
            view.getDrawingRect(rect);
            scrollView.offsetDescendantRectToMyCoords(view, rect);
            int extraOffset = 32;
            scrollView.smoothScrollTo(0, Math.max(0, rect.top - extraOffset));
        });
    }

    private boolean validateInput(String fullName, String email, String password, String confirmPassword) {
        if (fullName.isEmpty()) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return false;
        }

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

    private void signUpUser(String fullName, String email, String password) {
        progressBar.setVisibility(View.VISIBLE);
        btnSignUp.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            saveUserToFirestore(user.getUid(), fullName, email);
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

    private void saveUserToFirestore(String userId, String fullName, String email) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("displayName", fullName);
        userData.put("photoUrl", null);
        userData.put("authProvider", "email");
        userData.put("createdAt", System.currentTimeMillis());

        db.collection("users")
                .document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SignUp.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "User data saved to Firestore");

                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        android.content.SharedPreferences prefs = getSharedPreferences("NoteSecurityPrefs", MODE_PRIVATE);
                        prefs.edit().putBoolean(user.getUid() + "_has_seen_biometric_setup", true).apply();
                    }

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
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }
}