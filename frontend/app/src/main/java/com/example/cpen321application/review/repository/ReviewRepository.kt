package com.example.cpen321application.review.repository

import android.content.Context
import com.example.cpen321application.core.NETWORK_MESSAGE
import com.example.cpen321application.core.SESSION_ENDED_MESSAGE
import com.example.cpen321application.core.network.ServerApi
import com.example.cpen321application.core.network.readReply
import com.example.cpen321application.core.session.Session
import com.example.cpen321application.review.dto.PlayersToRateResponse
import com.example.cpen321application.review.dto.RatingBody
import com.example.cpen321application.review.dto.RatingResponse
import com.example.cpen321application.review.model.Badge
import com.example.cpen321application.review.model.PlayersToRate
import com.example.cpen321application.review.model.RatingCategory
import com.example.cpen321application.review.model.badgesOf
import com.example.cpen321application.review.model.toPlayersToRate
import com.google.gson.JsonParseException
import java.io.IOException

private const val COULD_NOT_LOAD_PLAYERS = "Couldn't load the players, try again"
private const val COULD_NOT_SUBMIT_RATING = "Couldn't submit your rating, try again"
private const val COULD_NOT_LOAD_SCORE = "Couldn't load your rating, try again"
private const val COULD_NOT_LOAD_BADGES = "Couldn't load your badges, try again"
private const val CANNOT_RATE = "You can't rate players in this game."
// ratings close a day after the game is marked completed
private const val RATING_CLOSED = "Rating for this game has closed."

sealed interface PlayersToRateResult {
    data class Loaded(val players: PlayersToRate) : PlayersToRateResult
    // retrying won't help: the game is gone or the user can't rate in it
    data class Unavailable(val message: String) : PlayersToRateResult
    data class Failed(val message: String) : PlayersToRateResult
}

suspend fun fetchPlayersToRate(context: Context, gameId: Int): PlayersToRateResult {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.reviewService.getPlayersToRate(authorization = authorization, gameId = gameId)
        } ?: return PlayersToRateResult.Failed(SESSION_ENDED_MESSAGE)
        if (response.code() == 403 || response.code() == 404) {
            val closed = readReply(response, PlayersToRateResponse::class.java)?.msg == "rating_closed"
            return PlayersToRateResult.Unavailable(if (closed) RATING_CLOSED else CANNOT_RATE)
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
    // score is null if the user isn't rated yet
    data class Loaded(val score: Double?) : ReputationScoreResult
    data class Failed(val message: String) : ReputationScoreResult
}

suspend fun fetchMyReputationScore(context: Context): ReputationScoreResult {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.reviewService.getMyStats(authorization = authorization)
        } ?: return ReputationScoreResult.Failed(SESSION_ENDED_MESSAGE)
        val body = response.body()
        if (response.isSuccessful && body?.msg == "success") {
            ReputationScoreResult.Loaded(body.reputationScore)
        } else {
            ReputationScoreResult.Failed(COULD_NOT_LOAD_SCORE)
        }
    } catch (e: IOException) {
        ReputationScoreResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        ReputationScoreResult.Failed(COULD_NOT_LOAD_SCORE)
    }
}

sealed interface BadgesResult {
    // the badges the user earned, in profile order
    data class Loaded(val badges: List<Badge>) : BadgesResult
    data class Failed(val message: String) : BadgesResult
}

suspend fun fetchMyBadges(context: Context): BadgesResult {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.reviewService.getMyBadges(authorization = authorization)
        } ?: return BadgesResult.Failed(SESSION_ENDED_MESSAGE)
        val body = response.body()
        if (response.isSuccessful && body?.msg == "success") {
            BadgesResult.Loaded(badgesOf(body.badges))
        } else {
            BadgesResult.Failed(COULD_NOT_LOAD_BADGES)
        }
    } catch (e: IOException) {
        BadgesResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        BadgesResult.Failed(COULD_NOT_LOAD_BADGES)
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
            ServerApi.reviewService.submitRating(authorization = authorization, gameId = gameId, request = body)
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
