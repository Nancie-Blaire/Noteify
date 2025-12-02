package com.example.testtasksync;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class Account extends AppCompatActivity {

    private static final String TAG = "AccountActivity";
    private static final int RC_GOOGLE_SIGN_IN_ADD_ACCOUNT = 9002;
    private static final String SECONDARY_APP_NAME = "SecondaryApp";

    private TextView btnLogout, btnEditProfile, tvChangePassword, tvDeleteAccount, tvSwitchAccount;
    private ImageView ivProfilePicture, ivBack;
    private TextView tvUserName, tvUserEmail;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private AccountManager accountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        accountManager = new AccountManager(this);

        // Initialize views
        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        ivBack = findViewById(R.id.ivBack);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        btnLogout = findViewById(R.id.btnLogout);
        tvChangePassword = findViewById(R.id.tvChangePassword);
        tvDeleteAccount = findViewById(R.id.tvDeleteAccount);
        tvSwitchAccount = findViewById(R.id.tvSwitchAccount);

        // Load user profile
        loadUserProfile();
        // Back Button
        ivBack.setOnClickListener(v -> finish());

        // Logout Button
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(Account.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Change Password Button
        tvChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(Account.this, ChangePasswordActivity.class);
            startActivity(intent);
        });

        // Delete Account Button
        tvDeleteAccount.setOnClickListener(v -> {
            showDeleteConfirmationDialog();
        });

        // Switch Account Button
        tvSwitchAccount.setOnClickListener(v -> {
            showAccountSwitchDialog();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GOOGLE_SIGN_IN_ADD_ACCOUNT) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account);
                }
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign in cancelled or failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadUserProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show email
        tvUserEmail.setText(user.getEmail());

        // Load profile data from Firestore
        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String displayName = documentSnapshot.getString("displayName");
                        String authProvider = documentSnapshot.getString("authProvider");  // ✅ Get from Firestore
                        String photoUrl = documentSnapshot.getString("photoUrl");

                        // Display name
                        if (displayName != null && !displayName.isEmpty()) {
                            tvUserName.setText(displayName);
                        } else {
                            String email = user.getEmail();
                            if (email != null && email.contains("@")) {
                                tvUserName.setText(email.split("@")[0]);
                            } else {
                                tvUserName.setText("User");
                            }
                        }

                        // Profile Picture
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            loadProfileImage(photoUrl);
                        } else {
                            showDefaultAvatar(displayName != null ? displayName : "User");
                        }

                        // ✅ Save account to AccountManager WITH authProvider
                        accountManager.saveAccount(user.getEmail(), user.getUid(), displayName, photoUrl, authProvider);
                    } else {
                        String email = user.getEmail();
                        if (email != null && email.contains("@")) {
                            tvUserName.setText(email.split("@")[0]);
                        } else {
                            tvUserName.setText("User");
                        }
                        showDefaultAvatar(tvUserName.getText().toString());

                        // ✅ Save account even without profile data - default to "email" provider
                        accountManager.saveAccount(user.getEmail(), user.getUid(),
                                tvUserName.getText().toString(), null, "email");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                    String email = user.getEmail();
                    if (email != null && email.contains("@")) {
                        tvUserName.setText(email.split("@")[0]);
                    } else {
                        tvUserName.setText("User");
                    }
                    showDefaultAvatar(tvUserName.getText().toString());

                    // ✅ Save account even on failure - default to "email" provider
                    accountManager.saveAccount(user.getEmail(), user.getUid(),
                            tvUserName.getText().toString(), null, "email");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                    String email = user.getEmail();
                    if (email != null && email.contains("@")) {
                        tvUserName.setText(email.split("@")[0]);
                    } else {
                        tvUserName.setText("User");
                    }
                    showDefaultAvatar(tvUserName.getText().toString());
                });
    }

    private void loadProfileImage(String photoUrl) {
        Glide.with(this)
                .load(photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_settings_account)
                .error(R.drawable.ic_settings_account)
                .into(ivProfilePicture);
    }

    private void showDefaultAvatar(String name) {
        ivProfilePicture.setImageResource(R.drawable.ic_settings_account);
    }

    /**
     * Get or create a secondary FirebaseAuth instance for testing credentials
     */
    private FirebaseAuth getSecondaryAuth() {
        try {
            FirebaseApp secondaryApp = FirebaseApp.getInstance(SECONDARY_APP_NAME);
            return FirebaseAuth.getInstance(secondaryApp);
        } catch (IllegalStateException e) {
            // Secondary app doesn't exist, create it
            FirebaseOptions primaryOptions = FirebaseApp.getInstance().getOptions();
            FirebaseOptions secondaryOptions = new FirebaseOptions.Builder()
                    .setApiKey(primaryOptions.getApiKey())
                    .setApplicationId(primaryOptions.getApplicationId())
                    .setProjectId(primaryOptions.getProjectId())
                    .build();

            FirebaseApp secondaryApp = FirebaseApp.initializeApp(this, secondaryOptions, SECONDARY_APP_NAME);
            return FirebaseAuth.getInstance(secondaryApp);
        }
    }

    /**
     * Show dialog to switch between saved accounts
     */
    private void showAccountSwitchDialog() {
        List<AccountManager.SavedAccount> allAccounts = accountManager.getSavedAccounts();

        // ✅ FILTER: Email accounts only
        List<AccountManager.SavedAccount> emailAccounts = new ArrayList<>();
        for (AccountManager.SavedAccount account : allAccounts) {
            if ("email".equals(account.getAuthProvider())) {
                emailAccounts.add(account);
            }
        }

        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_switch, null);

        RecyclerView recyclerView = dialogView.findViewById(R.id.rvAccounts);
        TextView tvAddAccount = dialogView.findViewById(R.id.tvAddAccount);
        Button btnGoogleSignIn = dialogView.findViewById(R.id.btnGoogleSignIn);  // ✅ CHANGED to Button
        TextView btnCancel = dialogView.findViewById(R.id.btnCancel);

        // Setup RecyclerView with FILTERED list
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        AccountSwitchAdapter adapter = new AccountSwitchAdapter(
                this,
                emailAccounts,  // ✅ Email accounts only
                accountManager.getCurrentAccountEmail(),
                account -> switchToAccount(account)
        );
        recyclerView.setAdapter(adapter);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // ✅ Google Sign-In Button
        btnGoogleSignIn.setOnClickListener(v -> {
            dialog.dismiss();
            showGoogleAccountPicker();
        });

        // Add Account button
        tvAddAccount.setOnClickListener(v -> {
            dialog.dismiss();
            showAddAccountBottomSheet();
        });

        // Cancel button
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Swipe-to-delete
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                AccountManager.SavedAccount accountToDelete = emailAccounts.get(position);
                showDeleteAccountConfirmationDialog(accountToDelete, position, adapter, emailAccounts);
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        dialog.show();
    }
    private void showGoogleAccountPicker() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Sign out to show account picker
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN_ADD_ACCOUNT);
        });
    }
    /**
     * Show confirmation dialog before deleting account from list
     */
    private void showDeleteAccountConfirmationDialog(AccountManager.SavedAccount account, int position,
                                                     AccountSwitchAdapter adapter, List<AccountManager.SavedAccount> accounts) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Account")
                .setMessage("Remove " + account.getEmail() + " from saved accounts?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    // Remove from AccountManager
                    accountManager.removeAccount(account.getEmail());

                    // Remove from list
                    accounts.remove(position);
                    adapter.notifyItemRemoved(position);

                    Toast.makeText(Account.this, "Account removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Restore the item in the adapter
                    adapter.notifyItemChanged(position);
                })
                .setOnCancelListener(dialog -> {
                    // Restore the item if dialog is cancelled
                    adapter.notifyItemChanged(position);
                })
                .show();
    }

    /**
     * Show bottom sheet for adding a new account
     */
    private void showAddAccountBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_account, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        // Initialize views
        EditText etBottomSheetEmail = bottomSheetView.findViewById(R.id.etBottomSheetEmail);
        EditText etBottomSheetPassword = bottomSheetView.findViewById(R.id.etBottomSheetPassword);
        ImageView ivBottomSheetTogglePassword = bottomSheetView.findViewById(R.id.ivBottomSheetTogglePassword);
        ImageView ivCloseBottomSheet = bottomSheetView.findViewById(R.id.ivCloseBottomSheet);
        Button btnBottomSheetSignIn = bottomSheetView.findViewById(R.id.btnBottomSheetSignIn);
        Button btnBottomSheetGoogleSignIn = bottomSheetView.findViewById(R.id.btnBottomSheetGoogleSignIn);
        ProgressBar progressBarBottomSheet = bottomSheetView.findViewById(R.id.progressBarBottomSheet);

        // Close button
        ivCloseBottomSheet.setOnClickListener(v -> bottomSheetDialog.dismiss());

        // Password visibility toggle
        final boolean[] isPasswordVisible = {false};
        ivBottomSheetTogglePassword.setOnClickListener(v -> {
            if (isPasswordVisible[0]) {
                etBottomSheetPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivBottomSheetTogglePassword.setImageResource(R.drawable.ic_password_eye_off);
                isPasswordVisible[0] = false;
            } else {
                etBottomSheetPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivBottomSheetTogglePassword.setImageResource(R.drawable.ic_password_eye_on);
                isPasswordVisible[0] = true;
            }
            etBottomSheetPassword.setSelection(etBottomSheetPassword.getText().length());
        });

        // Email/Password Sign In
        btnBottomSheetSignIn.setOnClickListener(v -> {
            String email = etBottomSheetEmail.getText().toString().trim();
            String password = etBottomSheetPassword.getText().toString().trim();

            // Validation
            if (email.isEmpty()) {
                etBottomSheetEmail.setError("Email is required");
                etBottomSheetEmail.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                etBottomSheetPassword.setError("Password is required");
                etBottomSheetPassword.requestFocus();
                return;
            }

            // Check if trying to add the same account
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser != null && email.equals(currentUser.getEmail())) {
                Toast.makeText(Account.this, "You are already signed in with this account", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show progress
            progressBarBottomSheet.setVisibility(View.VISIBLE);
            btnBottomSheetSignIn.setEnabled(false);
            btnBottomSheetGoogleSignIn.setEnabled(false);

            // Use secondary auth instance to verify credentials WITHOUT signing out the current user
            FirebaseAuth secondaryAuth = getSecondaryAuth();

            // Try to sign in with the new account using secondary auth instance
            secondaryAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        // Credentials are valid! Now we can safely switch accounts
                        // Get the user info before signing out from secondary
                        FirebaseUser newUser = authResult.getUser();

                        // Sign out from secondary instance
                        secondaryAuth.signOut();

                        // Now sign out from primary and sign in with the new account
                        auth.signOut();

                        auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener(authResult2 -> {
                                    if (newUser != null) {
                                        // Set as current account
                                        accountManager.setCurrentAccount(email);

                                        bottomSheetDialog.dismiss();
                                        Toast.makeText(Account.this,
                                                "Signed in as " + email,
                                                Toast.LENGTH_SHORT).show();

                                        // Reload the activity
                                        Intent intent = new Intent(Account.this, MainActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    // This shouldn't happen since we verified credentials, but handle it anyway
                                    progressBarBottomSheet.setVisibility(View.GONE);
                                    btnBottomSheetSignIn.setEnabled(true);
                                    btnBottomSheetGoogleSignIn.setEnabled(true);
                                    Toast.makeText(Account.this, "Failed to switch accounts", Toast.LENGTH_SHORT).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        // Invalid credentials - show error but DON'T affect current session
                        progressBarBottomSheet.setVisibility(View.GONE);
                        btnBottomSheetSignIn.setEnabled(true);
                        btnBottomSheetGoogleSignIn.setEnabled(true);

                        // Sign out from secondary instance to clean up
                        secondaryAuth.signOut();

                        String errorMessage = "Authentication failed";
                        if (e.getMessage() != null) {
                            if (e.getMessage().contains("password") || e.getMessage().contains("INVALID_LOGIN_CREDENTIALS")) {
                                errorMessage = "Incorrect email or password";
                            } else if (e.getMessage().contains("user-not-found")) {
                                errorMessage = "No account found with this email";
                            } else if (e.getMessage().contains("network")) {
                                errorMessage = "Network error. Please check your connection.";
                            } else if (e.getMessage().contains("invalid-email")) {
                                errorMessage = "Invalid email format";
                            }
                        }

                        Toast.makeText(Account.this, errorMessage, Toast.LENGTH_LONG).show();

                        // Current user remains logged in - no action needed!
                        Log.d(TAG, "Failed to add account, current user still logged in");
                    });
        });

        // Google Sign In
        btnBottomSheetGoogleSignIn.setOnClickListener(v -> {
            // Show progress
            progressBarBottomSheet.setVisibility(View.VISIBLE);
            btnBottomSheetSignIn.setEnabled(false);
            btnBottomSheetGoogleSignIn.setEnabled(false);

            // Configure Google Sign In
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();

            GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

            // Sign out from Google (not Firebase) to allow account selection
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN_ADD_ACCOUNT);
                bottomSheetDialog.dismiss();
            });
        });

        bottomSheetDialog.show();
    }

    /**
     * Helper method for Google Sign In authentication
     */
    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        // Check if trying to add the same account
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && account.getEmail().equals(currentUser.getEmail())) {
            Toast.makeText(this, "You are already signed in with this account", Toast.LENGTH_SHORT).show();
            return;
        }

        // Sign out current user and sign in with Google account
        auth.signOut();

        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser newUser = authResult.getUser();
                    if (newUser != null) {
                        accountManager.setCurrentAccount(newUser.getEmail());

                        Toast.makeText(Account.this,
                                "Signed in with Google",
                                Toast.LENGTH_SHORT).show();

                        // Reload the activity
                        Intent intent = new Intent(Account.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Account.this,
                            "Authentication failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Switch to a different saved account
     */
    private void switchToAccount(AccountManager.SavedAccount account) {
        FirebaseUser currentUser = auth.getCurrentUser();

        // Check if already on this account
        if (currentUser != null && currentUser.getEmail().equals(account.getEmail())) {
            Toast.makeText(this, "Already using this account", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ CHECK AUTH METHOD
        String authProvider = account.getAuthProvider();

        if ("google".equals(authProvider)) {
            // Google account - sign in directly without password
            switchToGoogleAccount(account);
        } else {
            // Email account - show password dialog
            showPasswordConfirmationDialog(account);
        }
    }
    private void switchToGoogleAccount(AccountManager.SavedAccount account) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

        // ✅ CHECK if already signed in to this Google account
        GoogleSignInAccount googleAccount = GoogleSignIn.getLastSignedInAccount(this);

        if (googleAccount != null && googleAccount.getEmail().equals(account.getEmail())) {
            // ✅ Direct sign-in - NO PICKER!
            Toast.makeText(this, "Switching to " + account.getEmail() + "...", Toast.LENGTH_SHORT).show();

            // Sign out from Firebase first
            auth.signOut();

            // Authenticate with Firebase using existing Google account
            firebaseAuthWithGoogle(googleAccount);
            return;
        }

        // Different account - show picker
        Toast.makeText(this, "Please select " + account.getEmail(), Toast.LENGTH_SHORT).show();

        auth.signOut();

        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN_ADD_ACCOUNT);
        });
    }
    /**
     * Show password confirmation dialog for account switching
     */
    private void showPasswordConfirmationDialog(AccountManager.SavedAccount account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_switch_password_confirm, null);

        // Get dialog views
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        EditText etDialogPassword = dialogView.findViewById(R.id.etDialogPassword);
        ImageView ivDialogTogglePassword = dialogView.findViewById(R.id.ivDialogTogglePassword);
        TextView tvDialogForgotPassword = dialogView.findViewById(R.id.tvDialogForgotPassword);
        Button btnDialogCancel = dialogView.findViewById(R.id.btnDialogCancel);
        Button btnDialogContinue = dialogView.findViewById(R.id.btnDialogContinue);

        // Set title with account name
        String displayName = account.getDisplayName() != null && !account.getDisplayName().isEmpty()
                ? account.getDisplayName()
                : account.getEmail().split("@")[0];
        tvDialogTitle.setText("Continue as " + displayName);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Password visibility toggle
        final boolean[] isPasswordVisible = {false};
        ivDialogTogglePassword.setOnClickListener(v -> {
            if (isPasswordVisible[0]) {
                etDialogPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivDialogTogglePassword.setImageResource(R.drawable.ic_password_eye_off);
                isPasswordVisible[0] = false;
            } else {
                etDialogPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivDialogTogglePassword.setImageResource(R.drawable.ic_password_eye_on);
                isPasswordVisible[0] = true;
            }
            etDialogPassword.setSelection(etDialogPassword.getText().length());
        });

        // Forgot password
        tvDialogForgotPassword.setOnClickListener(v -> {
            dialog.dismiss();
            // Send password reset email
            auth.sendPasswordResetEmail(account.getEmail())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(Account.this,
                                "Password reset email sent to " + account.getEmail(),
                                Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(Account.this,
                                "Failed to send reset email: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        });

        // Cancel button
        btnDialogCancel.setOnClickListener(v -> dialog.dismiss());

        // Continue button
        btnDialogContinue.setOnClickListener(v -> {
            String password = etDialogPassword.getText().toString().trim();

            if (password.isEmpty()) {
                etDialogPassword.setError("Password is required");
                etDialogPassword.requestFocus();
                return;
            }

            // Disable button and show progress
            btnDialogContinue.setEnabled(false);
            btnDialogContinue.setText("Signing in...");

            // Sign out current user first
            auth.signOut();

            // Sign in with the selected account
            auth.signInWithEmailAndPassword(account.getEmail(), password)
                    .addOnSuccessListener(authResult -> {
                        // Set as current account
                        accountManager.setCurrentAccount(account.getEmail());

                        dialog.dismiss();
                        Toast.makeText(Account.this,
                                "Switched to " + account.getEmail(),
                                Toast.LENGTH_SHORT).show();

                        // Reload the activity to show new account
                        Intent intent = new Intent(Account.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnDialogContinue.setEnabled(true);
                        btnDialogContinue.setText("CONTINUE");

                        String errorMessage = "Incorrect password";
                        if (e.getMessage() != null) {
                            if (e.getMessage().contains("network")) {
                                errorMessage = "Network error. Please check your connection.";
                            } else if (e.getMessage().contains("user-not-found")) {
                                errorMessage = "Account not found. Please sign up again.";
                            }
                        }

                        etDialogPassword.setError(errorMessage);
                        etDialogPassword.requestFocus();
                        Toast.makeText(Account.this, errorMessage, Toast.LENGTH_SHORT).show();
                    });
        });

        dialog.show();

        // Auto-focus on password field
        etDialogPassword.requestFocus();
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to permanently delete your account? All your data will be lost.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        deleteUserAccount();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteUserAccount() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userEmail = user.getEmail();

        // Delete user data from Firestore first
        db.collection("users").document(user.getUid())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User data successfully deleted from Firestore!");

                    // Delete from AccountManager
                    accountManager.removeAccount(userEmail);

                    // Delete the Firebase Auth user account
                    user.delete()
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Log.d(TAG, "User account deleted.");
                                    Toast.makeText(Account.this, "Account deleted successfully.", Toast.LENGTH_LONG).show();

                                    Intent intent = new Intent(Account.this, Login.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Log.w(TAG, "Failed to delete user account: " + task.getException());
                                    Toast.makeText(Account.this, "Failed to delete account. Please log out and log in again, then try deleting.", Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error deleting user data from Firestore: " + e.getMessage());
                    Toast.makeText(Account.this, "Failed to delete user data. Please try again.", Toast.LENGTH_SHORT).show();
                });
    }
}