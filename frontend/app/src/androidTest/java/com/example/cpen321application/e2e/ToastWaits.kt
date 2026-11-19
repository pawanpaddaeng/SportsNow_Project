package com.example.cpen321application.e2e

import android.view.accessibility.AccessibilityEvent
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeoutException

// how long a toast may take to show, a toast already showing stays up to 3.5 seconds
private const val TOAST_TIMEOUT_MILLIS = 15_000L

/*  runs action, then waits for a toast saying text. Android shows toasts outside the app's
    Compose screens, so the test listens for the accessibility event Android sends for each
    toast, through UiAutomation, the connection to Android that UI Automator uses
 */
fun expectToast(text: String, action: () -> Unit) {
    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    try {
        uiAutomation.executeAndWaitForEvent(
            { action() },
            { event ->
                event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                    event.text.joinToString().contains(text)
            },
            TOAST_TIMEOUT_MILLIS
        )
    } catch (e: TimeoutException) {
        throw AssertionError("No toast said \"$text\"", e)
    }
}