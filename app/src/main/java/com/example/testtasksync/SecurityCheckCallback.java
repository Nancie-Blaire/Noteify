package com.example.testtasksync;

/**
 * ✅ Callback interface for asynchronous security checks
 *
 * Why do we need this?
 * - Firestore operations are ASYNCHRONOUS (may delay)
 * - We can't return true/false immediately
 * - We need a callback to notify when the check is complete
 *
 * Usage:
 * isSecuritySetupComplete(context, auth, new SecurityCheckCallback() {
 *     @Override
 *     public void onResult(boolean isComplete) {
 *         if (isComplete) {
 *             // Proceed with lock/unlock
 *         } else {
 *             // Redirect to setup
 *         }
 *     }
 * });
 */
public interface SecurityCheckCallback {
    void onResult(boolean isComplete);
}