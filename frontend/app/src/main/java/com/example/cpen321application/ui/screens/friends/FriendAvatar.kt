package com.example.cpen321application.ui.screens.friends

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.cpen321application.ui.components.InitialAvatar

// a person's initial in a circle, on the Friends and Friend Requests screens.
// With onClick the circle can be tapped to open the person's profile
@Composable
internal fun FriendAvatar(initial: String, onClick: (() -> Unit)? = null) {
    val tapToOpen = onClick?.let { Modifier.clickable(onClickLabel = "View profile", onClick = it) } ?: Modifier
    InitialAvatar(initial, modifier = tapToOpen)
}
