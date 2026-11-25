package com.example.cpen321application.core.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/*  a push notification's data while the app is open: type is what happened
    (e.g. "join_request", "join_request_approved") and gameId the game it's about
 */
data class GameEvent(val type: String, val gameId: Int)

/*
    Passes push notifications that arrive while the app is open to the screens
    showing that game, so they reload without the user doing anything (e.g. the
    Join Requests list when someone asks to join)
 */
object GameEvents {
    // dropped if nobody is listening, screens reload on their own when shown again
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events

    // a message in a game's chat doesn't change the game, so screens about it don't reload
    private const val CHAT_MESSAGE_TYPE = "game_message"

    // called by PushNotificationService for every notification with a type and gameId
    fun publish(data: Map<String, String>) {
        val type = data["type"]?.takeIf { it != CHAT_MESSAGE_TYPE } ?: return
        val gameId = data["gameId"]?.toIntOrNull() ?: return
        _events.tryEmit(GameEvent(type, gameId))
    }
}
