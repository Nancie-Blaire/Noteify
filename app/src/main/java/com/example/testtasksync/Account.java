package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class Account extends AppCompatActivity {

    private TextView btnLogout, btnEditProfile;
    private ImageView ivProfilePicture;
    private TextView tvUserName, tvUserEmail;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

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

        // Initialize views
        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        btnLogout = findViewById(R.id.btnLogout);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        // Load user profile
        loadUserProfile();

        // Logout Button
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Account.this, Login.class);
            startActivity(intent);
            finish();
        });

        // Edit Profile Button
        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Account.this, EditProfileActivity.class);
            startActivity(intent);
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

        // Show email (always available)
        tvUserEmail.setText(user.getEmail());

        // Load profile data from Firestore
        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Get display name
                        String displayName = documentSnapshot.getString("displayName");
                        String authProvider = documentSnapshot.getString("authProvider");
                        String photoUrl = documentSnapshot.getString("photoUrl");

                        // Display name
                        if (displayName != null && !displayName.isEmpty()) {
                            tvUserName.setText(displayName);
                        } else {
                            // Fallback: Use email username
                            String email = user.getEmail();
                            if (email != null && email.contains("@")) {
                                tvUserName.setText(email.split("@")[0]);
                            } else {
                                tvUserName.setText("User");
                            }
                        }

                        // Profile Picture
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            // Load Google profile picture with Glide
                            loadProfileImage(photoUrl);
                        } else {
                            // Show default avatar
                            showDefaultAvatar(displayName != null ? displayName : "User");
                        }
                    } else {
                        // No profile data - show defaults
                        String email = user.getEmail();
                        if (email != null && email.contains("@")) {
                            tvUserName.setText(email.split("@")[0]);
                        } else {
                            tvUserName.setText("User");
                        }
                        showDefaultAvatar(tvUserName.getText().toString());
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
        // Load image with Glide (circular crop)
        Glide.with(this)
                .load(photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_settings_account)
                .error(R.drawable.ic_settings_account)
                .into(ivProfilePicture);
    }

    private void showDefaultAvatar(String name) {
        // Show default icon
        // TODO: You can create a custom drawable with first letter + colored circle
        // For now, just show default icon
        ivProfilePicture.setImageResource(R.drawable.ic_settings_account);

        /* Optional: Create colored circle with initials
        String firstLetter = name.substring(0, 1).toUpperCase();
        // Use a library like "Android-Letter-Avatar" or create custom drawable
        */
    }
}