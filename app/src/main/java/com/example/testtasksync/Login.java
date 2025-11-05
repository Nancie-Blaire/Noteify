package com.example.testtasksync;

import android.content.ActivityNotFoundException;
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

public class Login extends AppCompatActivity {

    private static final String TAG = "Login";
    private static final int RC_SIGN_IN = 9001;

    private EditText etEmail, etPassword;
    private ImageView ivTogglePassword;
    private Button btnLogin, btnGoogleSignIn;
    private TextView tvSignUpRedirect, tvForgotPassword;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    private boolean isPasswordVisible = false;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

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
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvSignUpRedirect = findViewById(R.id.tvSignUpRedirect);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        progressBar = findViewById(R.id.progressBar);
        scrollView = findViewById(R.id.scrollView);

        if (etEmail == null || etPassword == null || btnLogin == null ||
                tvSignUpRedirect == null || tvForgotPassword == null || progressBar == null) {
            Log.e(TAG, "One or more view references are null. Check activity_login.xml IDs.");
        }

        final View emailContainer = (etEmail != null && etEmail.getParent() instanceof View) ? (View) etEmail.getParent() : null;
        final View passwordContainer = (etPassword != null && etPassword.getParent() instanceof View) ? (View) etPassword.getParent() : null;

        // Google Sign-In button
        if (btnGoogleSignIn != null) {
            btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        }

        // Setup password toggle
        if (ivTogglePassword != null && etPassword != null) {
            ivTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        }

        // Setup focus listeners
        if (etEmail != null) {
            etEmail.setOnFocusChangeListener((v, hasFocus) -> {
                if (emailContainer != null) {
                    scrollToView(v);
                    emailContainer.setBackgroundResource(hasFocus ? R.drawable.input_border_focused : R.drawable.input_border_default);
                }
            });
        }

        if (etPassword != null) {
            etPassword.setOnFocusChangeListener((v, hasFocus) -> {
                if (passwordContainer != null) {
                    scrollToView(v);
                    passwordContainer.setBackgroundResource(hasFocus ? R.drawable.input_border_focused : R.drawable.input_border_default);
                }
            });
        }

        // Email Login button
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                String email = etEmail != null ? etEmail.getText().toString().trim() : "";
                String password = etPassword != null ? etPassword.getText().toString().trim() : "";

                if (validateInput(email, password)) {
                    loginUser(email, password);
                }
            });
        }

        // Redirect to Sign Up
        if (tvSignUpRedirect != null) {
            tvSignUpRedirect.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Login.this, SignUp.class));
                } catch (ActivityNotFoundException ex) {
                    Log.e(TAG, "SignUp Activity not found: " + ex.getMessage(), ex);
                    Toast.makeText(Login.this, "SignUp activity missing", Toast.LENGTH_LONG).show();
                }
            });
        }

        // Forgot Password
        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> {
                String email = etEmail != null ? etEmail.getText().toString().trim() : "";

                if (email.isEmpty()) {
                    Toast.makeText(this, "Please enter your email first", Toast.LENGTH_SHORT).show();
                    if (etEmail != null) etEmail.requestFocus();
                    return;
                }

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
                    if (etEmail != null) etEmail.requestFocus();
                    return;
                }

                resetPassword(email);
            });
        }
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
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            saveGoogleUserToFirestore(user);
                        }
                    } else {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Toast.makeText(Login.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "signInWithCredential:failure", task.getException());
                    }
                });
    }

    private void saveGoogleUserToFirestore(FirebaseUser user) {
        // Check if user already exists
        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        // New user - save data
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("email", user.getEmail());
                        userData.put("displayName", user.getDisplayName());
                        userData.put("photoUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null);
                        userData.put("authProvider", "google");
                        userData.put("createdAt", System.currentTimeMillis());

                        db.collection("users").document(user.getUid())
                                .set(userData)
                                .addOnSuccessListener(aVoid -> navigateToMain())
                                .addOnFailureListener(e -> {
                                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                                    Toast.makeText(Login.this, "Failed to save user data", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        // Existing user - just login
                        navigateToMain();
                    }
                });
    }

    private void navigateToMain() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        Toast.makeText(Login.this, "Login successful!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(Login.this, MainActivity.class);
        try {
            startActivity(intent);
            finish();
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "MainActivity not found: " + ex.getMessage(), ex);
            Toast.makeText(Login.this, "MainActivity not found", Toast.LENGTH_LONG).show();
        }
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

    private boolean validateInput(String email, String password) {
        if (etEmail == null || etPassword == null) {
            Toast.makeText(this, "Unexpected error: views not initialized", Toast.LENGTH_LONG).show();
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

        return true;
    }

    private void loginUser(String email, String password) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (btnLogin != null) btnLogin.setEnabled(false);

        if (auth == null) {
            Toast.makeText(this, "Auth not initialized", Toast.LENGTH_LONG).show();
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (btnLogin != null) btnLogin.setEnabled(true);
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (btnLogin != null) btnLogin.setEnabled(true);

                    if (task.isSuccessful()) {
                        navigateToMain();
                    } else {
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Login failed";
                        Toast.makeText(Login.this, errorMessage, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Login failed", task.getException());
                    }
                });
    }

    private void resetPassword(String email) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (auth == null) {
            Toast.makeText(this, "Auth not initialized", Toast.LENGTH_LONG).show();
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            return;
        }

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Password reset email sent!", Toast.LENGTH_LONG).show();
                        Log.d(TAG, "Password reset email sent");
                    } else {
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Failed to send reset email";
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Password reset failed", task.getException());
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = auth != null ? auth.getCurrentUser() : null;
        if (currentUser != null) {
            Intent intent = new Intent(this, MainActivity.class);
            try {
                startActivity(intent);
                finish();
            } catch (ActivityNotFoundException ex) {
                Log.e(TAG, "MainActivity not found onStart: " + ex.getMessage(), ex);
                Toast.makeText(this, "MainActivity not found", Toast.LENGTH_LONG).show();
            }
        }
    }
}