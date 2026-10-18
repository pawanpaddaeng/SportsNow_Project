package com.example.cpen321application.network

import com.google.gson.annotations.SerializedName

/*  a game as the server sends it, the same shape for every endpoint that returns
    games. Times are UTC ISO 8601 strings, e.g. "2026-10-18T02:00:00Z"
 */
data class GameDto(
    @SerializedName("game_id") val gameId: Int,
    @SerializedName("organizer_id") val organizerId: Int,
    val title: String,
    val sport: String,
    val description: String?,
    @SerializedName("starts_at") val startsAt: String,
    @SerializedName("ends_at") val endsAt: String?,
    @SerializedName("location_name") val locationName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("max_players") val maxPlayers: Int,
    @SerializedName("player_count") val playerCount: Int,
    @SerializedName("skill_level") val skillLevel: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String
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

// reply from POST api/games: msg "success" with the created game, or an error
data class CreateGameResponse(
    val msg: String?,
    val game: GameDto?,
    val error: String?
)
