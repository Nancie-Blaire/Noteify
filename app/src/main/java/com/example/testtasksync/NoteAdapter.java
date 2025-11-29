package com.example.testtasksync;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
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
    private ItemTypeDetector typeDetector;

    private static final String PREFS_NAME = "NoteSecurityPrefs";
    private static final String MASTER_PASSWORD_KEY = "master_password";
    private static final String BIOMETRIC_ENABLED_KEY = "biometric_enabled";
    private static final String SECURITY_SETUP_COMPLETE = "security_setup_complete";

    interface SecurityCheckCallback {
        void onResult(boolean isComplete);
    }

    public interface ItemTypeDetector {
        String getItemType(Note note);
    }

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    public NoteAdapter(List<Note> noteList, OnNoteClickListener listener, boolean isGridLayout) {
        this.noteList = noteList;
        this.listener = listener;
        this.isGridLayout = isGridLayout;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.typeDetector = null;
    }

    public NoteAdapter(List<Note> noteList, OnNoteClickListener listener) {
        this(noteList, listener, false);
    }

    public NoteAdapter(List<Note> noteList, OnNoteClickListener listener, boolean isGridLayout, ItemTypeDetector typeDetector) {
        this(noteList, listener, isGridLayout);
        this.typeDetector = typeDetector;
    }

    public void setTypeDetector(ItemTypeDetector typeDetector) {
        this.typeDetector = typeDetector;
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
        holder.bind(note, listener, db, auth, isGridLayout, noteList, this, typeDetector);
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteTitle, noteContent;
        ImageView starIcon, menuButton, lockIcon;

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
                         NoteAdapter adapter, ItemTypeDetector typeDetector) {

            if (note == null) {
                Log.e("NoteAdapter", "Note is null in bind()");
                return;
            }

            noteTitle.setText(note.getTitle() != null ? note.getTitle() : "Untitled");

            if (lockIcon != null) {
                lockIcon.setVisibility(note.isLocked() ? View.VISIBLE : View.GONE);
            }

            // ✅ ALWAYS show locked content if the note is locked
            if (note.isLocked()) {
                noteContent.setText("Locked");
            } else {
                noteContent.setText(note.getContent() != null ? note.getContent() : "");
            }

            updateStarIcon(note.isStarred());

            String itemType = typeDetector != null ? typeDetector.getItemType(note) : "note";

            starIcon.setOnClickListener(v -> {
                boolean newStarState = !note.isStarred();
                note.setStarred(newStarState);
                updateStarIcon(newStarState);
                updateStarInFirebase(note, db, auth, itemType);
            });

            if (menuButton != null) {
                menuButton.setOnClickListener(v -> {
                    showPopupMenu(v, note, db, auth, noteList, adapter, itemType);
                });
            }

            // ✅ FIX: Always check lock state before opening
            itemView.setOnClickListener(v -> {
                if (note.isLocked()) {
                    // ✅ Authenticate each time for locked notes
                    authenticateAndOpen(v.getContext(), note, listener, auth, itemType);
                } else {
                    listener.onNoteClick(note);
                }
            });
        }

        private String getUserKey(Context context, FirebaseAuth auth, String baseKey) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                return user.getUid() + "_" + baseKey;
            }
            return baseKey;
        }

        private void isSecuritySetupComplete(Context context, FirebaseAuth auth,
                                             SecurityCheckCallback callback) {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Log.d("NoteAdapter", "No user logged in");
                callback.onResult(false);
                return;
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String userKey = getUserKey(context, auth, SECURITY_SETUP_COMPLETE);
            String passwordKey = getUserKey(context, auth, MASTER_PASSWORD_KEY);

            boolean localSetup = prefs.getBoolean(userKey, false);
            String localPassword = prefs.getString(passwordKey, null);

            if (localSetup && localPassword != null && !localPassword.isEmpty()) {
                Log.d("NoteAdapter", "✓ Security setup found locally");
                callback.onResult(true);
                return;
            }

            Log.d("NoteAdapter", "Checking Firestore for security settings...");
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users")
                    .document(user.getUid())
                    .collection("security")
                    .document("settings")
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String masterPassword = documentSnapshot.getString("masterPassword");
                            Boolean setupComplete = documentSnapshot.getBoolean("securitySetupComplete");

                            if (masterPassword != null && setupComplete != null && setupComplete) {
                                prefs.edit()
                                        .putString(passwordKey, masterPassword)
                                        .putBoolean(userKey, true)
                                        .apply();

                                Log.d("NoteAdapter", "✓ Security settings synced from Firestore");
                                callback.onResult(true);
                            } else {
                                Log.d("NoteAdapter", "✗ Firestore data incomplete");
                                callback.onResult(false);
                            }
                        } else {
                            Log.d("NoteAdapter", "✗ No Firestore security data");
                            callback.onResult(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("NoteAdapter", "Failed to check Firestore", e);
                        callback.onResult(false);
                    });
        }

        private void redirectToSecuritySetup(Context context) {
            Toast.makeText(context, "Please set up security first to lock items", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(context, BiometricSetupActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }

        private boolean shouldUseBiometric(Context context, FirebaseAuth auth) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String biometricKey = getUserKey(context, auth, BIOMETRIC_ENABLED_KEY);
            boolean enabledInApp = prefs.getBoolean(biometricKey, false);

            if (!enabledInApp) {
                Log.d("NoteAdapter", "✗ Biometric NOT enabled for this device - using password");
                return false;
            }

            BiometricManager biometricManager = BiometricManager.from(context);
            int canAuthenticate = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG);

            if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
                Log.d("NoteAdapter", "✗ Device has no enrolled fingerprints - biometric status: " + canAuthenticate);
                return false;
            }

            Log.d("NoteAdapter", "✓ Biometric enabled AND device has fingerprints - using biometric");
            return true;
        }

        private void authenticateAndOpen(Context context, Note note, OnNoteClickListener listener,
                                         FirebaseAuth auth, String itemType) {
            isSecuritySetupComplete(context, auth, isComplete -> {
                if (!isComplete) {
                    redirectToSecuritySetup(context);
                    return;
                }

                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String savedPassword = prefs.getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), null);

                if (savedPassword == null) {
                    redirectToSecuritySetup(context);
                    return;
                }

                if (shouldUseBiometric(context, auth)) {
                    showBiometricPrompt(context, note, listener, auth, itemType);
                } else {
                    showPasswordDialog(context, note, listener, savedPassword);
                }
            });
        }

        private void showBiometricPrompt(Context context, Note note, OnNoteClickListener listener,
                                         FirebaseAuth auth, String itemType) {
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
                            // ✅ Open note temporarily - lock state remains unchanged
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
                            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                            String savedPassword = prefs.getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), "");
                            showPasswordDialog(context, note, listener, savedPassword);
                        }
                    });

            String itemLabel = itemType.equals("todo") ? "To-Do List" :
                    itemType.equals("weekly") ? "Weekly Plan" : "Note";

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("🔓 Unlock " + itemLabel)
                    .setSubtitle("Use your fingerprint to access this locked item")
                    .setNegativeButtonText("Use Password")
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }

        private void showPasswordDialog(Context context, Note note, OnNoteClickListener listener,
                                        String savedPassword) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("🔒 Enter Master Password");

            final EditText input = new EditText(context);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint("Master Password");
            builder.setView(input);

            builder.setPositiveButton("Unlock", (dialog, which) -> {
                String enteredPassword = input.getText().toString();
                if (enteredPassword.equals(savedPassword)) {
                    Toast.makeText(context, "✓ Unlocked!", Toast.LENGTH_SHORT).show();
                    // ✅ Open note temporarily - lock state remains unchanged
                    listener.onNoteClick(note);
                } else {
                    Toast.makeText(context, "✗ Incorrect password", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        }

        // Replace the showPopupMenu method with this:
        private void showPopupMenu(View view, Note note, FirebaseFirestore db, FirebaseAuth auth,
                                   List<Note> noteList, NoteAdapter adapter, String itemType) {
            LayoutInflater inflater = LayoutInflater.from(view.getContext());
            View popupView = inflater.inflate(R.layout.menu_note_layout, null);

            final PopupWindow popupWindow = new PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );

            popupWindow.setElevation(8);

            View deleteView = popupView.findViewById(R.id.menu_delete);
            View lockView = popupView.findViewById(R.id.menu_lock);

            // Update lock text based on current state
            TextView lockText = lockView.findViewById(R.id.menu_lock).findViewById(android.R.id.text1);
            if (lockText == null) {
                // If using custom layout, find the TextView differently
                lockText = ((ViewGroup) lockView).getChildAt(1) instanceof TextView ?
                        (TextView) ((ViewGroup) lockView).getChildAt(1) : null;
            }

            if (lockText != null) {
                lockText.setText(note.isLocked() ? "Unlock" : "Lock");
            }

            deleteView.setOnClickListener(v -> {
                deleteItem(note, db, auth, view, noteList, adapter, itemType);
                popupWindow.dismiss();
            });

            lockView.setOnClickListener(v -> {
                toggleLock(note, db, auth, view, noteList, adapter, itemType);
                popupWindow.dismiss();
            });

            // Measure the popup view first
            popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

            // Position popup aligned to right edge of anchor, slightly below
            popupWindow.showAsDropDown(view, view.getWidth() - popupView.getMeasuredWidth(), 8);
        }

        private void toggleLock(Note note, FirebaseFirestore db, FirebaseAuth auth, View view,
                                List<Note> noteList, NoteAdapter adapter, String itemType) {
            Context context = view.getContext();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean newLockState = !note.isLocked();

            if (newLockState) {
                isSecuritySetupComplete(context, auth, isComplete -> {
                    if (!isComplete) {
                        redirectToSecuritySetup(context);
                    } else {
                        updateLockState(note, true, db, user, view, noteList, adapter, itemType);
                    }
                });
            } else {
                authenticateToUnlock(context, auth, () -> {
                    updateLockState(note, false, db, user, view, noteList, adapter, itemType);
                }, itemType);
            }
        }

        private void authenticateToUnlock(Context context, FirebaseAuth auth, Runnable onSuccess, String itemType) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedPassword = prefs.getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), null);

            if (savedPassword == null || savedPassword.isEmpty()) {
                Toast.makeText(context, "Please set up security first", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(context, BiometricSetupActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }

            if (shouldUseBiometric(context, auth) && context instanceof FragmentActivity) {
                showBiometricPromptForUnlock(context, auth, onSuccess, itemType);
            } else {
                showPasswordDialogForUnlock(context, savedPassword, onSuccess);
            }
        }

        private void showBiometricPromptForUnlock(Context context, FirebaseAuth auth, Runnable onSuccess, String itemType) {
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
                            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                            String savedPassword = prefs.getString(getUserKey(context, auth, MASTER_PASSWORD_KEY), "");
                            showPasswordDialogForUnlock(context, savedPassword, onSuccess);
                        }
                    });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("🔓 Authenticate to Unlock")
                    .setSubtitle("Verify your identity to unlock this item")
                    .setNegativeButtonText("Use Password")
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }

        private void showPasswordDialogForUnlock(Context context, String savedPassword, Runnable onSuccess) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("🔒 Verify Master Password");
            builder.setMessage("Enter your master password to unlock this item");

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

        private void updateLockState(Note note, boolean newLockState, FirebaseFirestore db,
                                     FirebaseUser user, View view, List<Note> noteList,
                                     NoteAdapter adapter, String itemType) {
            view.setEnabled(false);
            Context context = view.getContext();

            // ✅ Determine correct collection
            String collection;
            if (itemType.equals("todo") || itemType.equals("weekly")) {
                collection = "schedules";  // ✅ Todo and weekly are in schedules
            } else {
                collection = "notes";  // ✅ Notes are in notes collection
            }

            db.collection("users")
                    .document(user.getUid())
                    .collection(collection)
                    .document(note.getId())
                    .update("isLocked", newLockState)
                    .addOnSuccessListener(aVoid -> {
                        note.setLocked(newLockState);

                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            noteList.set(position, note);
                            adapter.notifyItemChanged(position);
                        }

                        view.setEnabled(true);

                        String message = newLockState ? "Item locked 🔒" : "Item unlocked 🔓";
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                        Log.d("NoteAdapter", "Lock state updated for " + itemType + ": " + note.getId());
                    })
                    .addOnFailureListener(e -> {
                        view.setEnabled(true);
                        Log.e("NoteAdapter", "Failed to update lock state", e);
                        Toast.makeText(context, "✗ Failed to update lock state",
                                Toast.LENGTH_SHORT).show();
                    });
        }

        private void deleteItem(Note note, FirebaseFirestore db, FirebaseAuth auth, View view,
                                List<Note> noteList, NoteAdapter adapter, String itemType) {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;

            String userId = user.getUid();
            String itemId = note.getId();

            if (itemType.equals("todo")) {
                deleteTodo(userId, itemId, db, view, noteList, adapter);
            } else if (itemType.equals("weekly")) {
                deleteWeekly(userId, itemId, db, view, noteList, adapter);
            } else {
                deleteNote(userId, itemId, db, view, noteList, adapter);
            }
        }

        private void deleteTodo(String userId, String todoId, FirebaseFirestore db, View view,
                                List<Note> noteList, NoteAdapter adapter) {
            // Step 1: Get the sourceId from the schedule
            db.collection("users")
                    .document(userId)
                    .collection("schedules")
                    .document(todoId)
                    .get()
                    .addOnSuccessListener(scheduleDoc -> {
                        if (scheduleDoc.exists()) {
                            String sourceId = scheduleDoc.getString("sourceId");

                            // Step 2: Soft delete the schedule reference
                            db.collection("users")
                                    .document(userId)
                                    .collection("schedules")
                                    .document(todoId)
                                    .update("deletedAt", com.google.firebase.Timestamp.now())
                                    .addOnSuccessListener(aVoid -> {
                                        // Step 3: Soft delete the actual todo list
                                        if (sourceId != null && !sourceId.isEmpty()) {
                                            db.collection("users")
                                                    .document(userId)
                                                    .collection("todoLists")
                                                    .document(sourceId)
                                                    .update("deletedAt", com.google.firebase.Timestamp.now())
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        removeFromList(view, noteList, adapter);
                                                        Toast.makeText(view.getContext(), "📦 To-Do moved to Bin",
                                                                Toast.LENGTH_SHORT).show();
                                                        Log.d("NoteAdapter", "Todo and todoList moved to bin: " + todoId);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Log.e("NoteAdapter", "Failed to soft delete todoList", e);
                                                        // Still show success since schedule was deleted
                                                        removeFromList(view, noteList, adapter);
                                                        Toast.makeText(view.getContext(), "📦 To-Do moved to Bin",
                                                                Toast.LENGTH_SHORT).show();
                                                    });
                                        } else {
                                            removeFromList(view, noteList, adapter);
                                            Toast.makeText(view.getContext(), "📦 To-Do moved to Bin",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(view.getContext(), "❌ Failed to delete",
                                                Toast.LENGTH_SHORT).show();
                                        Log.e("NoteAdapter", "Failed to move todo to bin", e);
                                    });
                        } else {
                            Toast.makeText(view.getContext(), "❌ Schedule not found",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(view.getContext(), "❌ Failed to delete",
                                Toast.LENGTH_SHORT).show();
                        Log.e("NoteAdapter", "Failed to get schedule", e);
                    });
        }

        private void deleteWeekly(String userId, String weeklyId, FirebaseFirestore db, View view,
                                  List<Note> noteList, NoteAdapter adapter) {
            // Step 1: Get the sourceId from the schedule
            db.collection("users")
                    .document(userId)
                    .collection("schedules")
                    .document(weeklyId)
                    .get()
                    .addOnSuccessListener(scheduleDoc -> {
                        if (scheduleDoc.exists()) {
                            String sourceId = scheduleDoc.getString("sourceId");

                            // Step 2: Soft delete the schedule reference
                            db.collection("users")
                                    .document(userId)
                                    .collection("schedules")
                                    .document(weeklyId)
                                    .update("deletedAt", com.google.firebase.Timestamp.now())
                                    .addOnSuccessListener(aVoid -> {
                                        // Step 3: Soft delete the actual weekly plan
                                        if (sourceId != null && !sourceId.isEmpty()) {
                                            db.collection("users")
                                                    .document(userId)
                                                    .collection("weeklyPlans")
                                                    .document(sourceId)
                                                    .update("deletedAt", com.google.firebase.Timestamp.now())
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        removeFromList(view, noteList, adapter);
                                                        Toast.makeText(view.getContext(), "📦 Weekly plan moved to Bin",
                                                                Toast.LENGTH_SHORT).show();
                                                        Log.d("NoteAdapter", "Weekly and weeklyPlan moved to bin: " + weeklyId);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Log.e("NoteAdapter", "Failed to soft delete weeklyPlan", e);
                                                        // Still show success since schedule was deleted
                                                        removeFromList(view, noteList, adapter);
                                                        Toast.makeText(view.getContext(), "📦 Weekly plan moved to Bin",
                                                                Toast.LENGTH_SHORT).show();
                                                    });
                                        } else {
                                            removeFromList(view, noteList, adapter);
                                            Toast.makeText(view.getContext(), "📦 Weekly plan moved to Bin",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(view.getContext(), "❌ Failed to delete",
                                                Toast.LENGTH_SHORT).show();
                                        Log.e("NoteAdapter", "Failed to move weekly to bin", e);
                                    });
                        } else {
                            Toast.makeText(view.getContext(), "❌ Schedule not found",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(view.getContext(), "❌ Failed to delete",
                                Toast.LENGTH_SHORT).show();
                        Log.e("NoteAdapter", "Failed to get schedule", e);
                    });
        }
        private void deleteNote(String userId, String noteId, FirebaseFirestore db, View view,
                                List<Note> noteList, NoteAdapter adapter) {
            // ✅ SOFT DELETE - Just set deletedAt timestamp
            db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document(noteId)
                    .update("deletedAt", com.google.firebase.Timestamp.now())
                    .addOnSuccessListener(aVoid -> {
                        removeFromList(view, noteList, adapter);
                        Toast.makeText(view.getContext(), "📦 Note moved to Bin",
                                Toast.LENGTH_SHORT).show();
                        Log.d("NoteAdapter", "Note moved to bin: " + noteId);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(view.getContext(), "❌ Failed to delete",
                                Toast.LENGTH_SHORT).show();
                        Log.e("NoteAdapter", "Failed to move note to bin", e);
                    });
        }
        private void removeFromList(View view, List<Note> noteList, NoteAdapter adapter) {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                noteList.remove(position);
                adapter.notifyItemRemoved(position);
            }
        }

        private void updateStarIcon(boolean isStarred) {
            if (isStarred) {
                starIcon.setImageResource(R.drawable.ic_star);
            } else {
                starIcon.setImageResource(R.drawable.ic_star_outline);
            }
        }

        private void updateStarInFirebase(Note note, FirebaseFirestore db, FirebaseAuth auth, String itemType) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                // ✅ ALL items (notes, todo, weekly) are in their respective collections
                String collection;
                if (itemType.equals("todo") || itemType.equals("weekly")) {
                    collection = "schedules";  // ✅ Todo and weekly are in schedules
                } else {
                    collection = "notes";  // ✅ Notes are in notes collection
                }

                db.collection("users")
                        .document(user.getUid())
                        .collection(collection)
                        .document(note.getId())
                        .update("isStarred", note.isStarred())
                        .addOnSuccessListener(aVoid -> {
                            Log.d("NoteAdapter", "Star state updated for " + itemType);
                        })
                        .addOnFailureListener(e -> {
                            Log.e("NoteAdapter", "Failed to update star state", e);
                        });
            }
        }
    }
}