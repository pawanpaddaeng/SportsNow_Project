package com.example.cpen321application.games

import android.content.Context
import com.example.cpen321application.auth.Session
import com.example.cpen321application.network.CreateGameRequest
import com.example.cpen321application.network.CreateGameResponse
import com.example.cpen321application.network.DiscoverGameDto
import com.example.cpen321application.network.GameDetailsResponse
import com.example.cpen321application.network.GameDto
import com.example.cpen321application.network.JoinRequestBody
import com.example.cpen321application.network.JoinRequestResponse
import com.example.cpen321application.network.ServerApi
import com.example.cpen321application.ui.screens.games.GameInfoForm

// shown when Session.call ended the session (the app is going back to sign in)
private const val SESSION_ENDED_MESSAGE = "Your session ended, please sign in again"

// outcome of creating, editing or cancelling a game, message explains a failure to the user
sealed interface GameChangeResult {
    // the game as the server stored it
    data class Done(val game: GameDto) : GameChangeResult
    data class Failed(val message: String) : GameChangeResult
}

// the form as the json body of create and edit game
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

/*  sends a create, edit or cancel request and reads the reply. The server's description
    of the problem is shown for errors the user can act on (invalid field, not allowed,
    game cancelled or started, ...), server errors get failureMessage
 */
private suspend fun changeGame(
    context: Context,
    failureMessage: String,
    request: suspend (authorization: String) -> retrofit2.Response<CreateGameResponse>
): GameChangeResult {
    return try {
        val response = Session.call(context, request)
            ?: return GameChangeResult.Failed(SESSION_ENDED_MESSAGE)
        // error responses (4xx/5xx) carry the same json shape in the error body
        val reply = response.body() ?: try {
            com.google.gson.Gson().fromJson(response.errorBody()?.string(), CreateGameResponse::class.java)
        } catch (e: Exception) {
            null
        }

        val game = reply?.game
        when {
            reply?.msg == "success" && game != null -> GameChangeResult.Done(game)
            reply?.msg != null && reply.msg != "server_error" && reply.error != null ->
                GameChangeResult.Failed(reply.error)
            else -> GameChangeResult.Failed(failureMessage)
        }
    } catch (e: Exception) {
        GameChangeResult.Failed("Cannot reach the server, try again")
    }
}

/*  sends the Game Info form to the server as a new game.
    context is needed to renew an expired session (silent Google sign in)
 */
suspend fun createGame(context: Context, form: GameInfoForm): GameChangeResult =
    changeGame(context, "Could not create game, try again") { authorization ->
        ServerApi.retrofitService.createGame(authorization = authorization, request = form.toRequest())
    }

// saves the edited Game Info form over the organizer's game
suspend fun updateGame(context: Context, gameId: Int, form: GameInfoForm): GameChangeResult =
    changeGame(context, "Could not save your changes, try again") { authorization ->
        ServerApi.retrofitService.updateGame(authorization = authorization, gameId = gameId, request = form.toRequest())
    }

// cancels the organizer's game, its players are notified by the server
suspend fun cancelGame(context: Context, gameId: Int): GameChangeResult =
    changeGame(context, "Could not cancel the game, try again") { authorization ->
        ServerApi.retrofitService.cancelGame(authorization = authorization, gameId = gameId)
    }

// outcome of loading one game for the Game Details screen
sealed interface GameDetailsResult {
    data class Loaded(val details: GameDetailsResponse, val game: GameDto) : GameDetailsResult
    // the game was deleted or the id is wrong, retrying won't help
    data object NotFound : GameDetailsResult
    data class Failed(val message: String) : GameDetailsResult
}

/*  loads a game with its organizer, players and the signed in user's role in it.
    context is needed to renew an expired session (silent Google sign in)
 */
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
            GameDetailsResult.Failed("Couldn't load this game, try again")
        }
    } catch (e: Exception) {
        GameDetailsResult.Failed("Cannot reach the server, try again")
    }
}

// outcome of loading the Discover screen's games, message explains a failure to the user
sealed interface DiscoverGamesResult {
    // closest first, empty when there are no games nearby
    data class Loaded(val games: List<DiscoverGameDto>) : DiscoverGamesResult
    data class Failed(val message: String) : DiscoverGamesResult
}

/*  loads the upcoming games near the user's location that they can ask to join.
    context is needed to renew an expired session (silent Google sign in)
 */
suspend fun fetchDiscoverGames(context: Context, latitude: Double, longitude: Double): DiscoverGamesResult {
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
            else -> DiscoverGamesResult.Failed("Couldn't load games near you, try again")
        }
    } catch (e: Exception) {
        DiscoverGamesResult.Failed("Cannot reach the server, try again")
    }
}

// outcome of sending a join request, message explains a failure to the user
sealed interface JoinRequestResult {
    data object Sent : JoinRequestResult
    data class Failed(val message: String) : JoinRequestResult
}

/*  sends the join request popup's answers to the game's organizer. The server's
    description is shown for problems the user can act on (game full, already asked, ...),
    server errors get a general message. Blank messages are left out
 */
suspend fun sendJoinRequest(
    context: Context,
    gameId: Int,
    skillLevel: String,
    playFrequency: String?,
    message: String?
): JoinRequestResult {
    val request = JoinRequestBody(
        skillLevel = skillLevel,
        playFrequency = playFrequency,
        message = message?.trim()?.takeIf { it.isNotEmpty() }
    )
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.sendJoinRequest(authorization = authorization, gameId = gameId, request = request)
        } ?: return JoinRequestResult.Failed(SESSION_ENDED_MESSAGE)
        // error responses (4xx/5xx) carry the same json shape in the error body
        val reply = response.body() ?: try {
            com.google.gson.Gson().fromJson(response.errorBody()?.string(), JoinRequestResponse::class.java)
        } catch (e: Exception) {
            null
        }

        when {
            reply?.msg == "success" -> JoinRequestResult.Sent
            reply?.msg != null && reply.msg != "server_error" && reply.error != null ->
                JoinRequestResult.Failed(reply.error)
            else -> JoinRequestResult.Failed("Could not send your request, try again")
        }
    } catch (e: Exception) {
        JoinRequestResult.Failed("Cannot reach the server, try again")
    }
}

// outcome of loading the user's games, message explains a failure to the user
sealed interface MyGamesResult {
    data class Loaded(val organized: List<GameDto>, val joined: List<GameDto>) : MyGamesResult
    data class Failed(val message: String) : MyGamesResult
}

/*  loads the signed in user's organized and joined games, "no_games" gives two
    empty lists. context is needed to renew an expired session (silent Google sign in)
 */
suspend fun fetchMyGames(context: Context): MyGamesResult {
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
            else -> MyGamesResult.Failed("Couldn't update your games, try again")
        }
    } catch (e: Exception) {
        MyGamesResult.Failed("Couldn't reach the server, showing your last loaded games")
    }
}
