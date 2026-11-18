package com.example.cpen321application.notification.model

import com.example.cpen321application.notification.dto.NotificationDto
import com.example.cpen321application.notification.dto.NotificationsResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// what a notification is about, it decides the icon and the screen a tap opens
enum class NotificationType {
    FriendRequestAccepted, JoinRequest, JoinRequestApproved, JoinRequestDeclined, GameInvite, GameStarting,

    // a type this version of the app doesn't know yet, shown but opens nothing
    Other
}

fun notificationTypeOf(value: String): NotificationType = when (value) {
    "friend_request_accepted" -> NotificationType.FriendRequestAccepted
    "join_request" -> NotificationType.JoinRequest
    "join_request_approved" -> NotificationType.JoinRequestApproved
    "join_request_declined" -> NotificationType.JoinRequestDeclined
    "game_invite" -> NotificationType.GameInvite
    "game_starting" -> NotificationType.GameStarting
    else -> NotificationType.Other
}

// one row of the Notifications screen
data class AppNotification(
    val notificationId: Int,
    val type: NotificationType,
    val title: String,
    val body: String,
    // the game it's about, null for friend notifications
    val gameId: Int?,
    // null if the server's time couldn't be read
    val createdAt: Instant?,
    val isRead: Boolean
)

// what GET api/notifications returns: the list and the counts for the badge and the Friend requests button
data class NotificationsPage(
    val notifications: List<AppNotification>,
    val unreadCount: Int,
    val friendRequestCount: Int
)

fun NotificationDto.toAppNotification() = AppNotification(
    notificationId = notificationId,
    type = notificationTypeOf(type),
    title = title,
    body = body,
    gameId = gameId,
    createdAt = try {
        Instant.parse(createdAt)
    } catch (e: DateTimeParseException) {
        null
    },
    isRead = isRead
)

fun NotificationsResponse.toNotificationsPage() = NotificationsPage(
    notifications = notifications.orEmpty().map { it.toAppNotification() },
    unreadCount = unreadCount ?: 0,
    friendRequestCount = friendRequestCount ?: 0
)

private val olderFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

// older than this shows the date instead of weeks
private const val WEEKS_BEFORE_DATE = 4

// how long ago, like Instagram: "now", "5m", "3h", "2d", "1w", then the date ("Oct 17")
fun notificationAge(createdAt: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    if (createdAt == null) return ""
    val minutes = Duration.between(createdAt, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < MINUTES_PER_HOUR -> "${minutes}m"
        minutes < MINUTES_PER_DAY -> "${minutes / MINUTES_PER_HOUR}h"
        minutes < MINUTES_PER_WEEK -> "${minutes / MINUTES_PER_DAY}d"
        minutes < WEEKS_BEFORE_DATE * MINUTES_PER_WEEK -> "${minutes / MINUTES_PER_WEEK}w"
        else -> createdAt.atZone(zone).format(olderFormat)
    }
}
