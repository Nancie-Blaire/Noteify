package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private Button btnSignUp, btnGoogleSignIn, btnResendVerification;
    private TextView tvLoginRedirect, tvVerifyMessage;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private ScrollView scrollView;
    private View dividerLayout;

    private Handler verificationCheckHandler;
    private Runnable verificationCheckRunnable;
    private boolean isCheckingVerification = false;
    private String pendingFullName = "";
    private String pendingEmail = "";
    private long lastResendTime = 0;
    private static final long RESEND_COOLDOWN = 60000; // 60 seconds cooldown

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
        btnResendVerification = findViewById(R.id.btnResendVerification);
        tvLoginRedirect = findViewById(R.id.tvLoginRedirect);
        tvVerifyMessage = findViewById(R.id.tvVerifyMessage);
        progressBar = findViewById(R.id.progressBar);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword);
        scrollView = findViewById(R.id.scrollView);
        dividerLayout = findViewById(R.id.dividerLayout);

        // Handle back press with OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If currently checking verification, stop it and sign out the unverified user
                if (isCheckingVerification) {
                    stopVerificationCheck();
                    FirebaseUser currentUser = auth.getCurrentUser();
                    if (currentUser != null && !currentUser.isEmailVerified()) {
                        // Delete the unverified account
                        currentUser.delete().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Unverified account deleted");
                            }
                        });
                    }
                    auth.signOut();
                    // Restore the sign-up form
                    restoreSignUpForm();
                } else {
                    // Normal back press behavior - finish activity
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Get parent LinearLayouts
        View nameContainer = (View) etFullName.getParent();
        View emailContainer = (View) etEmail.getParent();
        View passwordContainer = (View) etPassword.getParent();
        View confirmContainer = (View) etConfirmPassword.getParent();

        // Google Sign-In button
        btnGoogleSignIn.setOnClickListener(v -> {
            // Stop verification check if switching to Google sign-in
            stopVerificationCheck();
            signInWithGoogle();
        });

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

        // Resend verification email button
        if (btnResendVerification != null) {
            btnResendVerification.setOnClickListener(v -> resendVerificationEmail());
        }

        // Redirect to Login
        tvLoginRedirect.setOnClickListener(v -> {
            stopVerificationCheck();
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
        // If there's an unverified user waiting, clean up first
        if (isCheckingVerification) {
            stopVerificationCheck();
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser != null && !currentUser.isEmailVerified()) {
                // Delete the unverified account before Google sign-in
                currentUser.delete().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Unverified account deleted before Google sign-in");
                    }
                    auth.signOut();
                    // Proceed with Google sign-in
                    Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                    startActivityForResult(signInIntent, RC_SIGN_IN);
                });
                return;
            }
        }

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
                // Delete unverified email account before proceeding with Google
                FirebaseUser currentUser = auth.getCurrentUser();
                if (currentUser != null && !currentUser.isEmailVerified()) {
                    stopVerificationCheck();
                    currentUser.delete().addOnCompleteListener(deleteTask -> {
                        if (deleteTask.isSuccessful()) {
                            Log.d(TAG, "Unverified email account deleted before Google sign-in");
                        }
                        // Now proceed with Google authentication
                        firebaseAuthWithGoogle(account.getIdToken());
                    });
                } else {
                    // No unverified account, proceed normally
                    firebaseAuthWithGoogle(account.getIdToken());
                }
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                // User cancelled or failed Google sign-in
                // Check if there's an unverified user and show verification UI
                FirebaseUser currentUser = auth.getCurrentUser();
                if (currentUser != null && !currentUser.isEmailVerified()) {
                    showVerificationUI();
                    if (!isCheckingVerification) {
                        startVerificationCheck(currentUser);
                    }
                }
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
        userData.put("emailVerified", true);
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

        // Check if email ends with @gmail.com
        if (!email.toLowerCase().endsWith("@gmail.com")) {
            etEmail.setError("Invalid Gmail account. Please use @gmail.com");
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
                            // Save the pending data
                            pendingFullName = fullName;
                            pendingEmail = email;
                            sendVerificationEmail(user);
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

    private void sendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        Log.d(TAG, "Verification email sent to " + user.getEmail());

                        // Show verification message and hide sign-up form
                        showVerificationUI();

                        // Start checking for email verification
                        startVerificationCheck(user);

                        Toast.makeText(SignUp.this,
                                "Verification email sent to " + user.getEmail(),
                                Toast.LENGTH_LONG).show();
                    } else {
                        btnSignUp.setEnabled(true);
                        Toast.makeText(SignUp.this,
                                "Failed to send verification email. Please try again.",
                                Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Failed to send verification email", task.getException());
                    }
                });
    }

    private void showVerificationUI() {
        // Hide sign-up form elements
        etFullName.setVisibility(View.GONE);
        etEmail.setVisibility(View.GONE);
        etPassword.setVisibility(View.GONE);
        etConfirmPassword.setVisibility(View.GONE);
        btnSignUp.setVisibility(View.GONE);

        // Hide parent containers
        ((View) etFullName.getParent()).setVisibility(View.GONE);
        ((View) etEmail.getParent()).setVisibility(View.GONE);
        ((View) etPassword.getParent()).setVisibility(View.GONE);
        ((View) etConfirmPassword.getParent()).setVisibility(View.GONE);

        // Show verification message and resend button
        tvVerifyMessage.setVisibility(View.VISIBLE);
        if (btnResendVerification != null) {
            btnResendVerification.setVisibility(View.VISIBLE);
        }

        // Keep divider and Google button visible
        if (dividerLayout != null) {
            dividerLayout.setVisibility(View.VISIBLE);
        }
        btnGoogleSignIn.setVisibility(View.VISIBLE);
    }

    private void startVerificationCheck(FirebaseUser user) {
        isCheckingVerification = true;
        verificationCheckHandler = new Handler(Looper.getMainLooper());

        // Store the pending data if not already stored
        if (pendingEmail.isEmpty() && user.getEmail() != null) {
            pendingEmail = user.getEmail();
        }

        verificationCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCheckingVerification) return;

                user.reload().addOnCompleteListener(reloadTask -> {
                    if (reloadTask.isSuccessful()) {
                        if (user.isEmailVerified()) {
                            // Email is verified, save to Firestore and proceed
                            stopVerificationCheck();
                            // Get display name from Firestore or use email
                            if (pendingFullName.isEmpty()) {
                                String email = user.getEmail();
                                pendingFullName = email != null && email.contains("@") ?
                                        email.split("@")[0] : "User";
                            }
                            saveUserToFirestore(user.getUid(), pendingFullName, pendingEmail);
                        } else {
                            // Check again after 3 seconds
                            if (isCheckingVerification) {
                                verificationCheckHandler.postDelayed(this, 3000);
                            }
                        }
                    } else {
                        // Error reloading user, try again
                        if (isCheckingVerification) {
                            verificationCheckHandler.postDelayed(this, 3000);
                        }
                    }
                });
            }
        };

        // Start the first check
        verificationCheckHandler.post(verificationCheckRunnable);
    }

    private void stopVerificationCheck() {
        isCheckingVerification = false;
        if (verificationCheckHandler != null && verificationCheckRunnable != null) {
            verificationCheckHandler.removeCallbacks(verificationCheckRunnable);
        }
    }

    private void resendVerificationEmail() {
        // Check cooldown period
        long currentTime = System.currentTimeMillis();
        long timeSinceLastResend = currentTime - lastResendTime;

        if (lastResendTime != 0 && timeSinceLastResend < RESEND_COOLDOWN) {
            long remainingSeconds = (RESEND_COOLDOWN - timeSinceLastResend) / 1000;
            Toast.makeText(SignUp.this,
                    "Please wait " + remainingSeconds + " seconds before resending",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            Toast.makeText(SignUp.this, "No user found. Please sign up again.", Toast.LENGTH_SHORT).show();
            restoreSignUpForm();
            return;
        }

        if (user.isEmailVerified()) {
            Toast.makeText(SignUp.this, "Email already verified!", Toast.LENGTH_SHORT).show();
            // Proceed to save user data
            saveUserToFirestore(user.getUid(), pendingFullName, pendingEmail);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        if (btnResendVerification != null) {
            btnResendVerification.setEnabled(false);
        }

        // Reload user first to ensure we have latest state
        user.reload().addOnCompleteListener(reloadTask -> {
            if (reloadTask.isSuccessful()) {
                user.sendEmailVerification()
                        .addOnCompleteListener(task -> {
                            progressBar.setVisibility(View.GONE);
                            if (btnResendVerification != null) {
                                btnResendVerification.setEnabled(true);
                            }

                            if (task.isSuccessful()) {
                                lastResendTime = System.currentTimeMillis();
                                Toast.makeText(SignUp.this,
                                        "Verification email sent to " + user.getEmail(),
                                        Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Verification email resent successfully");
                            } else {
                                String errorMsg = task.getException() != null ?
                                        task.getException().getMessage() : "Unknown error";

                                // Check if it's a rate limit error
                                if (errorMsg.contains("unusual activity") || errorMsg.contains("blocked")) {
                                    Toast.makeText(SignUp.this,
                                            "Too many requests. Please wait a few minutes and try again.",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(SignUp.this,
                                            "Failed to resend email: " + errorMsg,
                                            Toast.LENGTH_LONG).show();
                                }
                                Log.e(TAG, "Failed to resend verification email", task.getException());
                            }
                        });
            } else {
                progressBar.setVisibility(View.GONE);
                if (btnResendVerification != null) {
                    btnResendVerification.setEnabled(true);
                }
                Toast.makeText(SignUp.this,
                        "Failed to reload user. Please try again.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserToFirestore(String userId, String fullName, String email) {
        progressBar.setVisibility(View.VISIBLE);

        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("displayName", fullName);
        userData.put("photoUrl", null);
        userData.put("authProvider", "email");
        userData.put("emailVerified", true);
        userData.put("createdAt", System.currentTimeMillis());

        db.collection("users")
                .document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SignUp.this, "Email verified! Account created successfully!", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(SignUp.this, "Failed to save user data", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error saving user data", e);
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Only redirect if user is logged in AND email is verified
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else if (currentUser != null && !currentUser.isEmailVerified()) {
            // User exists but not verified - stay on this screen
            // Check if we should show verification UI
            if (!isCheckingVerification && tvVerifyMessage.getVisibility() != View.VISIBLE) {
                // Show verification UI if not already showing
                showVerificationUI();
                startVerificationCheck(currentUser);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check user state when resuming (e.g., coming back from Google sign-in)
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && !currentUser.isEmailVerified()) {
            // User is not verified, ensure verification UI is showing
            if (tvVerifyMessage.getVisibility() != View.VISIBLE) {
                showVerificationUI();
            }
            // Restart verification check if it was stopped
            if (!isCheckingVerification) {
                startVerificationCheck(currentUser);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVerificationCheck();
    }

    private void restoreSignUpForm() {
        // Show sign-up form elements
        ((View) etFullName.getParent()).setVisibility(View.VISIBLE);
        ((View) etEmail.getParent()).setVisibility(View.VISIBLE);
        ((View) etPassword.getParent()).setVisibility(View.VISIBLE);
        ((View) etConfirmPassword.getParent()).setVisibility(View.VISIBLE);
        etFullName.setVisibility(View.VISIBLE);
        etEmail.setVisibility(View.VISIBLE);
        etPassword.setVisibility(View.VISIBLE);
        etConfirmPassword.setVisibility(View.VISIBLE);
        btnSignUp.setVisibility(View.VISIBLE);
        btnSignUp.setEnabled(true);

        // Hide verification UI
        tvVerifyMessage.setVisibility(View.GONE);
        if (btnResendVerification != null) {
            btnResendVerification.setVisibility(View.GONE);
        }

        // Keep divider and Google button visible
        if (dividerLayout != null) {
            dividerLayout.setVisibility(View.VISIBLE);
        }
        btnGoogleSignIn.setVisibility(View.VISIBLE);

        // Clear fields
        etFullName.setText("");
        etEmail.setText("");
        etPassword.setText("");
        etConfirmPassword.setText("");
    }
}