package com.example.cpen321application.review.repository

import android.content.Context
import com.example.cpen321application.core.NETWORK_MESSAGE
import com.example.cpen321application.core.SESSION_ENDED_MESSAGE
import com.example.cpen321application.core.network.ServerApi
import com.example.cpen321application.core.network.readReply
import com.example.cpen321application.core.session.Session
import com.example.cpen321application.review.dto.RatingBody
import com.example.cpen321application.review.dto.RatingResponse
import com.example.cpen321application.review.model.PlayersToRate
import com.example.cpen321application.review.model.RatingCategory
import com.example.cpen321application.review.model.toPlayersToRate
import com.google.gson.JsonParseException
import java.io.IOException

private const val COULD_NOT_LOAD_PLAYERS = "Couldn't load the players, try again"
private const val COULD_NOT_SUBMIT_RATING = "Couldn't submit your rating, try again"
private const val COULD_NOT_LOAD_SCORE = "Couldn't load your rating, try again"

sealed interface PlayersToRateResult {
    data class Loaded(val players: PlayersToRate) : PlayersToRateResult
    // retrying won't help: the game is gone or the user can't rate in it
    data class Unavailable(val message: String) : PlayersToRateResult
    data class Failed(val message: String) : PlayersToRateResult
}

suspend fun fetchPlayersToRate(context: Context, gameId: Int): PlayersToRateResult {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.getPlayersToRate(authorization = authorization, gameId = gameId)
        } ?: return PlayersToRateResult.Failed(SESSION_ENDED_MESSAGE)
        if (response.code() == 403 || response.code() == 404) {
            return PlayersToRateResult.Unavailable("You can't rate players in this game.")
        }

        val reply = response.body()
        if (reply?.msg == "success" && reply.players != null) {
            PlayersToRateResult.Loaded(reply.toPlayersToRate())
        } else {
            PlayersToRateResult.Failed(COULD_NOT_LOAD_PLAYERS)
        }
    } catch (e: IOException) {
        PlayersToRateResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        PlayersToRateResult.Failed(COULD_NOT_LOAD_PLAYERS)
    }
}

sealed interface ReputationScoreResult {
    data class Loaded(val score: Double) : ReputationScoreResult
    data class Failed(val message: String) : ReputationScoreResult
}

suspend fun fetchMyReputationScore(context: Context): ReputationScoreResult {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.getMyStats(authorization = authorization)
        } ?: return ReputationScoreResult.Failed(SESSION_ENDED_MESSAGE)
        val score = response.body()?.reputationScore
        if (score != null) ReputationScoreResult.Loaded(score) else ReputationScoreResult.Failed(COULD_NOT_LOAD_SCORE)
    } catch (e: IOException) {
        ReputationScoreResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        ReputationScoreResult.Failed(COULD_NOT_LOAD_SCORE)
    }
}

sealed interface SubmitRatingResult {
    data object Submitted : SubmitRatingResult
    data class Failed(val message: String) : SubmitRatingResult
}

// scores has a value for every category, the form checks that before sending
suspend fun submitRating(
    context: Context,
    gameId: Int,
    ratedUserId: Int,
    scores: Map<RatingCategory, Int>
): SubmitRatingResult {
    val body = RatingBody(
        ratedUserId = ratedUserId,
        punctuality = scores.getValue(RatingCategory.Punctuality),
        sportsmanship = scores.getValue(RatingCategory.Sportsmanship),
        skill = scores.getValue(RatingCategory.Skill),
        reliability = scores.getValue(RatingCategory.Reliability),
        overall = scores.getValue(RatingCategory.Overall)
    )
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.submitRating(authorization = authorization, gameId = gameId, request = body)
        } ?: return SubmitRatingResult.Failed(SESSION_ENDED_MESSAGE)
        val reply = readReply(response, RatingResponse::class.java)
        when {
            reply?.msg == "success" -> SubmitRatingResult.Submitted
            reply?.msg != "server_error" && reply?.error != null -> SubmitRatingResult.Failed(reply.error)
            else -> SubmitRatingResult.Failed(COULD_NOT_SUBMIT_RATING)
        }
    } catch (e: IOException) {
        SubmitRatingResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        SubmitRatingResult.Failed(COULD_NOT_SUBMIT_RATING)
    }
}
