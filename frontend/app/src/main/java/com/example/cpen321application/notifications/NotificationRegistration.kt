package com.example.cpen321application.notifications

import android.content.Context
import android.util.Log
import com.example.cpen321application.auth.AuthTokenStore
import com.example.cpen321application.auth.Session
import com.example.cpen321application.auth.jwtToken
import com.example.cpen321application.network.RegisterNotifRequest
import com.example.cpen321application.network.ServerApi
import com.example.cpen321application.network.readAuthResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.JsonParseException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume

// result of trying to register this device for push notifications
enum class NotifRegistrationResult {
    // the server saved this device's Firebase token
    REGISTERED,
    // nothing to do right now (Firebase not set up or server unreachable), the user stays signed in
    SKIPPED,
    // the server didn't accept it, the user is sent back to sign in
    FAILED
}

/*
    Sends this device's Firebase token to the server (POST api/register_notif)
    so the user can get push notifications. Runs at app start when a saved JWT
    exists and after sign in / sign up. Session.call renews an expired JWT
    silently and sends the user to sign in if the session can't be used
 */
object NotificationRegistration {
    private const val TAG = "NotifRegistration"

    suspend fun register(context: Context): NotifRegistrationResult {
        val fcmToken = getFirebaseToken() ?: return NotifRegistrationResult.SKIPPED

        return try {
            val response = Session.call(context) { authorization ->
                ServerApi.retrofitService.registerNotif(
                    authorization = authorization,
                    request = RegisterNotifRequest(fcmToken = fcmToken)
                )
            } ?: return NotifRegistrationResult.FAILED

            if (readAuthResponse(response)?.msg == "success") {
                NotifRegistrationResult.REGISTERED
            } else {
                // server_error or an unreadable reply: start over from sign in
                Session.requireSignIn()
                NotifRegistrationResult.FAILED
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not reach the server to register notifications", e)
            NotifRegistrationResult.SKIPPED
        } catch (e: JsonParseException) {
            Log.w(TAG, "Could not read the server's reply to registering notifications", e)
            NotifRegistrationResult.SKIPPED
        }
    }

    /*  sends a token Firebase just replaced (onNewToken). This can run while the
        app is closed, so it can't renew an expired session or show sign in: it
        only sends the token if a saved JWT exists and ignores failures. The next
        app launch runs register() again, which handles renewal
     */
    suspend fun registerNewToken(context: Context, fcmToken: String) {
        val jwt = jwtToken ?: AuthTokenStore.load(context) ?: return
        try {
            val response = ServerApi.retrofitService.registerNotif(
                authorization = "Bearer $jwt",
                request = RegisterNotifRequest(fcmToken = fcmToken)
            )
            Log.d(TAG, "Sent new Firebase token, server reply: ${readAuthResponse(response)?.msg}")
        } catch (e: IOException) {
            Log.w(TAG, "Could not send the new Firebase token", e)
        } catch (e: JsonParseException) {
            Log.w(TAG, "Could not read the server's reply to the new Firebase token", e)
        }
    }

    // this device's Firebase token, or null if Firebase isn't set up (no google-services.json)
    private suspend fun getFirebaseToken(): String? {
        return try {
            suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    continuation.resume(if (task.isSuccessful) task.result else null)
                }
            }
        } catch (e: IllegalStateException) {
            // FirebaseMessaging.getInstance() throws this when no FirebaseApp was set up
            Log.w(TAG, "Firebase is not set up, skipping notification registration", e)
            null
        }
    }
}
