package com.example.testtasksync;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.Button;
import android.view.MenuItem;
import androidx.core.content.ContextCompat;

import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.graphics.PorterDuff;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
    private MultiSelectListener multiSelectListener;
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

    public interface MultiSelectListener {
        void onLongPress(Note note);
        void onItemClick(Note note);
        boolean isMultiSelectMode();
        boolean isSelected(Note note);
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

    public void setMultiSelectListener(MultiSelectListener listener) {
        this.multiSelectListener = listener;
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
        holder.bind(note, listener, db, auth, isGridLayout, noteList, this, typeDetector, multiSelectListener);
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteTitle, noteContent;
        ImageView starIcon, menuButton, lockIcon;
        CheckBox selectCheckBox;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteTitle = itemView.findViewById(R.id.noteTitle);
            noteContent = itemView.findViewById(R.id.noteContent);
            starIcon = itemView.findViewById(R.id.starIcon);
            menuButton = itemView.findViewById(R.id.noteMenuButton);
            lockIcon = itemView.findViewById(R.id.lockIcon);
            selectCheckBox = itemView.findViewById(R.id.selectCheckBox);
        }

        public void bind(Note note, OnNoteClickListener listener, FirebaseFirestore db,
                         FirebaseAuth auth, boolean isGridLayout, List<Note> noteList,
                         NoteAdapter adapter, ItemTypeDetector typeDetector,
                         MultiSelectListener multiSelectListener) {

            if (note == null) {
                Log.e("NoteAdapter", "Note is null in bind()");
                return;
            }

            noteTitle.setText(note.getTitle() != null ? note.getTitle() : "Untitled");

            if (lockIcon != null) {
                lockIcon.setVisibility(note.isLocked() ? View.VISIBLE : View.GONE);
            }

            if (note.isLocked()) {
                noteContent.setText("Locked");
            } else {
                noteContent.setText(note.getContent() != null ? note.getContent() : "");
            }

            updateStarIcon(note.isStarred());

            String itemType = typeDetector != null ? typeDetector.getItemType(note) : "note";

            // Multi-select checkbox visibility
            if (selectCheckBox != null && multiSelectListener != null) {
                if (multiSelectListener.isMultiSelectMode()) {
                    selectCheckBox.setVisibility(View.VISIBLE);
                    selectCheckBox.setChecked(multiSelectListener.isSelected(note));
                } else {
                    selectCheckBox.setVisibility(View.GONE);
                    selectCheckBox.setChecked(false);
                }
            }

            starIcon.setOnClickListener(v -> {
                if (multiSelectListener != null && multiSelectListener.isMultiSelectMode()) {
                    multiSelectListener.onItemClick(note);
                } else {
                    boolean newStarState = !note.isStarred();
                    note.setStarred(newStarState);
                    updateStarIcon(newStarState);
                    updateStarInFirebase(note, db, auth, itemType);
                }
            });

            if (menuButton != null) {
                menuButton.setOnClickListener(v -> {
                    if (multiSelectListener != null && multiSelectListener.isMultiSelectMode()) {
                        multiSelectListener.onItemClick(note);
                    } else {
                        showPopupMenu(v, note, db, auth, noteList, adapter, itemType);
                    }
                });
            }

            // Long press to enter multi-select mode
            itemView.setOnLongClickListener(v -> {
                if (multiSelectListener != null) {
                    multiSelectListener.onLongPress(note);
                    return true;
                }
                return false;
            });

            // Regular click
            itemView.setOnClickListener(v -> {
                if (multiSelectListener != null && multiSelectListener.isMultiSelectMode()) {
                    multiSelectListener.onItemClick(note);
                } else if (note.isLocked()) {
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
                callback.onResult(false);
                return;
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String userKey = getUserKey(context, auth, SECURITY_SETUP_COMPLETE);
            String passwordKey = getUserKey(context, auth, MASTER_PASSWORD_KEY);

            boolean localSetup = prefs.getBoolean(userKey, false);
            String localPassword = prefs.getString(passwordKey, null);

            if (localSetup && localPassword != null && !localPassword.isEmpty()) {
                callback.onResult(true);
                return;
            }

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
                                callback.onResult(true);
                            } else {
                                callback.onResult(false);
                            }
                        } else {
                            callback.onResult(false);
                        }
                    })
                    .addOnFailureListener(e -> callback.onResult(false));
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
                return false;
            }

            BiometricManager biometricManager = BiometricManager.from(context);
            int canAuthenticate = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG);

            return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
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
                    .setTitle("🔐 Unlock " + itemLabel)
                    .setSubtitle("Use your fingerprint to access this locked item")
                    .setNegativeButtonText("Use Password")
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }

        private void showPasswordDialog(Context context, Note note, OnNoteClickListener listener,
                                        String savedPassword) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_verify_password, null);
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();

            EditText passwordInput = dialogView.findViewById(R.id.passwordInput);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);
            Button btnUnlock = dialogView.findViewById(R.id.btnUnlock);

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnUnlock.setOnClickListener(v -> {
                String enteredPassword = passwordInput.getText().toString();
                if (enteredPassword.equals(savedPassword)) {
                    Toast.makeText(context, "✓ Unlocked!", Toast.LENGTH_SHORT).show();
                    listener.onNoteClick(note);
                    dialog.dismiss();
                } else {
                    Toast.makeText(context, "✗ Incorrect password", Toast.LENGTH_SHORT).show();
                }
            });

            dialog.show();
        }

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

            TextView lockText = lockView.findViewById(R.id.menu_lock).findViewById(android.R.id.text1);
            if (lockText == null) {
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

            popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
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
                    .setTitle("🔐 Authenticate to Unlock")
                    .setSubtitle("Verify your identity to unlock this item")
                    .setNegativeButtonText("Use Password")
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }

        private void showPasswordDialogForUnlock(Context context, String savedPassword, Runnable onSuccess) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_verify_password, null);
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();

            EditText passwordInput = dialogView.findViewById(R.id.passwordInput);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);
            Button btnUnlock = dialogView.findViewById(R.id.btnUnlock);

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnUnlock.setOnClickListener(v -> {
                String enteredPassword = passwordInput.getText().toString();
                if (enteredPassword.equals(savedPassword)) {
                    Toast.makeText(context, "✓ Verified!", Toast.LENGTH_SHORT).show();
                    onSuccess.run();
                    dialog.dismiss();
                } else {
                    Toast.makeText(context, "✗ Incorrect password", Toast.LENGTH_SHORT).show();
                }
            });

            dialog.show();
        }

        private void updateLockState(Note note, boolean newLockState, FirebaseFirestore db,
                                     FirebaseUser user, View view, List<Note> noteList,
                                     NoteAdapter adapter, String itemType) {
            view.setEnabled(false);
            Context context = view.getContext();

            String collection;
            if (itemType.equals("todo") || itemType.equals("weekly")) {
                collection = "schedules";
            } else {
                collection = "notes";
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
                    })
                    .addOnFailureListener(e -> {
                        view.setEnabled(true);
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

            new android.app.AlertDialog.Builder(view.getContext())
                    .setTitle("Move to Bin?")
                    .setMessage("This item will be moved to the bin and can be restored within 30 days.")
                    .setPositiveButton("Move to Bin", (dialog, which) -> {
                        if (itemType.equals("todo") || itemType.equals("weekly")) {
                            softDeleteSchedule(userId, itemId, db, view, noteList, adapter, itemType);
                        } else {
                            softDeleteNote(userId, itemId, db, view, noteList, adapter);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void softDeleteNote(String userId, String noteId, FirebaseFirestore db, View view,
                                    List<Note> noteList, NoteAdapter adapter) {
            Toast.makeText(view.getContext(), "✓ Note moved to bin", Toast.LENGTH_SHORT).show();
            removeFromList(view, noteList, adapter);

            db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document(noteId)
                    .update("deletedAt", com.google.firebase.Timestamp.now())
                    .addOnSuccessListener(aVoid -> {
                        Log.d("NoteAdapter", "Note soft deleted: " + noteId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("NoteAdapter", "Failed to soft delete note", e);
                    });
        }

        private void softDeleteSchedule(String userId, String scheduleId, FirebaseFirestore db,
                                        View view, List<Note> noteList, NoteAdapter adapter, String itemType) {
            String itemLabel = "todo".equals(itemType) ? "To-Do" : "Weekly plan";
            Toast.makeText(view.getContext(), "✓ " + itemLabel + " moved to bin", Toast.LENGTH_SHORT).show();
            removeFromList(view, noteList, adapter);

            db.collection("users")
                    .document(userId)
                    .collection("schedules")
                    .document(scheduleId)
                    .get()
                    .addOnSuccessListener(scheduleDoc -> {
                        if (scheduleDoc.exists()) {
                            String sourceId = scheduleDoc.getString("sourceId");
                            String category = scheduleDoc.getString("category");

                            com.google.firebase.firestore.WriteBatch batch = db.batch();

                            batch.update(
                                    db.collection("users")
                                            .document(userId)
                                            .collection("schedules")
                                            .document(scheduleId),
                                    "deletedAt", com.google.firebase.Timestamp.now()
                            );

                            if (sourceId != null && !sourceId.isEmpty() && category != null) {
                                String sourceCollection = "todo".equals(category) ? "todoLists" : "weeklyPlans";

                                batch.update(
                                        db.collection("users")
                                                .document(userId)
                                                .collection(sourceCollection)
                                                .document(sourceId),
                                        "deletedAt", com.google.firebase.Timestamp.now()
                                );
                            }

                            batch.commit();
                        }
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
                String collection;
                if (itemType.equals("todo") || itemType.equals("weekly")) {
                    collection = "schedules";
                } else {
                    collection = "notes";
                }

                db.collection("users")
                        .document(user.getUid())
                        .collection(collection)
                        .document(note.getId())
                        .update("isStarred", note.isStarred());
            }
        }
    }
}