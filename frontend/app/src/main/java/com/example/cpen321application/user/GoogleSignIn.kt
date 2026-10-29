package com.example.cpen321application.user

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.cpen321application.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

//helper to ensure proper context is passed to credential manager
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/*
    Sign in function that handles signing in with Google and returns
    the Google id token if sign in was successful
 */
suspend fun triggerGoogleSignIn(context: Context): String? {
    val activityContext = context.findActivity()
        ?: throw IllegalStateException("Google Sign-In requires an Activity Context")

    val credentialManager = CredentialManager.create(context)

    //see all google account options on the device(signed in or out)
    val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    /*  fallback "Sign in with Google" flow: unlike the bottom sheet above, it
        still opens when no account is on the device and lets the user add one
     */
    val signInWithGoogleRequest = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_CLIENT_ID).build()
        )
        .build()

    //mutable context to avoid memory leak as suggested by google
    val mutableContext = MutableContextWrapper(activityContext)

    return try {
            val result = try {
                credentialManager.getCredential(
                    request = request,
                    context = mutableContext
                )
            } catch (e: NoCredentialException) {
                credentialManager.getCredential(
                    request = signInWithGoogleRequest,
                    context = mutableContext
                )
            }
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {

                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                print("SUCCESS! Signed into google")
                idToken
            } else {
                print("Unexpected credential type returned")
                null
            }
        } catch (e: GetCredentialException) {
            // Handle failures and return null
            print("EROR: $e")
            null
        }

}

/*
    Silent Google sign in used to renew an expired session: only offers accounts
    that already signed into this app and picks one automatically, so the user
    normally sees no prompt. Returns the Google id token, or null if it can't
    sign in without the user's help
 */
suspend fun triggerSilentGoogleSignIn(context: Context): String? {
    val activityContext = context.findActivity() ?: return null
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setAutoSelectEnabled(true)
        .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val credential = credentialManager.getCredential(
            request = request,
            context = MutableContextWrapper(activityContext)
        ).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            null
        }
    } catch (e: GetCredentialException) {
        null
    }
}
