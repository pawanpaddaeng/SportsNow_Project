package com.example.cpen321application.core.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/*
    Passes friend request notifications that arrive while the app is open to the
    Friends tab, so its lists reload without the user doing anything
 */
object FriendEvents {
    // dropped if nobody is listening, the tab reloads on its own when shown again
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val events: SharedFlow<Unit> = _events

    // called by PushNotificationService for every notification
    fun publish(data: Map<String, String>) {
        if (data["type"]?.startsWith("friend_request") == true) _events.tryEmit(Unit)
    }
}
