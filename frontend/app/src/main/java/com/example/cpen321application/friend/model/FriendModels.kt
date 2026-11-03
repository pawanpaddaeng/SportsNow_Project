package com.example.cpen321application.friend.model

import com.example.cpen321application.friend.dto.FriendRequestDto
import com.example.cpen321application.friend.dto.FriendUserDto

// how a search result is connected to the signed in user
enum class FriendshipStatus { None, Friends, RequestSent, RequestReceived }

// someone on the Friends screen: a friend or a search result
data class FriendPerson(
    val userId: Int,
    val name: String,
    val status: FriendshipStatus = FriendshipStatus.None
) {
    val initial get() = name.take(1).uppercase()
}

// a friend request sent to this user, waiting for Accept or Decline
data class FriendRequestItem(val requestId: Int, val sender: FriendPerson)

fun friendshipStatusOf(value: String?): FriendshipStatus = when (value) {
    "friends" -> FriendshipStatus.Friends
    "request_sent" -> FriendshipStatus.RequestSent
    "request_received" -> FriendshipStatus.RequestReceived
    else -> FriendshipStatus.None
}

fun FriendUserDto.toFriendPerson() = FriendPerson(
    userId = userId,
    name = "$firstName $lastName".trim(),
    status = friendshipStatusOf(friendshipStatus)
)

fun FriendRequestDto.toFriendRequestItem() = FriendRequestItem(requestId = requestId, sender = sender.toFriendPerson())
