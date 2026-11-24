package com.example.cpen321application.ui.components

import androidx.compose.foundation.background
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

/*  the 40dp tinted circle used as an avatar on cards and lists, with content centred in it.
    modifier is applied after the circle's clip, so e.g. a clickable ripple stays round
 */
@Composable
fun AvatarCircle(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SportsPrimaryContainer)
            .then(modifier)
    ) {
        content()
    }
}

// a person's or sport's initial in an AvatarCircle
@Composable
fun InitialAvatar(initial: String, modifier: Modifier = Modifier) {
    AvatarCircle(modifier) {
        Text(text = initial, color = SportsOnPrimaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
