package com.example.cpen321application.notification.dto

import com.example.cpen321application.core.network.Required
import com.google.gson.annotations.SerializedName

// a saved notification as the server sends it
data class NotificationDto(
    @SerializedName("notification_id") val notificationId: Int,
    // e.g. "join_request", "game_invite", "game_starting", see NotificationType
    @field:Required val type: String,
    @field:Required val title: String,
    @field:Required val body: String,
    // the game it's about, null for friend notifications
    @SerializedName("game_id") val gameId: Int? = null,
    // UTC ISO 8601, e.g. "2026-11-18T02:00:00Z"
    @field:Required @SerializedName("created_at") val createdAt: String,
    @SerializedName("is_read") val isRead: Boolean = false
)

// reply from GET api/notifications: msg "success", otherwise an error code with error
data class NotificationsResponse(
    val msg: String?,
    val notifications: List<NotificationDto>?,
    // notifications the user hasn't seen on the Notifications screen yet
    @SerializedName("unread_count") val unreadCount: Int?,
    // friend requests waiting for an answer, for the Friend requests button
    @SerializedName("friend_request_count") val friendRequestCount: Int?,
    val error: String?
)

// reply from PATCH api/notifications/read
data class NotificationsReadResponse(val msg: String?, val error: String?)
