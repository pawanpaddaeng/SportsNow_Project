package com.example.cpen321application.friend.dto

import com.example.cpen321application.core.network.Required
import com.google.gson.annotations.SerializedName

// every friends reply: msg "success", otherwise an error code with error
interface FriendReplyDto {
    val msg: String?
    val error: String?
}

// a friend or search result as the server sends it
data class FriendUserDto(
    @SerializedName("user_id") val userId: Int,
    @field:Required @SerializedName("first_name") val firstName: String,
    @field:Required @SerializedName("last_name") val lastName: String,
    // search results only: "none", "friends", "request_sent" or "request_received"
    @SerializedName("friendship_status") val friendshipStatus: String? = null
)

data class FriendRequestDto(
    @SerializedName("request_id") val requestId: Int,
    @field:Required val sender: FriendUserDto
)

// reply from GET api/users/search
data class UserSearchResponse(
    override val msg: String?,
    val users: List<FriendUserDto>?,
    override val error: String?
) : FriendReplyDto

// reply from GET api/friends
data class FriendsResponse(
    override val msg: String?,
    val friends: List<FriendUserDto>?,
    override val error: String?
) : FriendReplyDto

// reply from GET api/friends/requests
data class FriendRequestsResponse(
    override val msg: String?,
    val requests: List<FriendRequestDto>?,
    override val error: String?
) : FriendReplyDto

// reply from POST api/friends/requests and PATCH api/friends/requests/{requestId}
data class FriendChangeResponse(
    override val msg: String?,
    override val error: String?
) : FriendReplyDto

// body for POST api/friends/requests, the user to send the request to
data class SendFriendRequestBody(@SerializedName("user_id") val userId: Int)

// body for PATCH api/friends/requests/{requestId}, decision is "accept" or "decline"
data class FriendDecisionBody(val decision: String)
