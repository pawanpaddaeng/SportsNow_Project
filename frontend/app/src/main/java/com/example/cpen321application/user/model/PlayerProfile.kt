package com.example.cpen321application.user.model

import com.example.cpen321application.friend.model.FriendshipStatus
import com.example.cpen321application.friend.model.friendshipStatusOf
import com.example.cpen321application.user.dto.PublicProfileDto
import java.util.Locale

data class PlayerProfile(
    val userId: Int,
    val name: String,
    val ageRange: String,
    val gamesPlayed: Int,
    val reputationScore: Double?,
    val badges: List<String>,
    val isSelf: Boolean,
    val friendship: FriendshipStatus
) {
    val initial get() = name.take(1).uppercase(Locale.getDefault())
}

fun PublicProfileDto.toPlayerProfile() = PlayerProfile(
    userId = userId,
    name = "$firstName $lastName".trim(),
    ageRange = ageRange,
    gamesPlayed = gamesPlayed,
    reputationScore = reputationScore,
    badges = badges.orEmpty(),
    isSelf = friendshipStatus == "self",
    friendship = friendshipStatusOf(friendshipStatus)
)