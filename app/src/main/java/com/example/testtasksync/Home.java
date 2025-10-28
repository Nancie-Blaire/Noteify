package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Home extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private FloatingActionButton fabMain, fabNote, fabTodo, fabWeekly;
    private boolean isFabOpen = false;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Log.d(TAG, "=== HOME ACTIVITY STARTED ===");
        Toast.makeText(this, "Home Activity Started", Toast.LENGTH_SHORT).show();

        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        Log.d(TAG, "User logged in: " + user.getEmail());

        fabMain = findViewById(R.id.fabMain);
        fabNote = findViewById(R.id.fabNote);
        fabTodo = findViewById(R.id.fabTodo);
        fabWeekly = findViewById(R.id.fabWeekly);

        Log.d(TAG, "fabMain: " + (fabMain != null));
        Log.d(TAG, "fabNote: " + (fabNote != null));
        Log.d(TAG, "fabTodo: " + (fabTodo != null));
        Log.d(TAG, "fabWeekly: " + (fabWeekly != null));

        if (fabMain == null || fabNote == null || fabTodo == null || fabWeekly == null) {
            Toast.makeText(this, "ERROR: FABs not found in Home!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Some FABs are NULL");
            return;
        }

        fabNote.hide();
        fabTodo.hide();
        fabWeekly.hide();

        Log.d(TAG, "Setting up click listeners");

        fabMain.setOnClickListener(v -> {
            Log.d(TAG, "======= FAB MAIN CLICKED =======");
            Toast.makeText(this, "Main FAB Clicked!", Toast.LENGTH_SHORT).show();
            toggleFabMenu();
        });

        fabNote.setOnClickListener(v -> {
            Log.d(TAG, "FAB Note clicked - navigating to Notes activity");
            Toast.makeText(this, "Opening Notes", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Home.this, Notes.class);
            startActivity(intent);
            toggleFabMenu();
        });

        fabTodo.setOnClickListener(v -> {
            Log.d(TAG, "FAB Todo clicked");
            Toast.makeText(this, "To-do template coming soon!", Toast.LENGTH_SHORT).show();
            toggleFabMenu();
        });

        fabWeekly.setOnClickListener(v -> {
            Log.d(TAG, "FAB Weekly clicked");
            Toast.makeText(this, "Weekly planner coming soon!", Toast.LENGTH_SHORT).show();
            toggleFabMenu();
        });

        Log.d(TAG, "=== HOME ACTIVITY SETUP COMPLETE ===");
    }

    private void toggleFabMenu() {
        Log.d(TAG, "toggleFabMenu() - Current state: " + (isFabOpen ? "OPEN" : "CLOSED"));

        if (isFabOpen) {
            Log.d(TAG, "Hiding FABs");
            fabNote.hide();
            fabTodo.hide();
            fabWeekly.hide();
            Toast.makeText(this, "Menu Closed", Toast.LENGTH_SHORT).show();
        } else {
            Log.d(TAG, "Showing FABs");
            fabNote.show();
            fabTodo.show();
            fabWeekly.show();
            Toast.makeText(this, "Menu Opened", Toast.LENGTH_SHORT).show();
        }
        isFabOpen = !isFabOpen;

        Log.d(TAG, "New state: " + (isFabOpen ? "OPEN" : "CLOSED"));
    }
}