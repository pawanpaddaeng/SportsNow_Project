package com.example.cpen321application.core.session

import android.content.Context
import android.util.Log
import com.example.cpen321application.core.network.ServerApi
import com.example.cpen321application.user.dto.LoginRequest
import com.example.cpen321application.user.dto.readAuthResponse
import com.example.cpen321application.user.triggerSilentGoogleSignIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.gson.JsonParseException
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException

// JWT from the server after sign up / sign in, also saved with AuthTokenStore
var jwtToken: String? = null

/*
    Keeps the signed in session working for API calls that need the JWT.
    Session.call sends a request with the JWT; if the server says it expired, it
    signs in again silently with the same Google account and retries once.
    When the session can't be used or renewed, signInRequired fires and the app
    goes back to the sign in screen
 */
object Session {
    private const val TAG = "Session"

    // fires when the user has to sign in again, AppNavigation listens and shows sign in
    private val _signInRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signInRequired: SharedFlow<Unit> = _signInRequired

    // only one silent sign in at a time, other expired calls wait and reuse its token
    private val renewLock = Mutex()

    /*  runs an API call that needs the JWT. request gets the Authorization header
        value ("Bearer <jwt>"). Returns the response, or null when the session ended
        (the user is then sent to sign in). Network errors are thrown as usual
     */
    suspend fun <T> call(
        context: Context,
        request: suspend (authorization: String) -> Response<T>
    ): Response<T>? {
        val jwt = jwtToken ?: AuthTokenStore.load(context) ?: return endSession()

        val response = request("Bearer $jwt")
        when (authProblem(response)) {
            null -> return response
            "expired_token" -> {
                val renewedJwt = renew(context, jwt) ?: return endSession()
                val retried = request("Bearer $renewedJwt")
                return if (authProblem(retried) == null) retried else endSession()
            }
            // no_user, invalid_token: this session can't be used any more
            else -> return endSession()
        }
    }

    // sends the user back to sign in, e.g. when a request can't continue without a new session
    fun requireSignIn() {
        _signInRequired.tryEmit(Unit)
    }

    private fun <T> endSession(): Response<T>? {
        requireSignIn()
        return null
    }

    /*  gets a new JWT without the user's help: silent Google sign in, then the
        login api. The new token must be for the same user as the expired one,
        otherwise a different Google account was picked and it is rejected.
        Returns null if the user has to sign in themselves
     */
    suspend fun renew(context: Context, expiredJwt: String): String? =
        renewLock.withLock { renewWhileLocked(context, expiredJwt) }

    // renew's work, renewLock makes sure only one call runs it at a time
    private suspend fun renewWhileLocked(context: Context, expiredJwt: String): String? {
        // another call already renewed the session while this one waited
        val current = jwtToken
        if (current != null && current != expiredJwt) return current

        val googleToken = triggerSilentGoogleSignIn(context) ?: return null
        val newJwt = try {
            val reply = readAuthResponse(
                ServerApi.retrofitService.login(LoginRequest(idToken = googleToken))
            )
            reply?.jwtToken?.takeIf { reply.msg == "signedIn" }
        } catch (e: IOException) {
            Log.w(TAG, "Silent sign in failed, server unreachable", e)
            null
        } catch (e: JsonParseException) {
            Log.w(TAG, "Silent sign in failed, unreadable reply", e)
            null
        } ?: return null

        val previousUser = AuthTokenStore.userIdOf(expiredJwt)
        if (previousUser == null || AuthTokenStore.userIdOf(newJwt) != previousUser) {
            return null
        }

        jwtToken = newJwt
        AuthTokenStore.save(context, newJwt)
        return newJwt
    }

    /*  the JWT middleware answers 401 with msg expired_token, invalid_token or
        no_user. Returns that msg, or null if the request wasn't rejected for auth.
        Reads a copy of the error body so callers can still read it afterwards
     */
    private fun authProblem(response: Response<*>): String? {
        if (response.code() != 401) return null
        return try {
            val source = response.errorBody()?.source() ?: return "unauthorized"
            source.request(Long.MAX_VALUE)
            val text = source.buffer.clone().readUtf8()
            JSONObject(text).optString("msg").ifBlank { "unauthorized" }
        } catch (e: IOException) {
            "unauthorized"
        } catch (e: JSONException) {
            "unauthorized"
        }
    }
}
