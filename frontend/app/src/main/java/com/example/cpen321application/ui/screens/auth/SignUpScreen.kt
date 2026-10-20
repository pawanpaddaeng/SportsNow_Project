package com.example.cpen321application.ui.screens.auth

import android.util.Base64
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
import com.example.cpen321application.auth.triggerGoogleSignIn
import com.example.cpen321application.rememberSafeCoroutineScope
import com.example.cpen321application.network.ServerApi
import com.example.cpen321application.network.SignUpRequest
import com.example.cpen321application.network.readAuthResponse
import com.google.gson.JsonParseException
import com.example.cpen321application.ui.theme.*
import com.example.cpen321application.ui.components.FieldLabel
import com.example.cpen321application.ui.components.LabeledTextField
import com.example.cpen321application.ui.components.sportsFieldColors
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Year

// youngest and oldest ages offered in the birth year picker
private const val MIN_AGE = 19
private const val MAX_AGE = 100

/*  details collected on the sign up screen,
    more profile fields will be added here later
 */
data class SignUpDetails(
    val idToken: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val birthYear: Int
)

/*
    Sign up screen for SportsNow:
    The user verifies with Google (which prefills their name), fills in
    their details and presses Create Account, which sends them to the server.
    onSignedUp gets the details and the server's JWT when the account is created,
    onAlreadyExists is called when the Google account already has an account
 */
@Composable
fun SignUpScreen(
    onSignedUp: (details: SignUpDetails, jwtToken: String) -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // top bar with back button and centered app name
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

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Create your account",
                color = SportsOnSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        GoogleVerificationCard(
            email = email,
            isVerified = idToken != null,
            isVerifying = isVerifying,
            onVerify = {
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
                        // prefill the form with the name on the Google account
                        val profile = readGoogleProfile(token)
                        idToken = token
                        email = profile.email
                        if (firstNameText.isBlank()) firstNameText = profile.givenName ?: ""
                        if (lastNameText.isBlank()) lastNameText = profile.familyName ?: ""
                    }
                }
            }
        )

        // profile fields, new fields get added to this column
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledTextField(
                    label = "First name *",
                    value = firstNameText,
                    onValueChange = { firstNameText = it },
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    label = "Last name *",
                    value = lastNameText,
                    onValueChange = { lastNameText = it },
                    modifier = Modifier.weight(1f)
                )
            }
            BirthYearField(
                birthYear = birthYear,
                onYearSelected = { birthYear = it }
            )
        }

        if (errorText != null) {
            Text(
                text = errorText!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        ) {
            Button(
                onClick = {
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
                            submitSignUp(details)
                        } finally {
                            isSubmitting = false
                        }
                        when (result) {
                            is SignUpResult.Created -> onSignedUp(details, result.jwtToken)
                            SignUpResult.AlreadyExists -> onAlreadyExists()
                            is SignUpResult.Failed -> errorText = result.message
                        }
                    }
                },
                enabled = canCreate,
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
}

// card showing whether the user has verified their Google account yet
@Composable
private fun GoogleVerificationCard(
    email: String?,
    isVerified: Boolean,
    isVerifying: Boolean,
    onVerify: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportsSurfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
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

// name and email read from the Google id token so the form can be prefilled
private data class GoogleProfile(
    val email: String?,
    val givenName: String?,
    val familyName: String?
)

/*  the id token is a JWT, its middle part is base64 encoded JSON with the
    account's profile claims. Only used for prefilling, the backend still
    verifies the token itself
 */
private fun readGoogleProfile(idToken: String): GoogleProfile {
    val payload = idToken.split(".").getOrNull(1) ?: return GoogleProfile(null, null, null)
    return try {
        val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        val json = JSONObject(String(Base64.decode(payload, flags)))
        GoogleProfile(
            email = json.optString("email").ifBlank { null },
            givenName = json.optString("given_name").ifBlank { null },
            familyName = json.optString("family_name").ifBlank { null }
        )
    } catch (e: IllegalArgumentException) {
        // not valid base64
        GoogleProfile(null, null, null)
    } catch (e: JSONException) {
        GoogleProfile(null, null, null)
    }
}

// outcome of sending the sign up form to the server
private sealed interface SignUpResult {
    data class Created(val jwtToken: String) : SignUpResult
    data object AlreadyExists : SignUpResult
    data class Failed(val message: String) : SignUpResult
}

/*  sends the sign up details to POST api/auth/signup.
    The server answers msg "signedIn" with a jwt_token when the account is created
    and msg "already_exists" (status 409) when the Google account already has one
 */
private suspend fun submitSignUp(details: SignUpDetails): SignUpResult {
    val email = details.email
        ?: return SignUpResult.Failed("Could not read the email of your Google account, verify again")

    return try {
        val response = ServerApi.retrofitService.signUp(
            SignUpRequest(
                idToken = details.idToken,
                email = email,
                firstName = details.firstName,
                lastName = details.lastName,
                birthYear = details.birthYear
            )
        )
        val body = readAuthResponse(response)

        when {
            body?.msg == "signedIn" && body.jwtToken != null -> SignUpResult.Created(body.jwtToken)
            body?.msg == "already_exists" || response.code() == 409 -> SignUpResult.AlreadyExists
            body?.msg == "invalid_token" ->
                SignUpResult.Failed("Your Google verification expired, verify with Google again")
            else -> SignUpResult.Failed(body?.error ?: "Could not create account, try again")
        }
    } catch (e: IOException) {
        SignUpResult.Failed("Cannot Reach Server")
    } catch (e: JsonParseException) {
        SignUpResult.Failed("Could not create account, try again")
    }
}
