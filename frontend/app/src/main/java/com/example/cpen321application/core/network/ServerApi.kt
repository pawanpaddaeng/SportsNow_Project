package com.example.cpen321application.core.network

import com.example.cpen321application.BuildConfig
import com.example.cpen321application.chat.dto.ChatMessagesResponse
import com.example.cpen321application.game.dto.CreateGameRequest
import com.example.cpen321application.game.dto.CreateGameResponse
import com.example.cpen321application.game.dto.DiscoverGamesResponse
import com.example.cpen321application.game.dto.GameDetailsResponse
import com.example.cpen321application.game.dto.JoinRequestBody
import com.example.cpen321application.game.dto.JoinRequestDecisionBody
import com.example.cpen321application.game.dto.JoinRequestDecisionResponse
import com.example.cpen321application.game.dto.JoinRequestResponse
import com.example.cpen321application.game.dto.MyGamesResponse
import com.example.cpen321application.game.dto.PendingJoinRequestsResponse
import com.example.cpen321application.user.dto.AuthTokenResponse
import com.example.cpen321application.user.dto.LoginRequest
import com.example.cpen321application.user.dto.ProfileResponse
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

interface NetworkConnection {
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

    // a page of the chat's messages, newest first. A null before gives the newest page
    @GET("api/chats/{chatId}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: Int,
        @Query("before") before: Int?
    ) : Response<ChatMessagesResponse>
}

object ServerApi {
    val retrofitService : NetworkConnection by lazy {
        retrofit.create(NetworkConnection::class.java)
    }
}
