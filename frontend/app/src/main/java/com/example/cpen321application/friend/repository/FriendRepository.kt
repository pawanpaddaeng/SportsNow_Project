package com.example.cpen321application.friend.repository

import android.content.Context
import com.example.cpen321application.core.NETWORK_MESSAGE
import com.example.cpen321application.core.SESSION_ENDED_MESSAGE
import com.example.cpen321application.core.network.ServerApi
import com.example.cpen321application.core.network.readReply
import com.example.cpen321application.core.session.Session
import com.example.cpen321application.friend.dto.FriendChangeResponse
import com.example.cpen321application.friend.dto.FriendDecisionBody
import com.example.cpen321application.friend.dto.FriendReplyDto
import com.example.cpen321application.friend.dto.FriendRequestsResponse
import com.example.cpen321application.friend.dto.FriendsResponse
import com.example.cpen321application.friend.dto.SendFriendRequestBody
import com.example.cpen321application.friend.dto.UserSearchResponse
import com.example.cpen321application.friend.model.FriendPerson
import com.example.cpen321application.friend.model.FriendRequestItem
import com.example.cpen321application.friend.model.toFriendPerson
import com.example.cpen321application.friend.model.toFriendRequestItem
import com.google.gson.JsonParseException
import retrofit2.Response
import java.io.IOException

sealed interface FriendResult<out T> {
    data class Done<T>(val value: T) : FriendResult<T>
    data class Failed(val message: String) : FriendResult<Nothing>
}

/*  sends a request and reads the reply. The server's error is shown for problems the user
    can act on (already friends, ...), server errors get failureMessage instead
 */
private suspend fun <R : FriendReplyDto, T> askServer(
    context: Context,
    failureMessage: String,
    replyType: Class<R>,
    request: suspend (authorization: String) -> Response<R>,
    readValue: (R) -> T
): FriendResult<T> {
    return try {
        val response = Session.call(context, request) ?: return FriendResult.Failed(SESSION_ENDED_MESSAGE)
        val reply = readReply(response, replyType)
        if (reply?.msg == "success") {
            FriendResult.Done(readValue(reply))
        } else {
            val serverError = reply?.error.takeIf { reply?.msg != null && reply.msg != "server_error" }
            FriendResult.Failed(serverError ?: failureMessage)
        }
    } catch (e: IOException) {
        FriendResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        FriendResult.Failed(failureMessage)
    }
}

suspend fun searchUsers(context: Context, text: String): FriendResult<List<FriendPerson>> =
    askServer(
        context,
        "Couldn't search for people, try again",
        UserSearchResponse::class.java,
        { authorization -> ServerApi.friendService.searchUsers(authorization, text) },
        { reply -> reply.users.orEmpty().map { it.toFriendPerson() } }
    )

suspend fun fetchFriends(context: Context): FriendResult<List<FriendPerson>> =
    askServer(
        context,
        "Couldn't load your friends, try again",
        FriendsResponse::class.java,
        { authorization -> ServerApi.friendService.getFriends(authorization) },
        { reply -> reply.friends.orEmpty().map { it.toFriendPerson() } }
    )

suspend fun fetchFriendRequests(context: Context): FriendResult<List<FriendRequestItem>> =
    askServer(
        context,
        "Couldn't load your friend requests, try again",
        FriendRequestsResponse::class.java,
        { authorization -> ServerApi.friendService.getFriendRequests(authorization) },
        { reply -> reply.requests.orEmpty().map { it.toFriendRequestItem() } }
    )

suspend fun sendFriendRequest(context: Context, userId: Int): FriendResult<Unit> =
    askServer(
        context,
        "Couldn't send your friend request, try again",
        FriendChangeResponse::class.java,
        { authorization -> ServerApi.friendService.sendFriendRequest(authorization, SendFriendRequestBody(userId)) },
        { }
    )

suspend fun decideFriendRequest(context: Context, requestId: Int, accept: Boolean): FriendResult<Unit> {
    val body = FriendDecisionBody(decision = if (accept) "accept" else "decline")
    return askServer(
        context,
        "Couldn't save your answer, try again",
        FriendChangeResponse::class.java,
        { authorization -> ServerApi.friendService.decideFriendRequest(authorization, requestId, body) },
        { }
    )
}
