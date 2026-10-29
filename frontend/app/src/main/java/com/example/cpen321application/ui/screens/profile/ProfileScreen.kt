package com.example.cpen321application.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.LabelValueRow
import com.example.cpen321application.ui.components.SurfaceCard
import com.example.cpen321application.ui.theme.SportsOnPrimaryContainer
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.example.cpen321application.ui.theme.SportsPrimaryContainer
import com.example.cpen321application.user.repository.UserProfile

// width of the labels in the details card, so the values line up
private val DETAIL_LABEL_WIDTH = 104.dp

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onEdit: () -> Unit = {},
    onRetry: () -> Unit = {},
    onTabSelected: (BottomTab) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(selectedTab = BottomTab.Profile, onTabSelected = onTabSelected)
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            ProfileHeader(canEdit = profile != null, onEdit = onEdit)

            if (isLoading) {
                LinearProgressIndicator(
                    color = SportsPrimary,
                    trackColor = SportsPrimaryContainer,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (profile == null) {
                // nothing to show yet: wait for the first load, or offer to try again
                if (errorMessage != null) ProfileLoadError(message = errorMessage, onRetry = onRetry)
            } else {
                if (errorMessage != null) ErrorText(errorMessage)
                ProfileIdentity(profile)
                ProfileDetailsCard(profile)
            }
        }
    }
}

@Composable
private fun ProfileHeader(canEdit: Boolean, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Profile",
            color = SportsOnSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onEdit, enabled = canEdit) {
            Text(
                text = "Edit",
                color = if (canEdit) SportsPrimary else SportsOnSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProfileIdentity(profile: UserProfile) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(88.dp)
                .clip(CircleShape)
                .background(SportsPrimaryContainer)
        ) {
            Text(
                text = initialsOf(profile),
                color = SportsOnPrimaryContainer,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = "${profile.firstName} ${profile.lastName}",
            color = SportsOnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(text = profile.email, color = SportsOnSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun ProfileDetailsCard(profile: UserProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SurfaceCard {
            LabelValueRow(label = "First name", value = profile.firstName, labelWidth = DETAIL_LABEL_WIDTH)
            LabelValueRow(label = "Last name", value = profile.lastName, labelWidth = DETAIL_LABEL_WIDTH)
            LabelValueRow(label = "Birth year", value = profile.birthYear.toString(), labelWidth = DETAIL_LABEL_WIDTH)
            profile.memberSince?.let { LabelValueRow(label = "Member since", value = it, labelWidth = DETAIL_LABEL_WIDTH) }
        }
        Text(
            text = "Your birth year is private. Other players only see your age range.",
            color = SportsOnSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ProfileLoadError(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
    ) {
        ErrorText(message)
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(percent = 50)) {
            Text(text = "Try again", color = SportsPrimary)
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

private fun initialsOf(profile: UserProfile): String =
    listOf(profile.firstName, profile.lastName)
        .mapNotNull { name -> name.firstOrNull()?.uppercaseChar() }
        .joinToString("")