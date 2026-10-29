package com.example.cpen321application.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.components.BirthYearField
import com.example.cpen321application.ui.components.CardRow
import com.example.cpen321application.ui.components.LabeledTextField
import com.example.cpen321application.ui.components.cardBackground
import com.example.cpen321application.ui.theme.*

/*  what the Sign Up screen shows. email is the verified Google account's email, null
    until the user verifies. errorText is shown above Create Account
 */
data class SignUpUiState(
    val email: String?,
    val isVerified: Boolean,
    val firstName: String,
    val lastName: String,
    val birthYear: Int?,
    val isVerifying: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorText: String? = null
) {
    val canCreate get() = isVerified && firstName.isNotBlank() && lastName.isNotBlank() &&
        birthYear != null && !isSubmitting
}

data class SignUpActions(
    val onVerify: () -> Unit,
    val onFirstNameChange: (String) -> Unit,
    val onLastNameChange: (String) -> Unit,
    val onBirthYearChange: (Int) -> Unit,
    val onCreateAccount: () -> Unit,
    val onNavigateToSignIn: () -> Unit,
    val onBack: () -> Unit
)

/*
    Sign up screen for SportsNow: the user verifies with Google (which prefills their
    name), fills in their details and presses Create Account
 */
@Composable
fun SignUpScreen(state: SignUpUiState, actions: SignUpActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SignUpHeader(onBack = actions.onBack)

        GoogleVerificationCard(
            email = state.email,
            isVerified = state.isVerified,
            isVerifying = state.isVerifying,
            onVerify = actions.onVerify
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledTextField(
                    label = "First name *",
                    value = state.firstName,
                    onValueChange = actions.onFirstNameChange,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    label = "Last name *",
                    value = state.lastName,
                    onValueChange = actions.onLastNameChange,
                    modifier = Modifier.weight(1f)
                )
            }
            BirthYearField(birthYear = state.birthYear, onYearSelected = actions.onBirthYearChange)
        }

        if (state.errorText != null) {
            Text(
                text = state.errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        CreateAccountFooter(
            enabled = state.canCreate,
            isSubmitting = state.isSubmitting,
            onCreate = actions.onCreateAccount,
            onNavigateToSignIn = actions.onNavigateToSignIn
        )
    }
}

// top bar with back button and centered app name, then "Create your account"
@Composable
private fun SignUpHeader(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SportsOnSurface
            )
        }
        Text(
            text = "SportsNow",
            color = SportsOnSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.size(48.dp))
    }

    Text(
        text = "Create your account",
        color = SportsOnSurface,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold
    )
}

// Create Account button (a spinner while sending) and the link back to sign in
@Composable
private fun CreateAccountFooter(
    enabled: Boolean,
    isSubmitting: Boolean,
    onCreate: () -> Unit,
    onNavigateToSignIn: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
    ) {
        Button(
            onClick = onCreate,
            enabled = enabled,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(text = "Create Account", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row {
            Text(
                text = "Already have an account? ",
                color = SportsOnSurfaceVariant,
                fontSize = 14.sp
            )
            Text(
                text = "Sign In",
                color = SportsPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onNavigateToSignIn)
            )
        }
    }
}

// card showing whether the user has verified their Google account yet
@Composable
private fun GoogleVerificationCard(
    email: String?,
    isVerified: Boolean,
    isVerifying: Boolean,
    onVerify: () -> Unit
) {
    CardRow(modifier = Modifier.cardBackground(SportsSurfaceContainer)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Text(text = "G", color = SportsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isVerified) "Verified with Google" else "Verify with Google",
                color = SportsOnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isVerified) email ?: "" else "Required to create your account",
                color = SportsOnSurfaceVariant,
                fontSize = 12.sp
            )
        }

        when {
            isVerified -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(SportsPrimary)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Verified",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            isVerifying -> CircularProgressIndicator(
                color = SportsPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
            else -> TextButton(onClick = onVerify) {
                Text(text = "Verify", color = SportsPrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
}
