package com.example.testtasksync;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages multiple user accounts on the device
 */
public class AccountManager {
    private static final String PREF_NAME = "TaskSyncAccounts";
    private static final String KEY_ACCOUNTS = "saved_accounts";
    private static final String KEY_CURRENT_ACCOUNT = "current_account_email";

    private SharedPreferences prefs;
    private Gson gson;

    public AccountManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    /**
     * Save a new account to the device
     */
    public void saveAccount(String email, String userId, String displayName, String photoUrl) {
        List<SavedAccount> accounts = getSavedAccounts();

        // Check if account already exists
        boolean exists = false;
        for (SavedAccount account : accounts) {
            if (account.getEmail().equals(email)) {
                // Update existing account
                account.setUserId(userId);
                account.setDisplayName(displayName);
                account.setPhotoUrl(photoUrl);
                exists = true;
                break;
            }
        }

        // Add new account if it doesn't exist
        if (!exists) {
            accounts.add(new SavedAccount(email, userId, displayName, photoUrl));
        }

        // Save to SharedPreferences
        String json = gson.toJson(accounts);
        prefs.edit().putString(KEY_ACCOUNTS, json).apply();

        // Set as current account
        setCurrentAccount(email);
    }

    /**
     * Get all saved accounts
     */
    public List<SavedAccount> getSavedAccounts() {
        String json = prefs.getString(KEY_ACCOUNTS, null);
        if (json == null) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<List<SavedAccount>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * Remove an account from saved accounts
     */
    public void removeAccount(String email) {
        List<SavedAccount> accounts = getSavedAccounts();
        accounts.removeIf(account -> account.getEmail().equals(email));

        String json = gson.toJson(accounts);
        prefs.edit().putString(KEY_ACCOUNTS, json).apply();

        // Clear current account if it was removed
        if (email.equals(getCurrentAccountEmail())) {
            prefs.edit().remove(KEY_CURRENT_ACCOUNT).apply();
        }
    }

    /**
     * Set current active account
     */
    public void setCurrentAccount(String email) {
        prefs.edit().putString(KEY_CURRENT_ACCOUNT, email).apply();
    }

    /**
     * Get current account email
     */
    public String getCurrentAccountEmail() {
        return prefs.getString(KEY_CURRENT_ACCOUNT, null);
    }

    /**
     * Clear all saved accounts (useful for testing)
     */
    public void clearAllAccounts() {
        prefs.edit().clear().apply();
    }

    /**
     * Model class for saved accounts
     */
    public static class SavedAccount {
        private String email;
        private String userId;
        private String displayName;
        private String photoUrl;

        public SavedAccount(String email, String userId, String displayName, String photoUrl) {
            this.email = email;
            this.userId = userId;
            this.displayName = displayName;
            this.photoUrl = photoUrl;
        }

        public String getEmail() {
            return email;
        }

        public String getUserId() {
            return userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getPhotoUrl() {
            return photoUrl;
        }

        public void setPhotoUrl(String photoUrl) {
            this.photoUrl = photoUrl;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }
}