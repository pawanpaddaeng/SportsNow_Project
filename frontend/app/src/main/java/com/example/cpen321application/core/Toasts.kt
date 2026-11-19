package com.example.cpen321application.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/*  shows a short message at the bottom of the screen, or a longer lasting one with long.
    Android only shows toasts from the main thread, and a coroutine can carry on elsewhere
    (e.g. on the network thread in the E2E tests), so from any other thread it's posted there
 */
fun showToast(context: Context, message: String, long: Boolean = false) {
    val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    val mainLooper = Looper.getMainLooper()
    if (Looper.myLooper() == mainLooper) {
        Toast.makeText(context, message, duration).show()
    } else {
        Handler(mainLooper).post { Toast.makeText(context, message, duration).show() }
    }
}
