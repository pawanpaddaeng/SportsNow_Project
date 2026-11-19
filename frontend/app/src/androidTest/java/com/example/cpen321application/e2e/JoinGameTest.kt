package com.example.cpen321application.e2e

import android.net.ConnectivityManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.cpen321application.MainActivity
import com.example.cpen321application.testutil.GAMES_SPOT
import com.example.cpen321application.testutil.TestLocation
import com.example.cpen321application.testutil.TestOrganizer
import com.example.cpen321application.testutil.openTab
import com.example.cpen321application.testutil.scrollTo
import com.example.cpen321application.testutil.waitForNode
import com.example.cpen321application.testutil.waitForText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// the games backend/tests/e2e/seedGameDiscovery.ts saves around GAMES_SPOT, one per test
private const val JOIN_GAME = "E2E Join"
private const val CANCELLED_GAME = "E2E Join Cancelled"
private const val STARTED_GAME = "E2E Join Started"
private const val OFFLINE_GAME = "E2E Join Offline"

// how long the phone may take to reconnect after airplane mode is turned off
private const val RECONNECT_TIMEOUT_MILLIS = 30_000L

/*  E2E tests for Use Case 5: Join Game (Requirements and Design, section 3.5).

    Before the tests:
      - User A is signed in and the app shows its main screen.
      - scripts/run-frontend-e2e-tests ran backend/tests/e2e/seedGameDiscovery.ts, which saved
        a game by a test organizer around GAMES_SPOT for each test, none of which User A has
        asked to join. It passed the organizer's token in, which TestOrganizer uses to cancel
        or start a game while User A has the join request form open (4a and 4b).
      - Each test sets the phone's location with TestLocation and opens its game from Discover,
        so the Player is viewing the details of a game they have not joined (the precondition).
 */
@RunWith(AndroidJUnit4::class)
class JoinGameTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val location = TestLocation(device)

    @Before
    fun setUp() {
        location.start()
    }

    @After
    fun tearDown() {
        setAirplaneMode(false)
        location.stop()
    }

    // main success scenario (steps 1 to 4) and failure scenario 3a
    @Test
    fun joinGame_sendsRequestAndShowsItPending() {
        openGameDetails(JOIN_GAME)

        // 1. Player selects "Request to join"
        composeRule.onNode(requestToJoinButton).performScrollTo().performClick()

        // 2. System displays the join request form with fields for skill level, how often they
        //    play (optional), and a message (optional)
        composeRule.waitForText("Request to join")
        composeRule.onNodeWithText("What is your skill level in basketball? *").assertIsDisplayed()
        composeRule.onNodeWithText("How often do you play?").assertIsDisplayed()
        composeRule.onNodeWithText("Message to organizer (optional)").performScrollTo().assertIsDisplayed()

        // 3a. Player did not select a skill level
        // 3a1. System marks the skill level as required and keeps "Send request" disabled until
        //      the Player selects it
        composeRule.onNode(sendButton).performScrollTo().assertIsNotEnabled()

        // 3. Player fills in the form and clicks "Send request"
        composeRule.onNode(chip("Intermediate")).performScrollTo().performClick()
        composeRule.onNode(chip("Monthly")).performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).performScrollTo().performTextInput("E2E happy to play any position")
        composeRule.onNode(sendButton).performScrollTo().assertIsEnabled().performClick()

        // 4. System sends the request to the organiser and shows the request as pending
        waitForPendingCard(JOIN_GAME)
        composeRule.onNode(gameCard(JOIN_GAME)).performClick()
        composeRule.waitForNode(hasText("Request sent") and hasClickAction())
        composeRule.onNode(hasText("Request sent") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Waiting for the organizer to respond. You will be notified when they do.")
            .performScrollTo().assertIsDisplayed()
    }

    // failure scenario 4a: the organiser cancelled the game before the request was sent
    @Test
    fun joinGame_gameCancelled_showsErrorAndSendsNothing() {
        openRequestForm(CANCELLED_GAME)

        // 4a. The organiser cancelled the game before the request was sent
        TestOrganizer.cancelGame(CANCELLED_GAME)
        composeRule.onNode(sendButton).performScrollTo().performClick()

        // 4a1. System displays "This game was cancelled" and does not send the request
        composeRule.waitForText("This game was cancelled")
        assertFormStillOpen()
    }

    // failure scenario 4b: the game started before the request was sent
    @Test
    fun joinGame_gameStarted_showsErrorAndSendsNothing() {
        openRequestForm(STARTED_GAME)

        // 4b. The game started before the request was sent
        TestOrganizer.startGame(STARTED_GAME)
        composeRule.onNode(sendButton).performScrollTo().performClick()

        // 4b1. System displays "This game has already started" and does not send the request
        composeRule.waitForText("This game has already started")
        assertFormStillOpen()
    }

    // failure scenario 4c: the phone cannot reach the server
    @Test
    fun joinGame_noConnection_showsErrorAndTryAgain() {
        openRequestForm(OFFLINE_GAME)

        // 4c. The phone cannot reach the server (no internet connection)
        setAirplaneMode(true)
        composeRule.onNode(sendButton).performScrollTo().performClick()

        // 4c1. System displays "Couldn't reach the server, try again" and lets the Player try again
        composeRule.waitForText("Couldn't reach the server, try again")
        assertFormStillOpen()

        // the Player reconnects and sends the same answers again
        setAirplaneMode(false)
        composeRule.onNode(sendButton).performScrollTo().performClick()
        waitForPendingCard(OFFLINE_GAME)
    }

    private val requestToJoinButton = hasText("Request to Join") and hasClickAction()
    private val sendButton = hasText("Send Request") and hasClickAction()

    private fun chip(label: String) = hasText(label) and hasClickAction()

    /*  a game's card on Discover, e.g. "2. E2E Join Cancelled". Matched by the end of its title
        since its rank depends on which games the other tests have cancelled, and "E2E Join" is
        the start of the other titles
     */
    private fun gameCard(title: String) = SemanticsMatcher("Discover card for $title") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.endsWith(". $title") }
    } and hasClickAction()

    // opens the game from Discover near GAMES_SPOT, as a Player who hasn't asked to join it
    private fun openGameDetails(title: String) {
        location.moveTo(GAMES_SPOT)
        composeRule.openTab("Discover")
        composeRule.waitForText("Sorted by distance")
        composeRule.scrollTo(gameCard(title))
        composeRule.onNode(gameCard(title)).performClick()
        composeRule.waitForNode(requestToJoinButton)
        composeRule.onNode(requestToJoinButton).performScrollTo().assertIsEnabled()
    }

    /*  after a request is sent the app goes back to Discover, which reloads and marks the game
        "Request pending". The card is there from before the reload, so scroll to it, then wait
     */
    private fun waitForPendingCard(title: String) {
        composeRule.waitForNode(gameCard(title))
        composeRule.scrollTo(gameCard(title))
        val pendingCard = gameCard(title) and hasText("Request pending")
        composeRule.waitForNode(pendingCard)
        composeRule.onNode(pendingCard).assertIsDisplayed()
    }

    // opens the game's join request form and fills in the required skill level
    private fun openRequestForm(title: String) {
        openGameDetails(title)
        composeRule.onNode(requestToJoinButton).performScrollTo().performClick()
        composeRule.waitForNode(chip("Beginner"))
        composeRule.onNode(chip("Beginner")).performScrollTo().performClick()
    }

    // the form is still open with the answers kept, so nothing was sent and the Player can try again
    private fun assertFormStillOpen() {
        composeRule.onNode(sendButton).performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Request to join").assertIsDisplayed()
    }

    // airplane mode cuts the phone off from the server. Turning it off waits until it reconnects
    private fun setAirplaneMode(enabled: Boolean) {
        device.executeShellCommand("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}")
        if (!enabled) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            composeRule.waitUntil(RECONNECT_TIMEOUT_MILLIS) { connectivity.activeNetwork != null }
        }
    }
}
