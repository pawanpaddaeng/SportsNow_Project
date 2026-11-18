package com.example.cpen321application.ui.screens.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.theme.SportsOnPrimaryContainer
import com.example.cpen321application.ui.theme.SportsPrimaryContainer

// a person's initial in a circle, on the Friends and Friend Requests screens.
// With onClick the circle can be tapped to open the person's profile
@Composable
internal fun FriendAvatar(initial: String, onClick: (() -> Unit)? = null) {
    val tapToOpen = onClick?.let { Modifier.clickable(onClickLabel = "View profile", onClick = it) } ?: Modifier
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SportsPrimaryContainer)
            .then(tapToOpen)
    ) {
        Text(text = initial, color = SportsOnPrimaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
