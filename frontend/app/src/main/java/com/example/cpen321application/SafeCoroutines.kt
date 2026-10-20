package com.example.cpen321application

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

private const val TAG = "SafeCoroutines"
private const val UNEXPECTED_ERROR_MESSAGE = "Something went wrong, try again"

/*  last resort for errors nothing else expected (requests already handle network and
    reply problems): logs the error and calls onError instead of crashing the app.
    Cancellation never reaches it, so leaving a screen still stops its work
 */
fun unexpectedErrorHandler(onError: () -> Unit = {}): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Unexpected error", error)
        onError()
    }

// a handler for a screen's coroutines that shows a general message on an unexpected error
@Composable
fun rememberUnexpectedErrorHandler(): CoroutineExceptionHandler {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        unexpectedErrorHandler {
            Toast.makeText(context, UNEXPECTED_ERROR_MESSAGE, Toast.LENGTH_SHORT).show()
        }
    }
}

// like rememberCoroutineScope, but its coroutines show a message instead of crashing on unexpected errors
@Composable
fun rememberSafeCoroutineScope(): CoroutineScope {
    val handler = rememberUnexpectedErrorHandler()
    return rememberCoroutineScope { handler }
}

/*  runs a LaunchedEffect's work so an unexpected error goes to handler instead of
    crashing. It's still cancelled with the effect
 */
suspend fun runGuarded(handler: CoroutineExceptionHandler, block: suspend CoroutineScope.() -> Unit) {
    // a coroutine launched directly in a supervisorScope uses its own exception handler
    supervisorScope { launch(handler, block = block) }
}
