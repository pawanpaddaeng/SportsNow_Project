package com.example.cpen321application.network

import com.google.gson.annotations.SerializedName

/*  a game as the server sends it, the same shape for every endpoint that returns
    games. Times are UTC ISO 8601 strings, e.g. "2026-10-18T02:00:00Z".
    @Required fields make a reply without them fail to parse (see ServerGson)
 */
data class GameDto(
    @SerializedName("game_id") val gameId: Int,
    @SerializedName("organizer_id") val organizerId: Int,
    @field:Required val title: String,
    @field:Required val sport: String,
    val description: String?,
    @field:Required @SerializedName("starts_at") val startsAt: String,
    @SerializedName("ends_at") val endsAt: String?,
    @field:Required @SerializedName("location_name") val locationName: String,
    @field:Required val address: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("max_players") val maxPlayers: Int,
    @SerializedName("player_count") val playerCount: Int,
    @field:Required @SerializedName("skill_level") val skillLevel: String,
    @field:Required val status: String,
    @field:Required @SerializedName("created_at") val createdAt: String
)

/*  body for POST api/games. starts_at is a UTC ISO 8601 time, e.g. "2026-10-18T02:00:00Z".
    A null description is left out of the json
 */
data class CreateGameRequest(
    val title: String,
    val sport: String,
    val description: String?,
    @SerializedName("starts_at") val startsAt: String,
    @SerializedName("location_name") val locationName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("max_players") val maxPlayers: Int,
    @SerializedName("skill_level") val skillLevel: String
)

/*  reply from GET api/games/mine: msg "success" with both lists (either can be
    empty), "no_games" when the user has none, otherwise an error code
 */
data class MyGamesResponse(
    val msg: String?,
    @SerializedName("organized_games") val organizedGames: List<GameDto>?,
    @SerializedName("joined_games") val joinedGames: List<GameDto>?,
    val error: String?
)

// a player in a game as the server sends it
data class GamePlayerDto(
    @SerializedName("user_id") val userId: Int,
    @field:Required @SerializedName("first_name") val firstName: String,
    @field:Required @SerializedName("last_name") val lastName: String
)

/*  reply from GET api/games/{gameId}: msg "success" with the game, its organizer and
    players (organizer included, in join order), otherwise "not_found" or an error code.
    viewerRole is "organizer", "player" or "viewer". pendingRequests is only set for
    the organizer, joinRequestStatus only for viewers (null if they never asked)
 */
data class GameDetailsResponse(
    val msg: String?,
    val game: GameDto?,
    val organizer: GamePlayerDto?,
    val players: List<GamePlayerDto>?,
    @SerializedName("viewer_role") val viewerRole: String?,
    @SerializedName("pending_requests") val pendingRequests: Int?,
    @SerializedName("join_request_status") val joinRequestStatus: String?,
    val error: String?
)

// reply from POST api/games: msg "success" with the created game, or an error
data class CreateGameResponse(
    val msg: String?,
    val game: GameDto?,
    val error: String?
)

/*  a game on the Discover screen: the game, how far it is from the user in metres
    and the user's join request for it ("pending", "declined", ... or null if they never asked)
 */
data class DiscoverGameDto(
    @field:Required val game: GameDto,
    @SerializedName("distance_m") val distanceMeters: Int,
    @SerializedName("join_request_status") val joinRequestStatus: String?
)

/*  reply from GET api/games/discover: msg "success" with the games closest first,
    "no_games" when there are none nearby, otherwise an error code
 */
data class DiscoverGamesResponse(
    val msg: String?,
    val games: List<DiscoverGameDto>?,
    val error: String?
)

/*  body for POST api/games/{gameId}/join-requests, the answers from the join request popup.
    skill_level is "beginner", "intermediate", "advanced" or "competitive", play_frequency
    "rarely", "monthly", "weekly" or "several_per_week". Null fields are left out of the json
 */
data class JoinRequestBody(
    @SerializedName("skill_level") val skillLevel: String,
    @SerializedName("play_frequency") val playFrequency: String?,
    val message: String?
)

// reply from POST api/games/{gameId}/join-requests: msg "success", or an error code with error
data class JoinRequestResponse(
    val msg: String?,
    val error: String?
)

/*  a pending join request as the organizer sees it: who asked, the popup's answers
    (playFrequency and message can be null) and when they asked (UTC ISO 8601)
 */
data class PendingJoinRequestDto(
    @SerializedName("request_id") val requestId: Int,
    @field:Required val user: GamePlayerDto,
    @field:Required @SerializedName("skill_level") val skillLevel: String,
    @SerializedName("play_frequency") val playFrequency: String?,
    val message: String?,
    @field:Required @SerializedName("created_at") val createdAt: String
)

/*  reply from GET api/games/{gameId}/join-requests: msg "success" with the game and its
    pending requests (newest first, can be empty), otherwise "not_found", "forbidden"
    (not the organizer) or an error code
 */
data class PendingJoinRequestsResponse(
    val msg: String?,
    val game: GameDto?,
    val requests: List<PendingJoinRequestDto>?,
    val error: String?
)

// body for PATCH api/games/{gameId}/join-requests/{requestId}, decision is "approve" or "decline"
data class JoinRequestDecisionBody(val decision: String)

/*  reply from PATCH api/games/{gameId}/join-requests/{requestId}: msg "success" with the
    game after the decision (an approval adds a player), otherwise an error code with error
 */
data class JoinRequestDecisionResponse(
    val msg: String?,
    val game: GameDto?,
    val error: String?
)
