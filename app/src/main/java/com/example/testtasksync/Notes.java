package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class Notes extends Fragment {

    private static final String TAG = "NotesFragment";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private List<Note> noteList;
    private List<Note> starredNoteList;
    private NoteAdapter adapter;
    private NoteAdapter starredAdapter;

    // RecyclerViews
    private RecyclerView prioNotesRecyclerView;
    private RecyclerView notesRecyclerView;

    // UI Elements
    private SearchView searchView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the notes layout
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.d(TAG, "onViewCreated started");

        // Initialize RecyclerViews
        prioNotesRecyclerView = view.findViewById(R.id.prioNotesRecyclerView);
        notesRecyclerView = view.findViewById(R.id.notesRecyclerView);

        // Set layout managers
        prioNotesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        notesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();

        // Initialize search bar
        searchView = view.findViewById(R.id.searchBar);
        searchView.clearFocus();

        searchView.setOnClickListener(v -> {
            searchView.setFocusable(true);
            searchView.setFocusableInTouchMode(true);
            searchView.requestFocus();
        });

        // Check if user is logged in
        if (user == null) {
            startActivity(new Intent(getContext(), Login.class));
            requireActivity().finish();
            return;
        }

        // Initialize note lists
        noteList = new ArrayList<>();
        starredNoteList = new ArrayList<>();

        // Adapter for regular notes
        adapter = new NoteAdapter(noteList, note -> {
            Intent intent = new Intent(getContext(), NoteActivity.class);
            intent.putExtra("noteId", note.getId());
            startActivity(intent);
        });

        // Adapter for starred notes
        starredAdapter = new NoteAdapter(starredNoteList, note -> {
            Intent intent = new Intent(getContext(), NoteActivity.class);
            intent.putExtra("noteId", note.getId());
            startActivity(intent);
        }, true);

        // Firebase listener - load notes from Firestore
        if (user != null) {
            db.collection("users")
                    .document(user.getUid())
                    .collection("notes")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .addSnapshotListener((snapshots, e) -> {
                        if (e != null) {
                            Log.w(TAG, "Listen failed.", e);
                            return;
                        }

                        noteList.clear();
                        starredNoteList.clear();

                        if (snapshots != null) {
                            for (QueryDocumentSnapshot doc : snapshots) {
                                Note note = new Note(
                                        doc.getId(),
                                        doc.getString("title"),
                                        doc.getString("content")
                                );

                                // Get starred state from Firebase
                                Boolean isStarred = doc.getBoolean("isStarred");
                                if (isStarred != null && isStarred) {
                                    note.setStarred(true);
                                    starredNoteList.add(note);
                                }

                                // Get locked state from Firebase
                                Boolean isLocked = doc.getBoolean("isLocked");
                                if (isLocked != null && isLocked) {
                                    note.setLocked(true);
                                }

                                // Add all notes to regular list
                                noteList.add(note);
                            }
                        }

                        // Update UI based on note availability
                        updateUI();
                    });
        }
    }

    /**
     * Update UI based on available notes
     */
    private void updateUI() {
        // Handle Starred/Prios Section
        if (starredNoteList.isEmpty()) {
            Log.d(TAG, "No starred notes - showing welcome card in prio section");
            defaultCardAdapter prioWelcomeAdapter = new defaultCardAdapter(true);
            prioNotesRecyclerView.setAdapter(prioWelcomeAdapter);
        } else {
            Log.d(TAG, "Starred notes found: " + starredNoteList.size());
            prioNotesRecyclerView.setAdapter(starredAdapter);
            starredAdapter.notifyDataSetChanged();
        }

        // Handle Recent Section
        if (noteList.isEmpty()) {
            Log.d(TAG, "No regular notes - showing welcome card in recent section");
            defaultCardAdapter recentWelcomeAdapter = new defaultCardAdapter(false);
            notesRecyclerView.setAdapter(recentWelcomeAdapter);
        } else {
            Log.d(TAG, "Regular notes found: " + noteList.size());
            notesRecyclerView.setAdapter(adapter);
            adapter.notifyDataSetChanged();
        }
    }
}