package com.example.cpen321application.game.repository

import android.content.Context
import com.example.cpen321application.core.NETWORK_MESSAGE
import com.example.cpen321application.core.SESSION_ENDED_MESSAGE
import com.example.cpen321application.core.network.ServerApi
import com.example.cpen321application.core.network.readReply
import com.example.cpen321application.core.session.Session
import com.example.cpen321application.game.dto.CreateGameRequest
import com.example.cpen321application.game.dto.CreateGameResponse
import com.example.cpen321application.game.dto.DiscoverGameDto
import com.example.cpen321application.game.dto.GameDetailsResponse
import com.example.cpen321application.game.dto.GameDto
import com.example.cpen321application.game.dto.JoinRequestBody
import com.example.cpen321application.game.dto.JoinRequestDecisionBody
import com.example.cpen321application.game.dto.JoinRequestDecisionResponse
import com.example.cpen321application.game.dto.JoinRequestResponse
import com.example.cpen321application.game.dto.PendingJoinRequestDto
import com.example.cpen321application.game.model.GameInfoForm
import com.example.cpen321application.game.model.toGameInfoForm
import com.google.gson.JsonParseException
import retrofit2.Response
import java.io.IOException

const val COULD_NOT_LOAD_GAME ="Couldn't load this game, try again"

sealed interface GameChangeResult {
    // the game as the server stored it
    data class Done(val game: GameDto) : GameChangeResult
    data class Failed(val message: String) : GameChangeResult
}

private fun GameInfoForm.toRequest() = CreateGameRequest(
    title = title,
    sport = sport,
    description = description,
    startsAt = startsAt.toString(),
    locationName = location.name,
    address = location.address,
    latitude = location.latitude,
    longitude = location.longitude,
    maxPlayers = maxPlayers,
    skillLevel = skillLevel
)

/*  the server's error is shown for problems the user can act on (invalid field,
    not allowed, game full, ...), server errors get failureMessage instead
 */
private fun userError(msg: String?, error: String?): String? =
    error.takeIf { msg != null && msg != "server_error" }

// sends a create, edit, cancel, complete or leave request and reads the reply
private suspend fun changeGame(
    context: Context,
    failureMessage: String,
    request: suspend (authorization: String) -> Response<CreateGameResponse>
): GameChangeResult {
    return try {
        val response = Session.call(context, request)
            ?: return GameChangeResult.Failed(SESSION_ENDED_MESSAGE)
        val reply = readReply(response, CreateGameResponse::class.java)
        val game = reply?.game
        when {
            reply?.msg == "success" && game != null -> GameChangeResult.Done(game)
            else -> GameChangeResult.Failed(userError(reply?.msg, reply?.error) ?: failureMessage)
        }
    } catch (e: IOException) {
        GameChangeResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        GameChangeResult.Failed(failureMessage)
    }
}

suspend fun createGame(context: Context, form: GameInfoForm): GameChangeResult =
    changeGame(context, "Could not create game, try again") { authorization ->
        ServerApi.retrofitService.createGame(authorization = authorization, request = form.toRequest())
    }

suspend fun updateGame(context: Context, gameId: Int, form: GameInfoForm): GameChangeResult =
    changeGame(context, "Could not save your changes, try again") { authorization ->
        ServerApi.retrofitService.updateGame(authorization = authorization, gameId = gameId, request = form.toRequest())
    }

// the server notifies the game's players
suspend fun cancelGame(context: Context, gameId: Int): GameChangeResult =
    changeGame(context, "Could not cancel the game, try again") { authorization ->
        ServerApi.retrofitService.cancelGame(authorization = authorization, gameId = gameId)
    }

// the organizer marks their started game completed, the server notifies the game's players
suspend fun completeGame(context: Context, gameId: Int): GameChangeResult =
    changeGame(context, "Could not complete the game, try again") { authorization ->
        ServerApi.retrofitService.completeGame(authorization = authorization, gameId = gameId)
    }

/*  takes the user out of the game: a player at any time, the organizer once it's
    cancelled or completed. The server explains why it can't
 */
suspend fun leaveGame(context: Context, gameId: Int): GameChangeResult =
    changeGame(context, "Could not leave the game, try again") { authorization ->
        ServerApi.retrofitService.leaveGame(authorization = authorization, gameId = gameId)
    }

sealed interface GameDetailsResult {
    data class Loaded(val details: GameDetailsResponse, val game: GameDto) : GameDetailsResult
    // the game was deleted or the id is wrong, retrying won't help
    data object NotFound : GameDetailsResult
    data class Failed(val message: String) : GameDetailsResult
}

suspend fun fetchGameDetails(context: Context, gameId: Int): GameDetailsResult {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.getGameDetails(authorization = authorization, gameId = gameId)
        } ?: return GameDetailsResult.Failed(SESSION_ENDED_MESSAGE)
        if (response.code() == 404) return GameDetailsResult.NotFound

        val reply = response.body()
        val game = reply?.game
        if (reply?.msg == "success" && game != null) {
            GameDetailsResult.Loaded(details = reply, game = game)
        } else {
            GameDetailsResult.Failed(COULD_NOT_LOAD_GAME)
        }
    } catch (e: IOException) {
        GameDetailsResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        GameDetailsResult.Failed(COULD_NOT_LOAD_GAME)
    }
}

// the game to edit as the form's starting values, or why it can't be edited
sealed interface EditableGame {
    data class Loaded(val form: GameInfoForm, val playerCount: Int) : EditableGame
    data class Unavailable(val message: String) : EditableGame
}

// only the organizer can edit a game, and not once it's cancelled or completed
suspend fun loadEditableGame(context: Context, gameId: Int): EditableGame =
    when (val result = fetchGameDetails(context, gameId)) {
        is GameDetailsResult.Loaded -> when {
            result.details.viewerRole != "organizer" -> EditableGame.Unavailable("Only the organizer can edit this game.")
            result.game.status == "cancelled" -> EditableGame.Unavailable("This game was cancelled.")
            result.game.status == "completed" -> EditableGame.Unavailable("This game was completed.")
            // the form is null when the game's values can't fill it
            else -> runCatching { result.game.toGameInfoForm() }.getOrNull()
                ?.let { EditableGame.Loaded(form = it, playerCount = result.game.playerCount) }
                ?: EditableGame.Unavailable(COULD_NOT_LOAD_GAME)
        }
        GameDetailsResult.NotFound -> EditableGame.Unavailable("This game no longer exists.")
        is GameDetailsResult.Failed -> EditableGame.Unavailable(result.message)
    }

sealed interface DiscoverGamesResult {
    // closest first, empty when there are no games nearby
    data class Loaded(val games: List<DiscoverGameDto>) : DiscoverGamesResult
    data class Failed(val message: String) : DiscoverGamesResult
}

suspend fun fetchDiscoverGames(context: Context, latitude: Double, longitude: Double): DiscoverGamesResult {
    val failed = DiscoverGamesResult.Failed("Couldn't load games near you, try again")
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.getDiscoverGames(
                authorization = authorization,
                latitude = latitude,
                longitude = longitude
            )
        } ?: return DiscoverGamesResult.Failed(SESSION_ENDED_MESSAGE)
        // error replies (e.g. server_error) have no body here and fall to the else case
        val reply = response.body()
        when (reply?.msg) {
            "success" -> DiscoverGamesResult.Loaded(reply.games.orEmpty())
            "no_games" -> DiscoverGamesResult.Loaded(emptyList())
            else -> failed
        }
    } catch (e: IOException) {
        DiscoverGamesResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        failed
    }
}

sealed interface JoinRequestResult {
    data object Sent : JoinRequestResult
    data class Failed(val message: String) : JoinRequestResult
}

// blank messages are left out
suspend fun sendJoinRequest(
    context: Context,
    gameId: Int,
    skillLevel: String,
    playFrequency: String?,
    message: String?
): JoinRequestResult {
    val failureMessage = "Could not send your request, try again"
    val request = JoinRequestBody(
        skillLevel = skillLevel,
        playFrequency = playFrequency,
        message = message?.trim()?.takeIf { it.isNotEmpty() }
    )
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.sendJoinRequest(authorization = authorization, gameId = gameId, request = request)
        } ?: return JoinRequestResult.Failed(SESSION_ENDED_MESSAGE)
        val reply = readReply(response, JoinRequestResponse::class.java)
        if (reply?.msg == "success") {
            JoinRequestResult.Sent
        } else {
            JoinRequestResult.Failed(userError(reply?.msg, reply?.error) ?: failureMessage)
        }
    } catch (e: IOException) {
        JoinRequestResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        JoinRequestResult.Failed(failureMessage)
    }
}

sealed interface PendingJoinRequestsResult {
    data class Loaded(val game: GameDto, val requests: List<PendingJoinRequestDto>) : PendingJoinRequestsResult
    // retrying won't help: the game is gone or the user isn't its organizer
    data class Unavailable(val message: String) : PendingJoinRequestsResult
    data class Failed(val message: String) : PendingJoinRequestsResult
}

suspend fun fetchPendingJoinRequests(context: Context, gameId: Int): PendingJoinRequestsResult {
    val failed = PendingJoinRequestsResult.Failed("Couldn't load join requests, try again")
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.getPendingJoinRequests(authorization = authorization, gameId = gameId)
        } ?: return PendingJoinRequestsResult.Failed(SESSION_ENDED_MESSAGE)
        when (response.code()) {
            404 -> return PendingJoinRequestsResult.Unavailable("This game no longer exists.")
            403 -> return PendingJoinRequestsResult.Unavailable("Only the organizer can see join requests.")
        }

        val reply = response.body()
        val game = reply?.game
        if (reply?.msg == "success" && game != null) {
            PendingJoinRequestsResult.Loaded(game = game, requests = reply.requests.orEmpty())
        } else {
            failed
        }
    } catch (e: IOException) {
        PendingJoinRequestsResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        failed
    }
}

sealed interface JoinRequestDecisionResult {
    // the game after the decision, with its new player count
    data class Done(val game: GameDto) : JoinRequestDecisionResult
    data class Failed(val message: String) : JoinRequestDecisionResult
}

suspend fun decideJoinRequest(
    context: Context,
    gameId: Int,
    requestId: Int,
    approve: Boolean
): JoinRequestDecisionResult {
    val failureMessage = "Could not save your answer, try again"
    val request = JoinRequestDecisionBody(decision = if (approve) "approve" else "decline")
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.decideJoinRequest(
                authorization = authorization,
                gameId = gameId,
                requestId = requestId,
                request = request
            )
        } ?: return JoinRequestDecisionResult.Failed(SESSION_ENDED_MESSAGE)
        val reply = readReply(response, JoinRequestDecisionResponse::class.java)
        val game = reply?.game
        if (reply?.msg == "success" && game != null) {
            JoinRequestDecisionResult.Done(game)
        } else {
            JoinRequestDecisionResult.Failed(userError(reply?.msg, reply?.error) ?: failureMessage)
        }
    } catch (e: IOException) {
        JoinRequestDecisionResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        JoinRequestDecisionResult.Failed(failureMessage)
    }
}

sealed interface MyGamesResult {
    data class Loaded(val organized: List<GameDto>, val joined: List<GameDto>) : MyGamesResult
    data class Failed(val message: String) : MyGamesResult
}

suspend fun fetchMyGames(context: Context): MyGamesResult {
    val failed = MyGamesResult.Failed("Couldn't update your games, try again")
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.getMyGames(authorization = authorization)
        } ?: return MyGamesResult.Failed(SESSION_ENDED_MESSAGE)
        // error replies (e.g. server_error) have no body here and fall to the else case
        val reply = response.body()
        when (reply?.msg) {
            "success" -> MyGamesResult.Loaded(
                organized = reply.organizedGames.orEmpty(),
                joined = reply.joinedGames.orEmpty()
            )
            "no_games" -> MyGamesResult.Loaded(organized = emptyList(), joined = emptyList())
            else -> failed
        }
    } catch (e: IOException) {
        MyGamesResult.Failed("Couldn't reach the server, showing your last loaded games")
    } catch (e: JsonParseException) {
        failed
    }
}
