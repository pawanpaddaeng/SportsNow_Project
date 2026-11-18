package com.example.cpen321application.navigation

// notifications about a game that open it when tapped
private val GAME_OPENING_TYPES = setOf(
    "game_invite",
    "game_starting",
    "join_request_approved",
    "join_request_declined",
    "rating_reminder",
    "game_updated",
    "game_cancelled",
    "game_completed",
    "player_left"
)

/*  the screen a tapped push notification opens, from its data (the intent's extras):
    friend requests open Friend Requests, a new friend the Friends tab, a friend's message
    their chat, a join request the organizer's Join Requests and other game notifications
    the game. Null when there's nothing to open (e.g. being removed from a game) or the
    data is missing, then the app just opens where it was
 */
fun notificationRoute(extra: (key: String) -> String?): String? {
    val gameId = extra("gameId")?.toIntOrNull()
    return when (val type = extra("type")) {
        "friend_request" -> Routes.FRIEND_REQUESTS
        "friend_request_accepted" -> Routes.FRIENDS
        "friend_message" -> extra("chatId")?.toIntOrNull()?.let { Routes.friendChat(it, extra("friendName").orEmpty()) }
        "join_request" -> gameId?.let(Routes::joinRequests)
        in GAME_OPENING_TYPES -> gameId?.let(Routes::gameDetails)
        else -> null
    }
}
