package com.example.cpen321application.e2e

import androidx.test.platform.app.InstrumentationRegistry
import com.example.cpen321application.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

// how long a call to the server may take
private const val CALL_TIMEOUT_MILLIS = 10_000

// how far in the past a game is moved to start, the server allows up to a minute for clock differences
private const val STARTED_SECONDS_AGO = 30L

/*  the test organizer saved by backend/tests/e2e/seedGameDiscovery.ts, which owns the test
    games. The E2E tests act as them through the server's interface, e.g. to cancel a game
    while User A is asking to join it, or to read the game chat. scripts/run-frontend-e2e-tests passes their token in
    as the organizerToken instrumentation argument.
 */
object TestOrganizer {
    private val token: String
        get() = InstrumentationRegistry.getArguments().getString("organizerToken")
            ?: error("No organizerToken argument, run the tests with scripts/run-frontend-e2e-tests")

    // cancels the organizer's game called title
    fun cancelGame(title: String) {
        val game = findGame(title)
        call("POST", "api/games/${game.getInt("game_id")}/cancel", body = null)
    }

    // edits the organizer's game called title so it started 30 seconds ago
    fun startGame(title: String) {
        val game = findGame(title)
        val body = JSONObject()
        listOf("title", "sport", "description", "location_name", "address", "latitude", "longitude",
            "max_players", "skill_level").forEach { field -> body.put(field, game.get(field)) }
        body.put("starts_at", Instant.now().minusSeconds(STARTED_SECONDS_AGO).toString())
        call("PUT", "api/games/${game.getInt("game_id")}", body)
    }

    // the texts of the newest messages in the chat of the organizer's game called title, newest first
    fun chatMessageTexts(title: String): List<String> {
        val messages = call("GET", "api/chats/${chatId(title)}/messages", body = null).getJSONArray("messages")
        return (0 until messages.length()).map { messages.getJSONObject(it).getString("text") }
    }

    // the chat of the organizer's game called title
    private fun chatId(title: String): Int {
        val gameId = findGame(title).getInt("game_id")
        return call("GET", "api/games/$gameId", body = null).getInt("chat_id")
    }

    // the organizer's upcoming game called title, as the server sends it
    private fun findGame(title: String): JSONObject {
        val games = call("GET", "api/games/mine", body = null).getJSONArray("organized_games")
        return (0 until games.length()).map { games.getJSONObject(it) }
            .firstOrNull { it.getString("title") == title }
            ?: error("The test organizer has no game called $title, run the seed script again")
    }

    // calls the server as the organizer and returns its reply, failing the test if it isn't a success
    private fun call(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CALL_TIMEOUT_MILLIS
            connection.readTimeout = CALL_TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val reply = stream.bufferedReader().use { it.readText() }
            check(status in 200..299) { "$method /$path failed with $status: $reply" }
            return JSONObject(reply)
        } finally {
            connection.disconnect()
        }
    }
}
