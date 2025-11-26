package com.example.testtasksync;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class Account extends AppCompatActivity {

    private static final String TAG = "AccountActivity";

    private TextView btnLogout, btnEditProfile, tvChangePassword, tvDeleteAccount, tvSwitchAccount;
    private ImageView ivProfilePicture;
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
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        btnLogout = findViewById(R.id.btnLogout);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        tvChangePassword = findViewById(R.id.tvChangePassword);
        tvDeleteAccount = findViewById(R.id.tvDeleteAccount);
        tvSwitchAccount = findViewById(R.id.tvSwitchAccount);

        // Load user profile
        loadUserProfile();

        // Logout Button
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(Account.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Edit Profile Button
        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Account.this, EditProfileActivity.class);
            startActivity(intent);
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
                        String authProvider = documentSnapshot.getString("authProvider");
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

                        // Save account to AccountManager
                        accountManager.saveAccount(user.getEmail(), user.getUid(), displayName, photoUrl);
                    } else {
                        String email = user.getEmail();
                        if (email != null && email.contains("@")) {
                            tvUserName.setText(email.split("@")[0]);
                        } else {
                            tvUserName.setText("User");
                        }
                        showDefaultAvatar(tvUserName.getText().toString());

                        // Save account even without profile data
                        accountManager.saveAccount(user.getEmail(), user.getUid(),
                                tvUserName.getText().toString(), null);
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
     * Show dialog to switch between saved accounts
     */
    private void showAccountSwitchDialog() {
        List<AccountManager.SavedAccount> accounts = accountManager.getSavedAccounts();

        if (accounts.isEmpty()) {
            Toast.makeText(this, "No saved accounts found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Switch Account");

        // Inflate custom layout with RecyclerView
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_switch, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rvAccounts);
        TextView tvAddAccount = dialogView.findViewById(R.id.tvAddAccount);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        AccountSwitchAdapter adapter = new AccountSwitchAdapter(
                this,
                accounts,
                accountManager.getCurrentAccountEmail(),
                account -> {
                    // Handle account switch
                    switchToAccount(account);
                }
        );
        recyclerView.setAdapter(adapter);

        // Add Account button
        tvAddAccount.setOnClickListener(v -> {
            // Sign out and go to login
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Account.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        builder.setView(dialogView);
        builder.setNegativeButton("Cancel", null);
        builder.show();
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

        // Sign out current user
        auth.signOut();

        // Set this as current account
        accountManager.setCurrentAccount(account.getEmail());

        // Redirect to login with pre-filled email
        Intent intent = new Intent(Account.this, Login.class);
        intent.putExtra("SWITCH_ACCOUNT_EMAIL", account.getEmail());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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