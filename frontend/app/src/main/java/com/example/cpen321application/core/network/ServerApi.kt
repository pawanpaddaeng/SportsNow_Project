package com.example.cpen321application.core.network

import com.example.cpen321application.BuildConfig
import com.example.cpen321application.chat.dto.ChatMessagesResponse
import com.example.cpen321application.friend.dto.FriendChangeResponse
import com.example.cpen321application.friend.dto.FriendDecisionBody
import com.example.cpen321application.friend.dto.FriendRequestsResponse
import com.example.cpen321application.friend.dto.FriendsResponse
import com.example.cpen321application.friend.dto.SendFriendRequestBody
import com.example.cpen321application.friend.dto.UserSearchResponse
import com.example.cpen321application.game.dto.CreateGameRequest
import com.example.cpen321application.game.dto.CreateGameResponse
import com.example.cpen321application.game.dto.DiscoverGamesResponse
import com.example.cpen321application.game.dto.GameDetailsResponse
import com.example.cpen321application.game.dto.InviteFriendsBody
import com.example.cpen321application.game.dto.InviteFriendsResponse
import com.example.cpen321application.game.dto.JoinRequestBody
import com.example.cpen321application.game.dto.JoinRequestDecisionBody
import com.example.cpen321application.game.dto.JoinRequestDecisionResponse
import com.example.cpen321application.game.dto.JoinRequestResponse
import com.example.cpen321application.game.dto.MyGamesResponse
import com.example.cpen321application.game.dto.PendingJoinRequestsResponse
import com.example.cpen321application.user.dto.AuthTokenResponse
import com.example.cpen321application.user.dto.LoginRequest
import com.example.cpen321application.user.dto.ProfileResponse
import com.example.cpen321application.user.dto.PublicProfileResponse
import com.example.cpen321application.user.dto.RegisterNotifRequest
import com.example.cpen321application.user.dto.SignUpRequest
import com.example.cpen321application.user.dto.UpdateProfileRequest
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

private val retrofit = Retrofit.Builder()
    .addConverterFactory(GsonConverterFactory.create(serverGson))
    .baseUrl(BuildConfig.API_BASE_URL)
    .build()

// signing up and in, notifications, and the users' profiles
interface UserApi {
    @POST("api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest) : Response<AuthTokenResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest) : Response<AuthTokenResponse>

    @POST("api/register_notif")
    suspend fun registerNotif(
        @Header("Authorization") authorization: String,
        @Body request: RegisterNotifRequest
    ) : Response<AuthTokenResponse>

    @GET("api/users/me")
    suspend fun getMyProfile(@Header("Authorization") authorization: String): Response<ProfileResponse>

    @PUT("api/users/me")
    suspend fun updateMyProfile(
        @Header("Authorization") authorization: String,
        @Body request: UpdateProfileRequest
    ) : Response<ProfileResponse>

    // deletes the signed in user's account and everything tied to it, msg is "success" with no user
    @DELETE("api/users/me")
    suspend fun deleteMyAccount(@Header("Authorization") authorization: String): Response<ProfileResponse>

    @GET("api/users/{userId}")
    suspend fun getPublicProfile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Int
    ) : Response<PublicProfileResponse>
}

// games, their join requests and invites, and their chats
interface GameApi {
    // the signed in user is the new game's organizer
    @POST("api/games")
    suspend fun createGame(
        @Header("Authorization") authorization: String,
        @Body request: CreateGameRequest
    ) : Response<CreateGameResponse>

    // games the signed in user organized and joined, soonest first
    @GET("api/games/mine")
    suspend fun getMyGames(@Header("Authorization") authorization: String) : Response<MyGamesResponse>

    @PUT("api/games/{gameId}")
    suspend fun updateGame(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int,
        @Body request: CreateGameRequest
    ) : Response<CreateGameResponse>

    @POST("api/games/{gameId}/cancel")
    suspend fun cancelGame(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int
    ) : Response<CreateGameResponse>

    @POST("api/games/{gameId}/complete")
    suspend fun completeGame(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int
    ) : Response<CreateGameResponse>

    @POST("api/games/{gameId}/leave")
    suspend fun leaveGame(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int
    ) : Response<CreateGameResponse>

    @DELETE("api/games/{gameId}/players/{userId}")
    suspend fun removePlayer(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int,
        @Path("userId") userId: Int
    ) : Response<CreateGameResponse>

    @GET("api/games/{gameId}")
    suspend fun getGameDetails(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int
    ) : Response<GameDetailsResponse>

    // upcoming games near the point that the signed in user can ask to join, closest first
    @GET("api/games/discover")
    suspend fun getDiscoverGames(
        @Header("Authorization") authorization: String,
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double
    ) : Response<DiscoverGamesResponse>

    @POST("api/games/{gameId}/join-requests")
    suspend fun sendJoinRequest(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int,
        @Body request: JoinRequestBody
    ) : Response<JoinRequestResponse>

    // the organizer's game with the join requests waiting for them, newest first
    @GET("api/games/{gameId}/join-requests")
    suspend fun getPendingJoinRequests(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int
    ) : Response<PendingJoinRequestsResponse>

    @PATCH("api/games/{gameId}/join-requests/{requestId}")
    suspend fun decideJoinRequest(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int,
        @Path("requestId") requestId: Int,
        @Body request: JoinRequestDecisionBody
    ) : Response<JoinRequestDecisionResponse>

    // the organizer invites friends to their game, the server notifies each of them
    @POST("api/games/{gameId}/invites")
    suspend fun inviteFriends(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int,
        @Body request: InviteFriendsBody
    ) : Response<InviteFriendsResponse>

    // a page of the chat's messages, newest first. A null before gives the newest page
    @GET("api/chats/{chatId}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: Int,
        @Query("before") before: Int?
    ) : Response<ChatMessagesResponse>
}

// finding people, friend requests and the user's friends
interface FriendApi {
    // users whose name matches or is similar to q, best match first
    @GET("api/users/search")
    suspend fun searchUsers(
        @Header("Authorization") authorization: String,
        @Query("q") query: String
    ) : Response<UserSearchResponse>

    @GET("api/friends")
    suspend fun getFriends(@Header("Authorization") authorization: String) : Response<FriendsResponse>

    // requests sent to the signed in user that are waiting for an answer, newest first
    @GET("api/friends/requests")
    suspend fun getFriendRequests(@Header("Authorization") authorization: String) : Response<FriendRequestsResponse>

    @POST("api/friends/requests")
    suspend fun sendFriendRequest(
        @Header("Authorization") authorization: String,
        @Body request: SendFriendRequestBody
    ) : Response<FriendChangeResponse>

    @PATCH("api/friends/requests/{requestId}")
    suspend fun decideFriendRequest(
        @Header("Authorization") authorization: String,
        @Path("requestId") requestId: Int,
        @Body request: FriendDecisionBody
    ) : Response<FriendChangeResponse>
}

// one Retrofit service per part of the API, all on the same server
object ServerApi {
    val userService: UserApi by lazy { retrofit.create(UserApi::class.java) }
    val gameService: GameApi by lazy { retrofit.create(GameApi::class.java) }
    val friendService: FriendApi by lazy { retrofit.create(FriendApi::class.java) }
}
