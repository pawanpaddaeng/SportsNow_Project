package com.example.cpen321application.navigation.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cpen321application.core.notifications.NotificationBadge
import com.example.cpen321application.notification.model.AppNotification
import com.example.cpen321application.notification.repository.NotificationsResult
import com.example.cpen321application.notification.repository.fetchNotifications
import com.example.cpen321application.notification.repository.markNotificationsRead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// the Notifications tab, and the count on its bell in the bottom bar
class NotificationsState(private val context: Context, private val scope: CoroutineScope) {
    var notifications by mutableStateOf(emptyList<AppNotification>())
        private set
    var friendRequestCount by mutableIntStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var loadJob: Job? = null

    /*  loads the notifications and updates the bell. With markRead (the screen is showing)
        unread ones are then marked seen on the server: the bell drops to the friend requests,
        and they keep their unread look until the next load so the user sees which are new.
        On failure the last loaded list stays visible with the error above it
     */
    fun refresh(markRead: Boolean = false) {
        if (isLoading) return
        isLoading = true
        loadJob = scope.launch {
            try {
                when (val result = fetchNotifications(context)) {
                    is NotificationsResult.Loaded -> {
                        val page = result.page
                        notifications = page.notifications
                        friendRequestCount = page.friendRequestCount
                        error = null
                        val seen = markRead && page.unreadCount > 0 && markNotificationsRead(context)
                        NotificationBadge.count = page.friendRequestCount + if (seen) 0 else page.unreadCount
                    }
                    // the error only shows on the Notifications screen, the bell keeps its count
                    is NotificationsResult.Failed -> error = result.message
                }
            } finally {
                isLoading = false
            }
        }
    }

    // also cancels a load in progress so its result can't fill the cleared state
    fun clear() {
        loadJob?.cancel()
        notifications = emptyList()
        friendRequestCount = 0
        isLoading = false
        error = null
        NotificationBadge.count = 0
    }
}
