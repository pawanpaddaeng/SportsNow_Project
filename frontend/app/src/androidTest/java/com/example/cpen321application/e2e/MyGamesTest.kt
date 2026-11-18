package com.example.cpen321application.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cpen321application.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// the games backend/tests/e2e/seedGameDiscovery.ts saves for User A
private const val ORGANIZED_GAME = "E2E Organized"
private const val CHAT_GAME = "E2E Chat"
private const val LEAVE_GAME = "E2E Leave"

/*  E2E tests for View Upcoming Games (use case 27) and Leave Game (use case 26) in
    Requirements and Design, section 3.4. Both start on the Home screen.

    Before the tests:
      - User A is signed in and the app shows its main screen.
      - scripts/run-frontend-e2e-tests ran backend/tests/e2e/seedGameDiscovery.ts, which saved
        "E2E Organized", organized by User A, and "E2E Chat" and "E2E Leave", organized by a test
        organizer and already joined by User A. Only the Leave Game test changes them. User B is
        also in "E2E Chat" when their account exists, so its player count isn't checked.
 */
@RunWith(AndroidJUnit4::class)
class MyGamesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // View Upcoming Games: the user sees the games they organise and the games they joined
    @Test
    fun viewUpcomingGames_showsOrganizedAndJoinedGames() {
        // the user opens the Home screen
        composeRule.openTab("Home")

        // the system lists the games the user joined
        composeRule.waitForNode(homeCard(CHAT_GAME))
        composeRule.onNodeWithText("Joined Games").assertIsDisplayed()
        assertHomeCard(CHAT_GAME)

        // and the games the user organises
        assertHomeCard(ORGANIZED_GAME, players = "1/10")

        // each game is in its own section: collapsing Joined Games hides only the joined games
        composeRule.onNodeWithContentDescription("Collapse Joined Games").performClick()
        composeRule.onNode(homeCard(CHAT_GAME)).assertDoesNotExist()
        assertHomeCard(ORGANIZED_GAME, players = "1/10")
        composeRule.onNodeWithContentDescription("Expand Joined Games").performClick()

        // and collapsing Organized Games hides only the organised games
        composeRule.scrollTo(hasText("Organized Games"))
        composeRule.onNodeWithContentDescription("Collapse Organized Games").performClick()
        composeRule.onNode(homeCard(ORGANIZED_GAME)).assertDoesNotExist()
        assertHomeCard(CHAT_GAME)
        composeRule.onNodeWithContentDescription("Expand Organized Games").performClick()

        // the user selects a game and the system opens its details
        composeRule.scrollTo(homeCard(ORGANIZED_GAME))
        composeRule.onNode(homeCard(ORGANIZED_GAME)).performClick()
        composeRule.waitForText("Organized by you")
        composeRule.onNodeWithText(ORGANIZED_GAME).assertIsDisplayed()
    }

    // Leave Game: a player who joined a game withdraws from it before it starts
    @Test
    fun leaveGame_removesTheGameFromJoinedGames() {
        // the player opens the game they joined from the Home screen
        composeRule.openTab("Home")
        composeRule.waitForNode(homeCard(LEAVE_GAME))
        composeRule.scrollTo(homeCard(LEAVE_GAME))
        composeRule.onNode(homeCard(LEAVE_GAME)).performClick()

        // the player selects "Leave Game" and the system asks them to confirm
        clickLeaveGame()
        composeRule.onNodeWithText("Leave this game?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "You'll give up your spot in $LEAVE_GAME and the organizer will be notified. " +
                "To play again you'll need to send a new join request."
        ).assertIsDisplayed()

        // choosing "Stay" keeps the player in the game
        composeRule.onNode(hasText("Stay") and hasAnyAncestor(isDialog())).performClick()
        composeRule.onNode(hasText("Leave this game?")).assertDoesNotExist()
        composeRule.onNode(leaveButton).performScrollTo().assertIsDisplayed()

        // the player confirms leaving
        clickLeaveGame()
        composeRule.onNode(leaveButton and hasAnyAncestor(isDialog())).performClick()

        // the system removes them from the game and goes back to Home, where it's no longer listed
        composeRule.waitForText("Joined Games")
        composeRule.waitUntil(LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(homeCard(LEAVE_GAME)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForNode(homeCard(CHAT_GAME))
    }

    private val leaveButton = hasText("Leave Game") and hasClickAction()

    // a game's card on Home. It's clickable, so its texts are merged into one node
    private fun homeCard(title: String) = hasText(title) and hasClickAction()

    // the game's card is on Home, showing how many players it has when players is given
    private fun assertHomeCard(title: String, players: String? = null) {
        val card = if (players == null) homeCard(title) else homeCard(title) and hasText(players)
        composeRule.scrollTo(card)
        composeRule.onNode(card).assertIsDisplayed()
    }

    // the "Leave Game" button at the bottom of Game Details, scrolled into view
    private fun clickLeaveGame() {
        val button = leaveButton and !hasAnyAncestor(isDialog())
        composeRule.waitForNode(button)
        composeRule.onNode(button).performScrollTo().performClick()
    }
}
