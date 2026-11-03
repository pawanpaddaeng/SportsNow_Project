package com.example.cpen321application.review.dto

import com.example.cpen321application.core.network.Required
import com.google.gson.annotations.SerializedName

// a player the user can rate as the server sends it, rated says the user already did
data class PlayerToRateDto(
    @SerializedName("user_id") val userId: Int,
    @field:Required @SerializedName("first_name") val firstName: String,
    @field:Required @SerializedName("last_name") val lastName: String,
    val rated: Boolean
)

/*  reply from GET api/games/{gameId}/ratings: msg "success" with the game's title and the
    other players who showed up, otherwise "not_found", "forbidden" or an error code
 */
data class PlayersToRateResponse(
    val msg: String?,
    @SerializedName("game_title") val gameTitle: String?,
    @SerializedName("organizer_id") val organizerId: Int?,
    val players: List<PlayerToRateDto>?,
    val error: String?
)

// body for POST api/games/{gameId}/ratings, each category is from 1 to 5
data class RatingBody(
    @SerializedName("rated_user_id") val ratedUserId: Int,
    val punctuality: Int,
    val sportsmanship: Int,
    val skill: Int,
    val reliability: Int,
    val overall: Int
)

// reply from GET api/users/me/stats: msg "success" with the user's score out of 5, or an error code
data class UserStatsResponse(
    val msg: String?,
    @SerializedName("reputation_score") val reputationScore: Double?,
    val error: String?
)

// reply from POST api/games/{gameId}/ratings: msg "success", or an error code with error
data class RatingResponse(
    val msg: String?,
    val error: String?
)
