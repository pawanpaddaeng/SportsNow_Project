package com.example.cpen321application.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.components.BirthYearField
import com.example.cpen321application.ui.components.LabeledTextField
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.example.cpen321application.ui.theme.SportsSurfaceContainer
import com.example.cpen321application.users.ProfileCheck
import com.example.cpen321application.users.ProfileForm
import com.example.cpen321application.users.UserProfile
import com.example.cpen321application.users.checkProfile

@Composable
fun EditProfileScreen(
    profile: UserProfile,
    onSave: (ProfileForm) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    errorMessage: String? = null
) {
    var firstNameText by rememberSaveable { mutableStateOf(profile.firstName) }
    var lastNameText by rememberSaveable { mutableStateOf(profile.lastName) }
    var birthYear by rememberSaveable { mutableStateOf<Int?>(profile.birthYear) }
    var formError by remember { mutableStateOf<String?>(null) }

    val hasChanges = firstNameText.trim() != profile.firstName ||
            lastNameText.trim() != profile.lastName || birthYear != profile.birthYear

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        EditProfileTopBar(onBack = onCancel)
        GoogleAccountCard(email = profile.email)

        ProfileFormFields(
            values = ProfileFieldValues(firstNameText, lastNameText, birthYear),
            onChange = { changed ->
                firstNameText = changed.firstName
                lastNameText = changed.lastName
                birthYear = changed.birthYear
                formError = null
            }
        )

        (formError ?: errorMessage)?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        EditProfileButtons(
            canSave = hasChanges && !isSaving,
            isSaving = isSaving,
            onSave = {
                val check = checkProfile(firstNameText, lastNameText, birthYear)
                formError = (check as? ProfileCheck.Invalid)?.message
                if (check is ProfileCheck.Valid) onSave(check.form)
            },
            onCancel = onCancel
        )
    }
}

private data class ProfileFieldValues(
    val firstName: String,
    val lastName: String,
    val birthYear: Int?
)

@Composable
private fun ProfileFormFields(values: ProfileFieldValues, onChange: (ProfileFieldValues) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledTextField(
                label = "First name *",
                value = values.firstName,
                onValueChange = { onChange(values.copy(firstName = it)) },
                modifier = Modifier.weight(1f)
            )
            LabeledTextField(
                label = "Last name *",
                value = values.lastName,
                onValueChange = { onChange(values.copy(lastName = it)) },
                modifier = Modifier.weight(1f)
            )
        }
        BirthYearField(
            birthYear = values.birthYear,
            onYearSelected = { onChange(values.copy(birthYear = it)) }
        )
    }
}

@Composable
private fun EditProfileTopBar(onBack: () -> Unit) {
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
            text = "Edit profile",
            color = SportsOnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GoogleAccountCard(email: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportsSurfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Verified with Google",
                color = SportsOnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(text = email, color = SportsOnSurfaceVariant, fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Verified",
            tint = Color.White,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(SportsPrimary)
                .padding(4.dp)
        )
    }
}

@Composable
private fun EditProfileButtons(
    canSave: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
    ) {
        Button(
            onClick = onSave,
            enabled = canSave,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(text = "Save changes", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !isSaving,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(text = "Cancel", color = SportsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

