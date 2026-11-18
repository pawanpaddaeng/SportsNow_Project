package com.example.cpen321application.e2e

import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.cpen321application.MainActivity
import com.example.cpen321application.ui.screens.friends.FRIEND_REQUEST_CARD_TAG
import com.example.cpen321application.ui.screens.friends.PERSON_CARD_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// the name the manual tester gives User B when signing up, no other user may have it
private const val USER_B_NAME = "Bea Tester"

// a name no user has
private const val UNKNOWN_NAME = "Zqxv Wjkplm"

// how long to wait for the server's answer to a search
private const val SEARCH_TIMEOUT_MILLIS = 10_000L

// how long the test waits for the manual tester to press Enter after a User B step
private const val USER_B_TIMEOUT_MILLIS = 600_000L

// how long User B's change may take to reach the server once they pressed Enter
private const val USER_B_SYNC_TIMEOUT_MILLIS = 30_000L

// how often the test checks again while waiting
private const val RECHECK_MILLIS = 1_000L

/*  the tag the User B steps are logged with. scripts/run-e2e-user-b shows each one in a
    terminal and, when the tester presses Enter, writes USER_B_DONE to USER_B_DONE_FILE
 */
private const val LOG_TAG = "FriendSystemTest"
private const val USER_B_DONE_FILE = "/data/local/tmp/e2e-user-b-done"
private const val USER_B_DONE = "done"

/*  E2E tests for the Friend feature (Requirements and Design, use cases 4 to 12).
    User A is the account signed in on this emulator and is tested automatically.

    Before the tests:
      - User A is signed in and the app shows its main screen.
      - User B (manual tester): an account named "Bea Tester" exists, made with the app's
        sign up on another device. User A and User B are not friends and have no pending
        friend requests.
 */
@RunWith(AndroidJUnit4::class)
class FriendSystemTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // runs shell commands on the emulator, outside the app
    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    // Use case 4: Search Users, its main success scenario and failure scenario 3a
    @Test
    fun searchUsers() {
        // 1. User selects "Friends" in the navigation bar
        composeRule.onNode(hasText("Friends") and hasClickAction()).performClick()

        // 2. System displays the Friends screen with a search field and the user's friends
        composeRule.onNode(hasText("Friends") and !hasClickAction()).assertIsDisplayed()
        searchField().assert(hasText("Search by name"))
        composeRule.onNodeWithText("Your Friends").assertIsDisplayed()

        // 3. User types a name in the search field
        searchField().performTextInput(USER_B_NAME)

        // 4. System displays the matching users, best match first
        waitForPersonCard(USER_B_NAME)
        composeRule.onAllNodesWithTag(PERSON_CARD_TAG)[0].assert(hasAnyDescendant(hasText(USER_B_NAME)))

        // 3a. No user matches the name
        searchField().performTextClearance()
        searchField().performTextInput(UNKNOWN_NAME)

        // 3a1. System displays a message saying nobody was found
        waitForText("No one found with that name.")
        composeRule.onAllNodesWithTag(PERSON_CARD_TAG).assertCountEquals(0)

        // 5. User clears the search
        composeRule.onNodeWithContentDescription("Clear search").performClick()

        // 6. System shows the user's friends again
        composeRule.onNodeWithText("Your Friends").assertIsDisplayed()
        composeRule.onAllNodes(personCard(USER_B_NAME)).assertCountEquals(0)
    }

    // Use case 5: Send Friend Request, its main success scenario and failure scenario 3a, then
    // use case 7: Decline Friend Request with the request User B sent in 3a, which also leaves
    // the two users with no requests for the next run.
    // User B is the manual tester, who follows the steps scripts/run-e2e-user-b shows
    @Test
    fun sendFriendRequest() {
        // 1. User selects "Friends" in the navigation bar
        composeRule.onNode(hasText("Friends") and hasClickAction()).performClick()

        // 2. User searches for the person by name
        search(USER_B_NAME)

        // 3. System shows the person with a button to add them
        composeRule.onNode(addButton(USER_B_NAME)).assertIsDisplayed()

        // 4. User selects "Add"
        composeRule.onNode(addButton(USER_B_NAME)).performClick()

        // 5. System sends the request, shows it as sent, and notifies the other user
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) { cardShows(USER_B_NAME, "Request Sent") }
        composeRule.onAllNodes(addButton(USER_B_NAME)).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Clear search").performClick()
        search(USER_B_NAME)
        composeRule.onNode(personCard(USER_B_NAME) and hasAnyDescendant(hasText("Request Sent"))).assertIsDisplayed()
        askUserB(
            "Check a 'New friend request' notification arrived from User A, then open " +
                "Notifications > Friend requests and click Decline on User A's request"
        )
        searchUntilCardShows(USER_B_NAME, "Add")

        // 3a. The other person already sent the user a request
        askUserB("Click the Friends tab, search for User A and click Add")
        searchUntilCardShows(USER_B_NAME, "Sent you a request")

        // 3a1. System shows that the person already sent a request instead of the "Add" button,
        //      and lets the user answer it
        composeRule.onAllNodes(addButton(USER_B_NAME)).assertCountEquals(0)
        composeRule.onNode(hasText("Sent you a request") and hasAnyAncestor(personCard(USER_B_NAME))).performClick()
        composeRule.onNodeWithText("Friend Requests").assertIsDisplayed()
        waitForRequestCard(USER_B_NAME)
        composeRule.onNodeWithContentDescription("Back").performClick()

        // Use case 7: Decline Friend Request, with the request User B sent in 3a
        // 1. User opens their friend requests
        composeRule.onNode(hasText("Notifications") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Friend requests") and hasClickAction()).performClick()

        // 2. System lists the requests waiting for an answer, each with Accept and Decline
        composeRule.onNodeWithText("Friend Requests").assertIsDisplayed()
        waitForRequestCard(USER_B_NAME)
        composeRule.onNode(requestButton(USER_B_NAME, "Accept")).assertIsDisplayed()
        composeRule.onNode(requestButton(USER_B_NAME, "Decline")).assertIsDisplayed()

        // 3. User selects "Decline"
        composeRule.onNode(requestButton(USER_B_NAME, "Decline")).performClick()

        // 4. System removes the request, and the two users don't become friends
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(requestCard(USER_B_NAME)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNode(hasText("Friends") and hasClickAction()).performClick()
        clearSearchIfAny()
        composeRule.onNodeWithText("Your Friends").assertIsDisplayed()
        composeRule.onAllNodes(personCard(USER_B_NAME)).assertCountEquals(0)
        search(USER_B_NAME)
        composeRule.onNode(addButton(USER_B_NAME)).assertIsDisplayed()
    }

    // the Friends screen's search field, the only text field on it
    private fun searchField() = composeRule.onNode(hasSetTextAction())

    // clears the search, which empties the results, then searches and waits for the person's card
    private fun search(name: String) {
        searchField().performTextClearance()
        searchField().performTextInput(name)
        waitForPersonCard(name)
    }

    // whether the person's card shows the text, e.g. "Request Sent"
    private fun cardShows(name: String, text: String): Boolean =
        composeRule.onAllNodes(personCard(name) and hasAnyDescendant(hasText(text)))
            .fetchSemanticsNodes().isNotEmpty()

    // searches again until the person's card shows the text, since User B's change takes a moment to arrive
    private fun searchUntilCardShows(name: String, text: String) {
        val deadline = System.currentTimeMillis() + USER_B_SYNC_TIMEOUT_MILLIS
        search(name)
        while (!cardShows(name, text)) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("After User B's step, the $name card never showed \"$text\"")
            }
            Thread.sleep(RECHECK_MILLIS)
            search(name)
        }
    }

    // the Add button on the person's card
    private fun addButton(name: String): SemanticsMatcher =
        hasText("Add") and hasClickAction() and hasAnyAncestor(personCard(name))

    // the person's card on the Friend Requests screen
    private fun requestCard(name: String): SemanticsMatcher =
        hasTestTag(FRIEND_REQUEST_CARD_TAG) and hasAnyDescendant(hasText(name))

    // the Accept or Decline button on the person's request
    private fun requestButton(name: String, label: String): SemanticsMatcher =
        hasText(label) and hasClickAction() and hasAnyAncestor(requestCard(name))

    private fun waitForRequestCard(name: String) {
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(requestCard(name)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // the Friends tab keeps the last search, clearing it shows the user's friends
    private fun clearSearchIfAny() {
        val clear = composeRule.onAllNodes(hasContentDescription("Clear search"))
        if (clear.fetchSemanticsNodes().isNotEmpty()) clear[0].performClick()
    }

    /*  asks the manual tester to do a User B step and waits until they press Enter. The step
        is logged for scripts/run-e2e-user-b, which shows it and marks it done on Enter. The
        mark is read with shell commands, since apps can't read its folder
     */
    private fun askUserB(step: String) {
        device.executeShellCommand("rm -f $USER_B_DONE_FILE")
        Log.i(LOG_TAG, "USER B: $step")
        val deadline = System.currentTimeMillis() + USER_B_TIMEOUT_MILLIS
        while (device.executeShellCommand("cat $USER_B_DONE_FILE").trim() != USER_B_DONE) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("User B's step wasn't confirmed with Enter in time: $step")
            }
            Thread.sleep(RECHECK_MILLIS)
        }
        device.executeShellCommand("rm -f $USER_B_DONE_FILE")
    }

    // a person card showing the name
    private fun personCard(name: String): SemanticsMatcher =
        hasTestTag(PERSON_CARD_TAG) and hasAnyDescendant(hasText(name))

    private fun waitForPersonCard(name: String) {
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(personCard(name)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
