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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/*
    Sign in screen for SportsNow: Sign In calls onSignIn, which verifies the user with
    Google and logs in with the backend. errorText is shown in red, otherwise noticeText
    (e.g. no account yet, or sent back from sign up because the account already exists)
 */
@Composable
fun SignInScreen(
    isSigningIn: Boolean,
    onSignIn: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    noticeText: String? = null
) {
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

        SignInMessage(isSigningIn = isSigningIn, errorText = errorText, noticeText = noticeText)
        SignInHeading()

        Spacer(modifier = Modifier.height(24.dp))
        SignInPillButton(
            text = "Sign In",
            enabled = !isSigningIn,
            onClick = onSignIn
        )

        OrDivider()

        SignInPillButton(
            text = "Sign Up",
            enabled = !isSigningIn,
            onClick = onNavigateToSignUp
        )

        Spacer(modifier = Modifier.weight(1.4f))
    }
}

/*  shows loading and any sign in messages above the sign in heading: errorText in red,
    otherwise noticeText. The min height keeps the heading from jumping when a message appears
 */
@Composable
private fun SignInMessage(isSigningIn: Boolean, errorText: String?, noticeText: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isSigningIn) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else if (errorText != null) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        } else if (noticeText != null) {
            Text(
                text = noticeText,
                color = Color.Black,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SignInHeading() {
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
}

// divider with "or Create a New Account" in the middle
@Composable
private fun OrDivider() {
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
