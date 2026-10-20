package com.example.cpen321application.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.cpen321application.auth.triggerGoogleSignIn
import com.example.cpen321application.rememberSafeCoroutineScope
import com.example.cpen321application.network.LoginRequest
import com.example.cpen321application.network.ServerApi
import com.example.cpen321application.network.readAuthResponse
import com.google.gson.JsonParseException
import java.io.IOException

/*
    Sign in screen for SportsNow:
    Sign In verifies the user with Google and logs in with the backend, then
    calls onSignedIn with the server's JWT. Sign Up calls onNavigateToSignUp
    to go to the sign up page
 */
@Composable
fun SignInScreen(
    onSignedIn: (jwtToken: String) -> Unit,
    onNavigateToSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    infoMessage: String? = null
) {
    val context = LocalContext.current
    val scope = rememberSafeCoroutineScope()
    var statusText by remember { mutableStateOf<String?>(null) }
    var noticeText by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "SportsNow",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.weight(1f))

        // shows loading and any sign in messages above the sign in heading,
        // min height keeps the heading from jumping when a message appears
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else if (statusText != null) {
                Text(
                    text = statusText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            } else if (noticeText != null || infoMessage != null) {
                // e.g. no account yet, or sent back from sign up because the account already exists
                Text(
                    text = noticeText ?: infoMessage!!,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Text(
            text = "Sign Into the SportsNow Account",
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Verify with Google to log into Account",
            color = Color.Black,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))
        SignInPillButton(
            text = "Sign In",
            enabled = !isSigningIn,
            onClick = {
                isSigningIn = true
                statusText = null
                noticeText = null
                scope.launch {
                    try {
                        val googleToken = triggerGoogleSignIn(context)
                        if (googleToken == null) {
                            statusText = "Google sign in failed. Make sure a Google account " +
                                    "is added to this device"
                        } else {
                            // valid token, log in with the server
                            when (val result = submitLogin(googleToken)) {
                                is LoginResult.SignedIn -> onSignedIn(result.jwtToken)
                                LoginResult.NoUser -> noticeText =
                                    "No account found for this Google account. Please sign up first."
                                LoginResult.Failed -> statusText = "Unexpected error occurred. Try again."
                            }
                        }
                    } finally {
                        isSigningIn = false
                    }
                }
            }
        )

        // divider with "or Create a New Account" in the middle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFD9D9D9))
            Text(
                text = "or Create a New Account",
                color = Color(0xFF757575),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFD9D9D9))
        }

        SignInPillButton(
            text = "Sign Up",
            enabled = !isSigningIn,
            onClick = onNavigateToSignUp
        )

        Spacer(modifier = Modifier.weight(1.4f))
    }
}

// white rounded button with a shadow used on the sign in screen
@Composable
private fun SignInPillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp)
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// outcome of logging in with the server
private sealed interface LoginResult {
    data class SignedIn(val jwtToken: String) : LoginResult
    data object NoUser : LoginResult
    data object Failed : LoginResult
}

/*  sends the Google id token to POST api/auth/login.
    The server answers msg "signedIn" with a jwt_token when the user exists and
    msg "no_user" (status 404) when they need to sign up first
 */
private suspend fun submitLogin(googleToken: String): LoginResult {
    return try {
        val response = ServerApi.retrofitService.login(LoginRequest(idToken = googleToken))
        val body = readAuthResponse(response)

        when {
            body?.msg == "signedIn" && body.jwtToken != null -> LoginResult.SignedIn(body.jwtToken)
            body?.msg == "no_user" -> LoginResult.NoUser
            else -> LoginResult.Failed
        }
    } catch (e: IOException) {
        LoginResult.Failed
    } catch (e: JsonParseException) {
        LoginResult.Failed
    }
}
