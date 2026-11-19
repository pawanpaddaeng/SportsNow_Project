package com.example.cpen321application.e2e

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cpen321application.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// the games backend/tests/e2e/seedGameDiscovery.ts saves for User A, one per test
private const val EDIT_GAME = "E2E Edit"
private const val CANCEL_GAME = "E2E Cancel"
private const val INVITE_GAME = "E2E Invite"
private const val ACCEPT_GAME = "E2E Accept"
private const val REJECT_GAME = "E2E Reject"
private const val REMOVE_GAME = "E2E Remove"

// the new title the Edit Game test gives EDIT_GAME
private const val EDITED_TITLE = "E2E Edited"

// the seeded users: User A's two friends, a player asking to join ACCEPT_GAME and one asking to
// join REJECT_GAME, and a player in REMOVE_GAME
private const val FIONA = "Fiona Friend"
private const val FINN = "Finn Friend"
private const val ALEX = "Alex Asker"
private const val BLAKE = "Blake Asker"
private const val RILEY = "Riley Player"

private const val NO_REQUESTS = "No join requests right now. You'll get a notification when someone asks to join."

/*  E2E tests for Edit Game (use case 18), Cancel Game (19), Invite Friends (20), Accept Join
    Request (21), Reject Join Request (22) and Remove Player (23) in Requirements and Design,
    section 3.4. CreateGameTest tests Create Game (use case 17). Each test opens its own game
    from Home, so none depends on another.

    Before the tests:
      - User A is signed in and the app shows its main screen.
      - scripts/run-frontend-e2e-tests ran backend/tests/e2e/seedGameDiscovery.ts, which saved
        the games above for User A, with only User A in them and room for 10 players, except:
        "E2E Accept" and "E2E Reject" have a pending join request from Alex Asker and Blake
        Asker, and Riley Player is in "E2E Remove". It also made Fiona Friend and Finn Friend
        User A's friends. The tests change these games, so they need a fresh seed for each run.
 */
@RunWith(AndroidJUnit4::class)
class GamePlanningTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // Edit Game: the organiser changes the details of a game they created
    @Test
    fun editGame_savesTheChanges() {
        // the organiser opens their game and selects "Edit"
        openGame(EDIT_GAME)
        composeRule.onNode(hasText("Edit") and hasClickAction()).performClick()

        // the system shows the form filled in with the game's details, with nothing to save yet
        composeRule.waitForNode(hasSetTextAction() and hasText(EDIT_GAME))
        composeRule.onNodeWithText("Edit Game").assertIsDisplayed()
        composeRule.onNode(saveButton).performScrollTo().assertIsNotEnabled()

        // the organiser changes the title, the max players and the skill level, and saves
        composeRule.onNode(hasSetTextAction() and hasText(EDIT_GAME)).performScrollTo().performTextReplacement(EDITED_TITLE)
        composeRule.onNode(hasContentDescription("More players")).performScrollTo().performClick()
        composeRule.onNode(hasText("Intermediate") and hasClickAction()).performScrollTo().performClick()
        composeRule.onNode(saveButton).performScrollTo().assertIsEnabled().performClick()

        // the system saves the changes and shows the game's details with them
        composeRule.waitForText("Players · 1/11")
        composeRule.onNode(hasText(EDITED_TITLE) and !hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Intermediate").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText(EDIT_GAME)).assertDoesNotExist()
    }

    // Cancel Game: the organiser cancels a game they created
    @Test
    fun cancelGame_marksTheGameCancelled() {
        // the organiser opens their game and selects "Cancel Game"
        openGame(CANCEL_GAME)
        composeRule.onNode(cancelButton and !hasAnyAncestor(isDialog())).performScrollTo().performClick()

        // the system asks them to confirm, saying the players will be told
        composeRule.waitForText("Cancel this game?")
        composeRule.onNode(hasText("$CANCEL_GAME will be cancelled for everyone", substring = true)).assertIsDisplayed()

        // the organiser confirms
        composeRule.onNode(cancelButton and hasAnyAncestor(isDialog())).performClick()

        // the system cancels the game, which can't be changed any more
        composeRule.waitForText("This game was cancelled.")
        composeRule.onNode(hasText("Edit") and hasClickAction()).assertDoesNotExist()
        composeRule.onNode(cancelButton).assertDoesNotExist()
        composeRule.onNode(hasText("Invite Friends") and hasClickAction()).assertDoesNotExist()
    }

    // Invite Friends: the organiser invites a friend to a game they created
    @Test
    fun inviteFriends_sendsTheInvite() {
        // the organiser opens their game and selects "Invite Friends"
        openGame(INVITE_GAME)
        composeRule.onNode(hasText("Invite Friends") and hasClickAction()).performScrollTo().performClick()

        // the system lists the organiser's friends who aren't in the game
        composeRule.waitForNode(friendRow(FIONA))
        composeRule.onNode(friendRow(FINN)).performScrollTo().assertIsDisplayed()
        composeRule.onNode(inviteButton("Invite")).assertIsNotEnabled()

        // the organiser ticks a friend
        composeRule.onNode(friendRow(FIONA)).performScrollTo().performClick()
        composeRule.onNode(friendRow(FIONA)).assertIsOn()

        // the organiser selects "Invite" and the system sends the invite
        expectToast("Invite sent") {
            composeRule.onNode(inviteButton("Invite (1)")).performClick()
        }
        composeRule.waitUntil(LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(friendRow(FIONA)).fetchSemanticsNodes().isEmpty()
        }
    }

    // Accept Join Request: the organiser accepts a player's request to join their game
    @Test
    fun acceptJoinRequest_addsThePlayer() {
        // the organiser opens the join request from their game
        openGame(ACCEPT_GAME)
        openJoinRequests()

        // the system shows the request with the player's rating, how often they play and their message
        composeRule.waitForText(ALEX)
        composeRule.onNodeWithText("Not rated yet · Intermediate · Plays weekly").assertIsDisplayed()
        composeRule.onNodeWithText("Happy to play any position!").assertIsDisplayed()
        composeRule.onNode(hasText("1/10 players", substring = true)).assertIsDisplayed()

        // the organiser approves it
        composeRule.onNode(hasText("Approve") and hasClickAction()).performClick()

        // the system adds the player to the game
        waitUntilGone(hasText(ALEX))
        composeRule.onNodeWithText(NO_REQUESTS).assertIsDisplayed()
        composeRule.onNode(hasText("2/10 players", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForText("Players · 2/10")
        openPlayers()
        composeRule.onNode(hasText(ALEX) and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }

    // Reject Join Request: the organiser rejects a player's request to join their game
    @Test
    fun rejectJoinRequest_declinesTheRequest() {
        // the organiser opens the join request from their game
        openGame(REJECT_GAME)
        openJoinRequests()

        // the system shows the request with the player's rating, how often they play and their message
        composeRule.waitForText(BLAKE)
        composeRule.onNodeWithText("Not rated yet · Beginner · Plays rarely").assertIsDisplayed()
        composeRule.onNodeWithText("First time playing, hope that is okay.").assertIsDisplayed()

        // the organiser declines it
        composeRule.onNode(hasText("Decline") and hasClickAction()).performClick()

        // the system rejects the request, and the player isn't added to the game
        waitUntilGone(hasText(BLAKE))
        composeRule.onNodeWithText(NO_REQUESTS).assertIsDisplayed()
        composeRule.onNode(hasText("1/10 players", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForText("Players · 1/10")
        waitUntilGone(hasText("1 pending join request"))
        openPlayers()
        composeRule.onNode(hasText(BLAKE)).assertDoesNotExist()
    }

    // Remove Player: the organiser removes a player from a game they created
    @Test
    fun removePlayer_removesThePlayer() {
        // the organiser opens their game's list of players
        openGame(REMOVE_GAME)
        composeRule.waitForText("Players · 2/10")
        openPlayers()
        composeRule.onNode(hasText(RILEY) and hasAnyAncestor(isDialog())).assertIsDisplayed()

        // the organiser selects "Remove" next to the player, and the system asks them to confirm
        composeRule.onNode(hasText("Remove") and hasClickAction()).performClick()
        composeRule.waitForText("Remove $RILEY?")

        // the organiser confirms
        composeRule.onNode(hasText("Remove") and hasClickAction() and inDialogWith("Remove $RILEY?")).performClick()

        // the system removes the player from the game
        composeRule.waitForText("Players · 1/10")
        waitUntilGone(hasText(RILEY))
        composeRule.onNode(hasText("Close") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Players · 1/10").performScrollTo().assertIsDisplayed()
    }

    private val saveButton = hasText("Save Changes") and hasClickAction()
    private val cancelButton = hasText("Cancel Game") and hasClickAction()

    // a game's card on Home. It's clickable, so its texts are merged into one node
    private fun homeCard(title: String) = hasText(title) and hasClickAction()

    // a friend's row in the invite list, clicking it ticks them
    private fun friendRow(name: String) = hasText(name) and isToggleable()

    private fun inviteButton(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(isDialog())

    // inside the dialog with this title
    private fun inDialogWith(title: String): SemanticsMatcher = hasAnyAncestor(isDialog() and hasAnyDescendant(hasText(title)))

    // Home > the game, as its organiser
    private fun openGame(title: String) {
        composeRule.openTab("Home")
        composeRule.waitAndScrollTo(homeCard(title))
        composeRule.onNode(homeCard(title)).performClick()
        composeRule.waitForText("Organized by you")
    }

    // from the game's details, opens its join requests
    private fun openJoinRequests() {
        composeRule.onNode(hasText("1 pending join request") and hasClickAction()).performScrollTo().performClick()
        composeRule.waitForText("Join Requests")
    }

    // from the game's details, opens the list of everyone in the game
    private fun openPlayers() {
        composeRule.onNode(hasText("See all") and hasClickAction()).performScrollTo().performClick()
        composeRule.waitForNode(hasText("Close") and hasClickAction())
    }

    private fun waitUntilGone(matcher: SemanticsMatcher) {
        composeRule.waitUntil(LOAD_TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
    }
}