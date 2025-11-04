package com.example.testtasksync;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.concurrent.Executor;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private List<Note> noteList;
    private OnNoteClickListener listener;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private boolean isGridLayout;
    private static final String PREFS_NAME = "NoteSecurityPrefs";
    private static final String MASTER_PASSWORD_KEY = "master_password";
    private static final String SECURITY_SETUP_COMPLETE = "security_setup_complete";

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    public NoteAdapter(List<Note> noteList, OnNoteClickListener listener, boolean isGridLayout) {
        this.noteList = noteList;
        this.listener = listener;
        this.isGridLayout = isGridLayout;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    public NoteAdapter(List<Note> noteList, OnNoteClickListener listener) {
        this(noteList, listener, false);
    }

    public void updateNote(Note note) {
        for (int i = 0; i < noteList.size(); i++) {
            if (noteList.get(i).getId().equals(note.getId())) {
                noteList.set(i, note);
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isGridLayout ? R.layout.prio_grid_layout : R.layout.item_note;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = noteList.get(position);
        holder.bind(note, listener, db, auth, isGridLayout, noteList, this);
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteTitle, noteContent;
        ImageView starIcon, menuButton, lockIcon, LockIcon;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteTitle = itemView.findViewById(R.id.noteTitle);
            noteContent = itemView.findViewById(R.id.noteContent);
            starIcon = itemView.findViewById(R.id.starIcon);
            menuButton = itemView.findViewById(R.id.noteMenuButton);
            lockIcon = itemView.findViewById(R.id.lockIcon);
        }

        public void bind(Note note, OnNoteClickListener listener, FirebaseFirestore db,
                         FirebaseAuth auth, boolean isGridLayout, List<Note> noteList,
                         NoteAdapter adapter) {
            noteTitle.setText(note.getTitle());

            // Show lock icon if note is locked - ONLY for grid (Prios)
            ImageView lockIcon = itemView.findViewById(R.id.lockIcon);
            if (lockIcon != null) {
                lockIcon.setVisibility(note.isLocked() ? View.VISIBLE : View.GONE);
            }

            // Adjust title & content margin based on lock state - ONLY for list view (Recently Open)
            if (!isGridLayout && lockIcon != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) noteTitle.getLayoutParams();
                if (note.isLocked()) {
                    // Add space for lock icon
                    params.setMarginStart((int) (32 * noteTitle.getContext().getResources().getDisplayMetrics().density)); // 32dp
                    params.setMarginStart((int) (32 * noteContent.getContext().getResources().getDisplayMetrics().density));
                } else {
                    // Normal padding when not locked
                    params.setMarginStart((int) (1 * noteTitle.getContext().getResources().getDisplayMetrics().density));
                    params.setMarginStart((int) (1 * noteContent.getContext().getResources().getDisplayMetrics().density));// 1dp
                }
                noteTitle.setLayoutParams(params);
                noteContent.setLayoutParams(params);
            }

            // Show locked content differently
            if (note.isLocked()) {
                noteContent.setText(" Locked");
            } else {
                noteContent.setText(note.getContent());
            }

            // Set initial star state
            updateStarIcon(note.isStarred());

            // Handle star click
            starIcon.setOnClickListener(v -> {
                boolean newStarState = !note.isStarred();
                note.setStarred(newStarState);
                updateStarIcon(newStarState);
                updateStarInFirebase(note, db, auth);
            });

            // Handle three dots menu click
            if (menuButton != null) {
                menuButton.setOnClickListener(v -> {
                    showPopupMenu(v, note, db, auth, noteList, adapter);
                });
            }

            // Handle card click - check if locked
            itemView.setOnClickListener(v -> {
                if (note.isLocked()) {
                    authenticateAndOpen(v.getContext(), note, listener, auth);
                } else {
                    listener.onNoteClick(note);
                }
            });
        }

        // Helper method to get user-specific preference key
        private String getUserKey(Context context, FirebaseAuth auth, String baseKey) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                return user.getUid() + "_" + baseKey;
            }
            return baseKey; // Fallback
        }

        private boolean isSecuritySetupComplete(Context context, FirebaseAuth auth) {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Log.d("NoteAdapter", "No user logged in");
                return false;
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String userKey = getUserKey(context, auth, SECURITY_SETUP_COMPLETE);
            String passwordKey = getUserKey(context, auth, MASTER_PASSWORD_KEY);

            boolean setupComplete = prefs.getBoolean(userKey, false);
            String masterPassword = prefs.getString(passwordKey, null);

            Log.d("NoteAdapter", "Security Check - User: " + user.getUid());
            Log.d("NoteAdapter", "Setup Complete Key: " + userKey + " = " + setupComplete);
            Log.d("NoteAdapter", "Password Key: " + passwordKey + " = " + (masterPassword != null ? "EXISTS" : "NULL"));

            // Setup is complete only if BOTH flag is true AND password exists
            boolean isComplete = setupComplete && masterPassword != null && !masterPassword.isEmpty();
            Log.d("NoteAdapter", "Final Result: " + isComplete);

            return isComplete;
        }

        private void redirectToSecuritySetup(Context context) {
            Toast.makeText(context, "Please set up security first to lock notes", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(context, BiometricSetupActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }

        private void authenticateAndOpen(Context context, Note note, OnNoteClickListener listener, FirebaseAuth auth) {
            // Check if security setup is complete FOR THIS USER
            if (!isSecuritySetupComplete(context, auth)) {
                redirectToSecuritySetup(context);
                return;
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedPassword = prefs.getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), null);

            if (savedPassword == null) {
                // This shouldn't happen if setup is complete, but just in case
                redirectToSecuritySetup(context);
                return;
            }

            // Try biometric first
            if (isBiometricAvailable(context)) {
                showBiometricPrompt(context, note, listener, auth);
            } else {
                // Fallback to password
                showPasswordDialog(context, note, listener, savedPassword);
            }
        }

        private boolean isBiometricAvailable(Context context) {
            BiometricManager biometricManager = BiometricManager.from(context);
            int canAuthenticate = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG |
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
        }

        private void showBiometricPrompt(Context context, Note note, OnNoteClickListener listener, FirebaseAuth auth) {
            if (!(context instanceof FragmentActivity)) {
                showPasswordDialog(context, note, listener,
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), ""));
                return;
            }

            FragmentActivity activity = (FragmentActivity) context;
            Executor executor = ContextCompat.getMainExecutor(context);

            BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            Toast.makeText(context, "✓ Authentication successful", Toast.LENGTH_SHORT).show();
                            listener.onNoteClick(note);
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Toast.makeText(context, "✗ Authentication failed", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            // Fallback to password
                            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                            String savedPassword = prefs.getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), "");
                            showPasswordDialog(context, note, listener, savedPassword);
                        }
                    });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("🔓 Unlock Note")
                    .setSubtitle("Use your fingerprint to access this locked note")
                    .setNegativeButtonText("Use Password")
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }

        private void showPasswordDialog(Context context, Note note, OnNoteClickListener listener,
                                        String savedPassword) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("🔐 Enter Master Password");

            final EditText input = new EditText(context);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint("Master Password");
            builder.setView(input);

            builder.setPositiveButton("Unlock", (dialog, which) -> {
                String enteredPassword = input.getText().toString();
                if (enteredPassword.equals(savedPassword)) {
                    Toast.makeText(context, "✓ Unlocked!", Toast.LENGTH_SHORT).show();
                    listener.onNoteClick(note);
                } else {
                    Toast.makeText(context, "✗ Incorrect password", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        }

        private void showPopupMenu(View view, Note note, FirebaseFirestore db, FirebaseAuth auth,
                                   List<Note> noteList, NoteAdapter adapter) {
            PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
            popupMenu.getMenuInflater().inflate(R.menu.note_menu, popupMenu.getMenu());

            // Update lock menu item text based on current state
            android.view.MenuItem lockItem = popupMenu.getMenu().findItem(R.id.menu_lock);
            if (lockItem != null) {
                lockItem.setTitle(note.isLocked() ? " Unlock" : " Lock");
            }

            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();

                if (itemId == R.id.menu_delete) {
                    deleteNote(note, db, auth, view, noteList, adapter);
                    return true;
                } else if (itemId == R.id.menu_lock) {
                    toggleLock(note, db, auth, view, noteList, adapter);
                    return true;
                }
                return false;
            });

            popupMenu.show();
        }

        // ✅ IMPROVED: Lock with security check, Unlock requires authentication
        private void toggleLock(Note note, FirebaseFirestore db, FirebaseAuth auth, View view,
                                List<Note> noteList, NoteAdapter adapter) {
            Context context = view.getContext();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean newLockState = !note.isLocked();

            // ✅ SCENARIO 1: User wants to LOCK a note
            if (newLockState) {
                // Check if security is set up before allowing lock
                if (!isSecuritySetupComplete(context, auth)) {
                    redirectToSecuritySetup(context);
                    return;
                }
                // Security is set up, proceed with locking
                updateLockState(note, true, db, user, view, noteList, adapter);
            }
            // ✅ SCENARIO 2: User wants to UNLOCK a note - NEED AUTH
            else {
                authenticateToUnlock(context, auth, () -> {
                    updateLockState(note, false, db, user, view, noteList, adapter);
                });
            }
        }

        private void authenticateToUnlock(Context context, FirebaseAuth auth, Runnable onSuccess) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedPassword = prefs.getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), null);

            // ✅ If no password setup (for old users), redirect to setup
            if (savedPassword == null || savedPassword.isEmpty()) {
                Toast.makeText(context, "Please set up security first", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(context, BiometricSetupActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }

            // Try biometric first if available
            if (isBiometricAvailable(context) && context instanceof FragmentActivity) {
                showBiometricPromptForUnlock(context, auth, onSuccess);
            } else {
                // Fallback to password
                showPasswordDialogForUnlock(context, savedPassword, onSuccess);
            }
        }

        private void showBiometricPromptForUnlock(Context context, FirebaseAuth auth, Runnable onSuccess) {
            if (!(context instanceof FragmentActivity)) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String savedPassword = prefs.getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), "");
                showPasswordDialogForUnlock(context, savedPassword, onSuccess);
                return;
            }

            FragmentActivity activity = (FragmentActivity) context;
            Executor executor = ContextCompat.getMainExecutor(context);

            BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            Toast.makeText(context, "✓ Authentication successful", Toast.LENGTH_SHORT).show();
                            onSuccess.run();
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Toast.makeText(context, "✗ Authentication failed", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            // Fallback to password
                            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                            String savedPassword = prefs.getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), "");
                            showPasswordDialogForUnlock(context, savedPassword, onSuccess);
                        }
                    });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("🔓 Authenticate to Unlock")
                    .setSubtitle("Verify your identity to unlock this note")
                    .setNegativeButtonText("Use Password")
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }

        private void showPasswordDialogForUnlock(Context context, String savedPassword, Runnable onSuccess) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("🔐 Verify Master Password");
            builder.setMessage("Enter your master password to unlock this note");

            final EditText input = new EditText(context);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint("Master Password");
            builder.setView(input);

            builder.setPositiveButton("Unlock", (dialog, which) -> {
                String enteredPassword = input.getText().toString();
                if (enteredPassword.equals(savedPassword)) {
                    Toast.makeText(context, "✓ Verified!", Toast.LENGTH_SHORT).show();
                    onSuccess.run();
                } else {
                    Toast.makeText(context, "✗ Incorrect password", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        }

        // ✅ IMPROVED updateLockState with better error handling and UI feedback
        private void updateLockState(Note note, boolean newLockState, FirebaseFirestore db,
                                     FirebaseUser user, View view, List<Note> noteList,
                                     NoteAdapter adapter) {

            // Disable interaction during update
            view.setEnabled(false);

            // Show loading state
            Context context = view.getContext();

            db.collection("users")
                    .document(user.getUid())
                    .collection("notes")
                    .document(note.getId())
                    .update("isLocked", newLockState)
                    .addOnSuccessListener(aVoid -> {
                        // Update local note object
                        note.setLocked(newLockState);

                        // Update the note in the list
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            noteList.set(position, note);
                            adapter.notifyItemChanged(position);
                        }

                        // Re-enable interaction
                        view.setEnabled(true);

                        // Show success message with emoji
                        String message = newLockState ? "Note locked 🔒" : "Note unlocked 🔓";
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                        Log.d("NoteAdapter", "Lock state updated successfully for note: " + note.getId()
                                + " to " + newLockState);
                    })
                    .addOnFailureListener(e -> {
                        // Re-enable interaction
                        view.setEnabled(true);

                        // Show error message
                        Log.e("NoteAdapter", "Failed to update lock state for note: " + note.getId(), e);
                        Toast.makeText(context, "✗ Failed to update lock state. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    });
        }

        private void deleteNote(Note note, FirebaseFirestore db, FirebaseAuth auth, View view,
                                List<Note> noteList, NoteAdapter adapter) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                String userId = user.getUid();
                String noteId = note.getId();

                // First, delete all subpages (if they exist)
                db.collection("users")
                        .document(userId)
                        .collection("notes")
                        .document(noteId)
                        .collection("subpages")
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            // Delete each subpage
                            for (com.google.firebase.firestore.QueryDocumentSnapshot document : querySnapshot) {
                                document.getReference().delete()
                                        .addOnFailureListener(e ->
                                                Log.e("NoteAdapter", "Failed to delete subpage: " + document.getId(), e));
                            }

                            // After deleting subpages, delete the main note document
                            db.collection("users")
                                    .document(userId)
                                    .collection("notes")
                                    .document(noteId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        // Remove from list and notify adapter
                                        int position = getAdapterPosition();
                                        if (position != RecyclerView.NO_POSITION) {
                                            noteList.remove(position);
                                            adapter.notifyItemRemoved(position);
                                        }

                                        Log.d("NoteAdapter", "Note and all subpages deleted successfully");
                                        Toast.makeText(view.getContext(), "✓ Note deleted",
                                                Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("NoteAdapter", "Failed to delete note", e);
                                        Toast.makeText(view.getContext(), "✗ Failed to delete note",
                                                Toast.LENGTH_SHORT).show();
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Log.e("NoteAdapter", "Failed to fetch subpages", e);
                            // Even if subpages fetch fails, try to delete the main note
                            db.collection("users")
                                    .document(userId)
                                    .collection("notes")
                                    .document(noteId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        int position = getAdapterPosition();
                                        if (position != RecyclerView.NO_POSITION) {
                                            noteList.remove(position);
                                            adapter.notifyItemRemoved(position);
                                        }
                                        Toast.makeText(view.getContext(), "✓ Note deleted",
                                                Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(deleteError -> {
                                        Toast.makeText(view.getContext(), "✗ Failed to delete note",
                                                Toast.LENGTH_SHORT).show();
                                    });
                        });
            }
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