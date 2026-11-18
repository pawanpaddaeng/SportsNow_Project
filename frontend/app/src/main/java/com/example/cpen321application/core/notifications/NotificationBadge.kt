package com.example.cpen321application.core.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/*
    The number on the Notifications tab's bell: unread notifications plus friend requests
    waiting for an answer. Kept here so the bottom bar on every screen can show it without
    each screen passing it along. NotificationsState updates it
 */
object NotificationBadge {
    var count by mutableIntStateOf(0)
}
