package com.example.testtasksync;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private List<Note> noteList;
    private OnNoteClickListener listener;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private boolean isGridLayout; // NEW: Flag to determine layout type

    // ðŸ”¹ Interface for handling clicks
    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    // Updated constructor with layout type
    public NoteAdapter(List<Note> noteList, OnNoteClickListener listener, boolean isGridLayout) {
        this.noteList = noteList;
        this.listener = listener;
        this.isGridLayout = isGridLayout;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    // Keep old constructor for backward compatibility
    public NoteAdapter(List<Note> noteList, OnNoteClickListener listener) {
        this(noteList, listener, false); // Default to list layout
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use different layout based on isGridLayout flag
        int layoutId = isGridLayout ? R.layout.prio_grid_layout : R.layout.item_note;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = noteList.get(position);
        holder.bind(note, listener, db, auth);
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteTitle, noteContent;
        ImageView starIcon;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteTitle = itemView.findViewById(R.id.noteTitle);
            noteContent = itemView.findViewById(R.id.noteContent);
            starIcon = itemView.findViewById(R.id.starIcon);
        }

        public void bind(Note note, OnNoteClickListener listener, FirebaseFirestore db, FirebaseAuth auth) {
            noteTitle.setText(note.getTitle());
            noteContent.setText(note.getContent());

            // Set initial star state
            updateStarIcon(note.isStarred());

            // Handle star click
            starIcon.setOnClickListener(v -> {
                // Toggle the star state
                boolean newStarState = !note.isStarred();
                note.setStarred(newStarState);

                // Update icon immediately
                updateStarIcon(newStarState);

                // Save to Firebase
                updateStarInFirebase(note, db, auth);
            });

            // Handle card click
            itemView.setOnClickListener(v -> listener.onNoteClick(note));
        }

        private void updateStarIcon(boolean isStarred) {
            if (isStarred) {
                starIcon.setImageResource(R.drawable.ic_star);
            } else {
                starIcon.setImageResource(R.drawable.ic_star_outline);
            }
        }

        private void updateStarInFirebase(Note note, FirebaseFirestore db, FirebaseAuth auth) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                db.collection("users")
                        .document(user.getUid())
                        .collection("notes")
                        .document(note.getId())
                        .update("isStarred", note.isStarred())
                        .addOnSuccessListener(aVoid -> {
                            Log.d("NoteAdapter", "Star state updated successfully");
                        })
                        .addOnFailureListener(e -> {
                            Log.e("NoteAdapter", "Failed to update star state", e);
                        });
            }
        }

    }
}