package com.example.cpen321application.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.core.rememberSafeCoroutineScope
import com.example.cpen321application.user.repository.SignUpDetails
import com.example.cpen321application.user.repository.SignUpResult
import com.example.cpen321application.user.repository.readGoogleProfile
import com.example.cpen321application.user.repository.signUp
import com.example.cpen321application.user.triggerGoogleSignIn
import com.example.cpen321application.ui.theme.*
import com.example.cpen321application.ui.components.CardRow
import com.example.cpen321application.ui.components.FieldLabel
import com.example.cpen321application.ui.components.cardBackground
import com.example.cpen321application.ui.components.LabeledTextField
import com.example.cpen321application.ui.components.sportsFieldColors
import kotlinx.coroutines.launch
import java.time.Year

// youngest and oldest ages offered in the birth year picker
private const val MIN_AGE = 19
private const val MAX_AGE = 100

/*
    Sign up screen for SportsNow:
    The user verifies with Google (which prefills their name), fills in
    their details and presses Create Account, which sends them to the server.
    onSignedUp gets the server's JWT when the account is created,
    onAlreadyExists is called when the Google account already has an account
 */
@Composable
fun SignUpScreen(
    onSignedUp: (jwtToken: String) -> Unit,
    onAlreadyExists: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberSafeCoroutineScope()

    var idToken by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf<String?>(null) }
    var firstNameText by remember { mutableStateOf("") }
    var lastNameText by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf<Int?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val canCreate = idToken != null && firstNameText.isNotBlank() &&
            lastNameText.isNotBlank() && birthYear != null && !isSubmitting

    // verifies with Google and prefills the form with the name on the Google account
    fun verify() {
        isVerifying = true
        errorText = null
        scope.launch {
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
                if (firstNameText.isBlank()) firstNameText = profile.givenName ?: ""
                if (lastNameText.isBlank()) lastNameText = profile.familyName ?: ""
            }
        }
    }

    // sends the form to the server, only called once canCreate is true
    fun createAccount() {
        val details = SignUpDetails(
            idToken = idToken!!,
            email = email,
            firstName = firstNameText.trim(),
            lastName = lastNameText.trim(),
            birthYear = birthYear!!
        )
        isSubmitting = true
        errorText = null
        scope.launch {
            val result = try {
                signUp(details)
            } finally {
                isSubmitting = false
            }
            when (result) {
                is SignUpResult.Created -> onSignedUp(result.jwtToken)
                SignUpResult.AlreadyExists -> onAlreadyExists()
                is SignUpResult.Failed -> errorText = result.message
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SignUpHeader(onBack = onBack)

        GoogleVerificationCard(
            email = email,
            isVerified = idToken != null,
            isVerifying = isVerifying,
            onVerify = { verify() }
        )

        NameAndBirthYearFields(
            firstName = firstNameText,
            lastName = lastNameText,
            birthYear = birthYear,
            onFirstNameChange = { firstNameText = it },
            onLastNameChange = { lastNameText = it },
            onBirthYearChange = { birthYear = it }
        )

        if (errorText != null) {
            Text(
                text = errorText!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        CreateAccountFooter(
            enabled = canCreate,
            isSubmitting = isSubmitting,
            onCreate = { createAccount() },
            onNavigateToSignIn = onNavigateToSignIn
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

// profile fields, new fields get added to this column
@Composable
private fun NameAndBirthYearFields(
    firstName: String,
    lastName: String,
    birthYear: Int?,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onBirthYearChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledTextField(
                label = "First name *",
                value = firstName,
                onValueChange = onFirstNameChange,
                modifier = Modifier.weight(1f)
            )
            LabeledTextField(
                label = "Last name *",
                value = lastName,
                onValueChange = onLastNameChange,
                modifier = Modifier.weight(1f)
            )
        }
        BirthYearField(
            birthYear = birthYear,
            onYearSelected = onBirthYearChange
        )
    }
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

// read only field that opens a dropdown of birth years
@Composable
private fun BirthYearField(
    birthYear: Int?,
    onYearSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentYear = Year.now().value
    val years = remember(currentYear) { (currentYear - MIN_AGE downTo currentYear - MAX_AGE).toList() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel("Birth year *")
        Box {
            OutlinedTextField(
                value = birthYear?.toString() ?: "",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                placeholder = { Text("Select year") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                shape = RoundedCornerShape(8.dp),
                colors = sportsFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            // covers the field so a tap anywhere opens the menu
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                years.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString()) },
                        onClick = {
                            onYearSelected(year)
                            expanded = false
                        }
                    )
                }
            }
        }
        Text(
            text = "Only your age range is shown to other players.",
            color = SportsOnSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
