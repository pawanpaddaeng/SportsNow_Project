package com.example.cpen321application.network

import com.example.cpen321application.BuildConfig
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/*  retrofit for server connection and auto parsing to objects
    using gson
 */
private val retrofit = Retrofit.Builder()
    .addConverterFactory(GsonConverterFactory.create())
    .baseUrl(BuildConfig.API_BASE_URL)
    .build()

interface NetworkConnection {
    @POST("api/auth/google")
    suspend fun authAndInfo(@Body request: AuthRequest) : Response<AuthResponse>

    @POST("api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest) : Response<AuthTokenResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest) : Response<AuthTokenResponse>

    // responds msg "success", or an auth error (expired_token, invalid_token, no_user, ...)
    @POST("api/register_notif")
    suspend fun registerNotif(
        @Header("Authorization") authorization: String,
        @Body request: RegisterNotifRequest
    ) : Response<AuthTokenResponse>

    // creates the game in the body, the signed in user is its organizer
    @POST("api/games")
    suspend fun createGame(
        @Header("Authorization") authorization: String,
        @Body request: CreateGameRequest
    ) : Response<CreateGameResponse>

    // games the signed in user organized and joined, soonest first
    @GET("api/games/mine")
    suspend fun getMyGames(@Header("Authorization") authorization: String) : Response<MyGamesResponse>

    // replaces the organizer's game details, same body and reply as createGame
    @PUT("api/games/{gameId}")
    suspend fun updateGame(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int,
        @Body request: CreateGameRequest
    ) : Response<CreateGameResponse>

    // cancels the organizer's game, replies with the cancelled game like createGame
    @POST("api/games/{gameId}/cancel")
    suspend fun cancelGame(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int
    ) : Response<CreateGameResponse>

    // one game with its organizer, players and the signed in user's role in it
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

    // asks the game's organizer to let the signed in user join
    @POST("api/games/{gameId}/join-requests")
    suspend fun sendJoinRequest(
        @Header("Authorization") authorization: String,
        @Path("gameId") gameId: Int,
        @Body request: JoinRequestBody
    ) : Response<JoinRequestResponse>
}

// main object instance with retrofit service used to connect to server
object ServerApi {
    val retrofitService : NetworkConnection by lazy {
        retrofit.create(NetworkConnection::class.java)
    }
}
