package com.example.cpen321application.e2e

import android.util.Log
import androidx.test.uiautomator.UiDevice

// how long the test waits for the manual tester to press Enter after a User B step
private const val USER_B_TIMEOUT_MILLIS = 600_000L

// how often the test checks again whether the step was confirmed
private const val RECHECK_MILLIS = 1_000L

/*  must match LOG_TAG, DONE_FILE and DONE in scripts/run-e2e-user-b, the helper that shows the
    manual tester each "USER B:" step in a second terminal and, when they press Enter, writes
    DONE to DONE_FILE on User A's emulator. It's the same helper FriendSystemTest uses
 */
private const val LOG_TAG = "FriendSystemTest"
private const val DONE_FILE = "/data/local/tmp/e2e-user-b-done"
private const val DONE = "done"

/*  User B, the manual tester's account "Bea Tester" on another device (see Testing.md,
    "Doing User B's steps"). ask shows them a step and waits until they confirm it with Enter
 */
class UserB(private val device: UiDevice) {
    fun ask(step: String) {
        device.executeShellCommand("rm -f $DONE_FILE")
        Log.i(LOG_TAG, "USER B: $step")
        val deadline = System.currentTimeMillis() + USER_B_TIMEOUT_MILLIS
        while (device.executeShellCommand("cat $DONE_FILE").trim() != DONE) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("User B's step wasn't confirmed with Enter in time: $step")
            }
            Thread.sleep(RECHECK_MILLIS)
        }
        device.executeShellCommand("rm -f $DONE_FILE")
    }
}
