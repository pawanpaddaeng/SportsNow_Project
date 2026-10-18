package com.example.cpen321application.games

import android.content.Context
import com.example.cpen321application.auth.Session
import com.example.cpen321application.network.CreateGameRequest
import com.example.cpen321application.network.CreateGameResponse
import com.example.cpen321application.network.GameDto
import com.example.cpen321application.network.ServerApi
import com.example.cpen321application.ui.screens.games.NewGameForm

// shown when Session.call ended the session (the app is going back to sign in)
private const val SESSION_ENDED_MESSAGE = "Your session ended, please sign in again"

// outcome of creating a game, message explains a failure to the user
sealed interface CreateGameResult {
    data class Created(val game: GameDto) : CreateGameResult
    data class Failed(val message: String) : CreateGameResult
}

/*  sends the Create Game form to the server. On invalid_request the server's
    description of the problem is shown, other failures get a general message.
    context is needed to renew an expired session (silent Google sign in)
 */
suspend fun createGame(context: Context, form: NewGameForm): CreateGameResult {
    val request = CreateGameRequest(
        title = form.title,
        sport = form.sport,
        description = form.description,
        startsAt = form.startsAt.toString(),
        locationName = form.location.name,
        address = form.location.address,
        latitude = form.location.latitude,
        longitude = form.location.longitude,
        maxPlayers = form.maxPlayers,
        skillLevel = form.skillLevel
    )

    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.retrofitService.createGame(authorization = authorization, request = request)
        } ?: return CreateGameResult.Failed(SESSION_ENDED_MESSAGE)
        // error responses (4xx/5xx) carry the same json shape in the error body
        val reply = response.body() ?: try {
            com.google.gson.Gson().fromJson(response.errorBody()?.string(), CreateGameResponse::class.java)
        } catch (e: Exception) {
            null
        }

        val game = reply?.game
        when {
            reply?.msg == "success" && game != null -> CreateGameResult.Created(game)
            reply?.msg == "invalid_request" && reply.error != null -> CreateGameResult.Failed(reply.error)
            else -> CreateGameResult.Failed("Could not create game, try again")
        }
    } catch (e: Exception) {
        CreateGameResult.Failed("Cannot reach the server, try again")
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
