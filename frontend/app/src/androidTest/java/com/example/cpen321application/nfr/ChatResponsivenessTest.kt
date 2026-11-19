package com.example.cpen321application.nfr

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cpen321application.MainActivity
import com.example.cpen321application.game.model.GameInfoForm
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.game.repository.GameChangeResult
import com.example.cpen321application.game.repository.cancelGame
import com.example.cpen321application.game.repository.createGame
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/*  NFR 2 (Chat Responsiveness): when a user sends a message in a game chat or friend chat, the
    other members who have the chat open should receive it within 1 second. The app shows a sent
    message straight away as "Sending...", and replaces it with the server's copy when the chat
    WebSocket sends it back. The server sends that copy to every member with the chat open at the
    same time, so the time from tapping Send to "Sending..." going away is the time the other
    members wait, measured on the phone.

    Run it with scripts/run-frontend-nfr-tests, which starts the backend and the app and waits
    for you to sign in, since Google Sign-In can't be automated.
 */
@RunWith(AndroidJUnit4::class)
class ChatResponsivenessTest {

    // the app is opened in the test, after the game is created, so Home lists the game
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var gameId: Int? = null
    private var activity: ActivityScenario<MainActivity>? = null

    // creates a game organized by the signed in user, so they are in its chat
    @Before
    fun setUp() {
        // the app asks for notification permission when it opens, and the pop-up would cover it
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            .close()
        val form = GameInfoForm(
            sport = "basketball",
            title = GAME_TITLE,
            startsAt = Instant.now().plus(Duration.ofHours(1)),
            location = GameLocation("UBC Rec Centre", "6000 Student Union Blvd", 49.2606, -123.2460),
            maxPlayers = 10,
            skillLevel = "any",
            description = null
        )
        val result = runBlocking { createGame(context, form) }
        check(result is GameChangeResult.Done) { "Could not create the test game: $result" }
        gameId = result.game?.gameId
    }

    // cancels the test game so it doesn't stay on the user's Home screen
    @After
    fun tearDown() {
        activity?.close()
        gameId?.let { runBlocking { cancelGame(context, it) } }
    }

    // Input: a signed in user with their game's chat open sends MESSAGE_COUNT messages
    // Expected behavior: each message is sent back by the server in under 1 second
    // Expected output: each message is shown without "Sending..." within 1 second of tapping Send
    @Test
    fun eachMessageIsDeliveredWithin1Second() {
        // 1. open the app, then the game's chat from Home, not timed
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(timeoutMillis = APP_START_TIMEOUT_MS) {
            compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText(HOME_TAB)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            runCatching {
                compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(GAME_TITLE))
            }.isSuccess
        }
        compose.onNodeWithText(GAME_TITLE).performClick()
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            compose.onAllNodes(hasText(OPEN_CHAT)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(OPEN_CHAT).performClick()
        waitForChatConnection()

        // 2. send each message and time how long until the server's copy replaces it
        val timesMs = (1..MESSAGE_COUNT).map { index ->
            val text = "NFR message $index"
            compose.onNodeWithText(MESSAGE_BOX).performTextInput(text)
            val start = SystemClock.elapsedRealtime()
            compose.onNodeWithContentDescription(SEND_BUTTON).performClick()

            // 3. waitUntil fails the test if the message is still sending after 1 second
            compose.waitUntil(timeoutMillis = LIMIT_MS) {
                compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodes(hasText(SENDING)).fetchSemanticsNodes().isEmpty()
            }
            SystemClock.elapsedRealtime() - start
        }
        Log.i(TAG, "Chat with $MESSAGE_COUNT messages: average ${timesMs.average().toLong()} ms, " +
            "slowest ${timesMs.max()} ms")
    }

    // the chat shows "Connecting..." until its WebSocket is open, and a loading bar before that
    private fun waitForChatConnection() {
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            compose.onAllNodes(hasContentDescription(SEND_BUTTON)).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText(CONNECTING)).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                    .fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        const val TAG = "NFR2"
        const val GAME_TITLE = "NFR Chat Test"
        const val HOME_TAB = "Home"
        const val OPEN_CHAT = "Open Chat"
        const val MESSAGE_BOX = "Message"
        const val SEND_BUTTON = "Send message"
        const val SENDING = "Sending..."
        const val CONNECTING = "Connecting..."
        const val MESSAGE_COUNT = 20
        const val LIMIT_MS = 1_000L
        const val APP_START_TIMEOUT_MS = 15_000L
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
