package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.SIGN_IN_MESSAGE_KEY
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.auth.SignInScreen
import com.example.cpen321application.ui.screens.auth.SignUpActions
import com.example.cpen321application.ui.screens.auth.SignUpScreen
import com.example.cpen321application.ui.screens.auth.SignUpUiState
import com.example.cpen321application.user.repository.LoginResult
import com.example.cpen321application.user.repository.SignUpDetails
import com.example.cpen321application.user.repository.SignUpResult
import com.example.cpen321application.user.repository.login
import com.example.cpen321application.user.repository.readGoogleProfile
import com.example.cpen321application.user.repository.signUp
import com.example.cpen321application.user.triggerGoogleSignIn
import kotlinx.coroutines.launch

@Composable
fun SignInRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    var isSigningIn by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var noticeText by remember { mutableStateOf<String?>(null) }
    // message left by another screen, e.g. sign up when the account already exists
    val infoMessage by backStackEntry.savedStateHandle
        .getStateFlow<String?>(SIGN_IN_MESSAGE_KEY, null)
        .collectAsState()

    // verifies with Google, then logs in with the server
    fun signIn() {
        isSigningIn = true
        errorText = null
        noticeText = null
        app.scope.launch {
            try {
                val googleToken = triggerGoogleSignIn(context)
                if (googleToken == null) {
                    errorText = "Google sign in failed. Make sure a Google account is added to this device"
                } else {
                    when (val result = login(googleToken)) {
                        is LoginResult.SignedIn -> app.completeSignIn(result.jwtToken)
                        LoginResult.NoUser -> noticeText = "No account found for this Google account. Please sign up first."
                        LoginResult.Failed -> errorText = "Unexpected error occurred. Try again."
                    }
                }
            } finally {
                isSigningIn = false
            }
        }
    }

    SignInScreen(
        isSigningIn = isSigningIn,
        onSignIn = { signIn() },
        onNavigateToSignUp = {
            backStackEntry.savedStateHandle[SIGN_IN_MESSAGE_KEY] = null
            app.navController.navigate(Routes.SIGN_UP)
        },
        modifier = modifier,
        errorText = errorText,
        noticeText = noticeText ?: infoMessage
    )
}

@Composable
fun SignUpRoute(app: AppState, modifier: Modifier) {
    val context = LocalContext.current
    val navController = app.navController
    var idToken by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf<String?>(null) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf<Int?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // verifies with Google and prefills the form with the name on the Google account
    fun verify() {
        isVerifying = true
        errorText = null
        app.scope.launch {
            val token = try {
                triggerGoogleSignIn(context)
            } finally {
                isVerifying = false
            }
            if (token == null) {
                errorText = "Google verification failed. Try again."
            } else {
                val profile = readGoogleProfile(token)
                idToken = token
                email = profile.email
                if (firstName.isBlank()) firstName = profile.givenName ?: ""
                if (lastName.isBlank()) lastName = profile.familyName ?: ""
            }
        }
    }

    fun createAccount() {
        val token = idToken ?: return
        val year = birthYear ?: return
        val details = SignUpDetails(
            idToken = token,
            email = email,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            birthYear = year
        )
        isSubmitting = true
        errorText = null
        app.scope.launch {
            val result = try {
                signUp(details)
            } finally {
                isSubmitting = false
            }
            when (result) {
                is SignUpResult.Created -> app.completeSignIn(result.jwtToken)
                SignUpResult.AlreadyExists -> {
                    // go back to sign in and tell the user to sign in instead
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        SIGN_IN_MESSAGE_KEY,
                        "An account already exists for this Google account. Please sign in."
                    )
                    navController.popBackStack()
                }
                is SignUpResult.Failed -> errorText = result.message
            }
        }
    }

    SignUpScreen(
        state = SignUpUiState(
            email = email,
            isVerified = idToken != null,
            firstName = firstName,
            lastName = lastName,
            birthYear = birthYear,
            isVerifying = isVerifying,
            isSubmitting = isSubmitting,
            errorText = errorText
        ),
        actions = SignUpActions(
            onVerify = { verify() },
            onFirstNameChange = { firstName = it },
            onLastNameChange = { lastName = it },
            onBirthYearChange = { birthYear = it },
            onCreateAccount = { createAccount() },
            onNavigateToSignIn = { navController.popBackStack() },
            onBack = { navController.popBackStack() }
        ),
        modifier = modifier
    )
}
