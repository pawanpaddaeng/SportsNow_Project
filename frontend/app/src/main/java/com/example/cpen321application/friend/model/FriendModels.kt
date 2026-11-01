package com.example.cpen321application.friend.model

// someone on the Friends screen: a friend or a search result
data class FriendPerson(val userId: Int, val name: String) {
    val initial get() = name.take(1).uppercase()
}

// a friend request sent to this user, waiting for Accept or Decline
data class FriendRequestItem(val requestId: Int, val sender: FriendPerson)
