package com.example.cpen321application.friend.model

import com.example.cpen321application.friend.dto.FriendRequestDto
import com.example.cpen321application.friend.dto.FriendUserDto

// someone on the Friends screen: a friend or a search result
data class FriendPerson(val userId: Int, val name: String) {
    val initial get() = name.take(1).uppercase()
}

// a friend request sent to this user, waiting for Accept or Decline
data class FriendRequestItem(val requestId: Int, val sender: FriendPerson)

fun FriendUserDto.toFriendPerson() = FriendPerson(userId = userId, name = "$firstName $lastName".trim())

fun FriendRequestDto.toFriendRequestItem() = FriendRequestItem(requestId = requestId, sender = sender.toFriendPerson())
