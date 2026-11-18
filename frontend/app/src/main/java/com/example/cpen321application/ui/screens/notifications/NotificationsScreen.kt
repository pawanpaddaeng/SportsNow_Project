package com.example.cpen321application.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.notification.model.AppNotification
import com.example.cpen321application.notification.model.NotificationType
import com.example.cpen321application.notification.model.notificationAge
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.CardRow
import com.example.cpen321application.ui.components.LazyTabScreen
import com.example.cpen321application.ui.components.ListNote
import com.example.cpen321application.ui.components.PeopleIcon
import com.example.cpen321application.ui.components.badgeText
import com.example.cpen321application.ui.components.cardBackground
import com.example.cpen321application.ui.components.loadStatus
import com.example.cpen321application.ui.components.pillBackground
import com.example.cpen321application.ui.theme.*
import java.time.Instant

/*  what the Notifications screen shows. friendRequestCount is shown on the Friend requests
    button at the top. isLoading shows a loading bar and errorMessage a problem.
    now is when the ages ("5m", "2h") are worked out from
 */
data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    val friendRequestCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val now: Instant = Instant.now()
)

// what the Notifications screen's controls do
data class NotificationsActions(
    val onRetry: (() -> Unit)? = null,
    val onOpenFriendRequests: () -> Unit = {},
    val onOpenNotification: (AppNotification) -> Unit = {},
    val onTabSelected: (BottomTab) -> Unit = {}
)

/*
    Notifications tab: a Friend requests button at the top (like Instagram's), then what
    happened to the user, newest first. Unread ones have a dot and a tinted card. Tapping
    one opens where the user can act on it, through AppNavigation
 */
@Composable
fun NotificationsScreen(state: NotificationsUiState, actions: NotificationsActions, modifier: Modifier = Modifier) {
    LazyTabScreen(selectedTab = BottomTab.Notifications, onTabSelected = actions.onTabSelected, modifier = modifier) {
        item(key = "title") {
            Text(text = "Notifications", color = SportsOnSurface, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
        item(key = "friendRequests") {
            FriendRequestsButton(count = state.friendRequestCount, onClick = actions.onOpenFriendRequests)
        }

        loadStatus(isLoading = state.isLoading, errorMessage = state.errorMessage, onRetry = actions.onRetry)

        items(state.notifications, key = { "notification${it.notificationId}" }) { notification ->
            NotificationCard(
                notification = notification,
                age = notificationAge(notification.createdAt, state.now),
                onClick = { actions.onOpenNotification(notification) }
            )
        }
        if (state.notifications.isEmpty() && !state.isLoading && state.errorMessage == null) {
            item(key = "empty") {
                ListNote("No notifications yet. Join requests, invites and game reminders show up here.")
            }
        }
    }
}

// the way to the Friend requests screen, with how many are waiting
@Composable
private fun FriendRequestsButton(count: Int, onClick: () -> Unit) {
    CardRow(
        modifier = Modifier
            .cardBackground(SportsSurfaceContainer)
            .border(1.dp, SportsOutlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Open friend requests", onClick = onClick)
    ) {
        TypeIcon(PeopleIcon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "Friend requests", color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = if (count == 0) "No requests waiting" else "Accept or decline people who added you",
                color = SportsOnSurfaceVariant,
                fontSize = 12.sp
            )
        }
        if (count > 0) {
            Text(
                text = badgeText(count),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.pillBackground(SportsPrimary, borderColor = null)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SportsOnSurfaceVariant
        )
    }
}

@Composable
private fun NotificationCard(notification: AppNotification, age: String, onClick: () -> Unit) {
    val opensSomething = notification.type != NotificationType.Other
    val tapToOpen = if (opensSomething) Modifier.clickable(onClick = onClick) else Modifier
    CardRow(
        modifier = Modifier
            .cardBackground(if (notification.isRead) Color.White else SportsSurfaceContainer)
            .border(1.dp, SportsOutlineVariant, RoundedCornerShape(12.dp))
            .then(tapToOpen)
    ) {
        TypeIcon(iconFor(notification.type))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = notification.title,
                color = SportsOnSurface,
                fontSize = 14.sp,
                fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.SemiBold
            )
            Text(
                text = notification.body,
                color = SportsOnSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = age, color = SportsOnSurfaceVariant, fontSize = 12.sp)
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SportsPrimary)
                )
            }
        }
    }
}

// the notification's icon in a circle, like the avatars on the Friends screen
@Composable
private fun TypeIcon(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SportsPrimaryContainer)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SportsOnPrimaryContainer, modifier = Modifier.size(20.dp))
    }
}

private fun iconFor(type: NotificationType): ImageVector = when (type) {
    NotificationType.FriendRequestAccepted -> PeopleIcon
    NotificationType.JoinRequest -> Icons.Filled.Person
    NotificationType.JoinRequestApproved -> Icons.Filled.Check
    NotificationType.JoinRequestDeclined -> Icons.Filled.Close
    NotificationType.GameInvite -> Icons.Filled.Email
    NotificationType.GameStarting, NotificationType.Other -> Icons.Filled.Notifications
}
