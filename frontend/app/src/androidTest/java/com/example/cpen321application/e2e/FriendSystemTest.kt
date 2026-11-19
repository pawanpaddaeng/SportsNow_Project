package com.example.cpen321application.e2e

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.cpen321application.MainActivity
import com.example.cpen321application.ui.screens.friends.FRIEND_REQUEST_CARD_TAG
import com.example.cpen321application.ui.screens.friends.PERSON_CARD_TAG
import org.junit.After
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

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

// User B's first name, the title of a notification for their message
private const val USER_B_FIRST_NAME = "Bea"

// how long a sent message or a notification may take to arrive
private const val MESSAGE_TIMEOUT_MILLIS = 30_000L

// how long the chat may take to reconnect once the network is back on
private const val RECONNECT_TIMEOUT_MILLIS = 60_000L

// what a message that couldn't be sent shows under it
private const val NOT_SENT_TEXT = "Not sent, check your connection. Tap to try again."

private const val APP_PACKAGE = "com.example.cpen321application"

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

    The tests run in the order of their names (test1, test2, test3). Each one leaves the users
    as it found them: not friends, with no pending requests
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FriendSystemTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // runs shell commands on the emulator and opens the notification shade, outside the app
    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    // a test that fails while offline must not leave the emulator without a network
    @After
    fun turnNetworkBackOn() {
        setNetwork(on = true)
    }

    // Use case 4: Search Users, its main success scenario and failure scenario 3a
    @Test
    fun test1SearchUsers() {
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
    fun test2SendFriendRequest() {
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

    /*  Use case 6: Accept Friend Request, then the chat it opens: use case 9: Send Friend Message
        (formal UC1, with failure scenarios 5a and 6a), use case 11: Receive Friend Message and
        use case 10: View Friend Messages, in that order, since chatting needs the two users to
        be friends. User B is the manual tester, who follows the steps scripts/run-e2e-user-b
        shows. Only User A's own messages are checked by their text, User B can send anything.
        It ends with use case 12: Remove Friend, which also leaves the two users not friends
        for the next run
     */
    @Test
    fun test3AcceptFriendRequestAndChat() {
        // unique per run: if the two were friends before, their old chat and its messages are reused
        val runId = System.currentTimeMillis() / 1000
        val helloText = "E2E hello $runId"
        val offlineText = "E2E offline $runId"
        allowNotifications()

        acceptFriendRequest()
        sendFriendMessage(helloText, offlineText)
        receiveFriendMessage()
        viewFriendMessages(helloText, offlineText)
        removeFriend()
    }

    // Use case 6: Accept Friend Request, ends on the Friends screen with User B as a friend
    private fun acceptFriendRequest() {
        // 1. The other person sends the user a friend request
        askUserB("Click the Friends tab, search for User A and click Add")

        // 2. User opens their friend requests
        composeRule.onNode(hasText("Notifications") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Friend requests") and hasClickAction()).performClick()

        // 3. System lists the requests waiting for an answer, each with Accept and Decline
        composeRule.onNodeWithText("Friend Requests").assertIsDisplayed()
        waitForRequestCard(USER_B_NAME, MESSAGE_TIMEOUT_MILLIS)
        composeRule.onNode(requestButton(USER_B_NAME, "Decline")).assertIsDisplayed()

        // 4. User selects "Accept"
        composeRule.onNode(requestButton(USER_B_NAME, "Accept")).performClick()

        // 5. System removes the request, makes the two users friends, and notifies the other user
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(requestCard(USER_B_NAME)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNode(hasText("Friends") and hasClickAction()).performClick()
        clearSearchIfAny()
        waitForPersonCard(USER_B_NAME)
        askUserB("Check a 'Friend request accepted' notification arrived, and User A is in your friends")
    }

    // Use case 9: Send Friend Message (formal UC1, with 5a and 6a), ends in the chat with User B
    private fun sendFriendMessage(helloText: String, offlineText: String) {
        // 1. User selects "Friends"   2. System displays the list of the user's friends
        composeRule.onNodeWithText("Your Friends").assertIsDisplayed()
        composeRule.onNode(personCard(USER_B_NAME)).assertIsDisplayed()

        // 3. User selects a friend
        composeRule.onNode(personCard(USER_B_NAME)).performClick()

        // 4. System opens the chat with that friend
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(chatTitle()).fetchSemanticsNodes().isNotEmpty()
        }
        messageField().assert(hasText("Message"))
        sendButton().assertIsDisplayed()

        // 5a. User clicked "Send" without typing a message
        sendButton().assertIsNotEnabled()
        messageField().performTextInput("   ")
        sendButton().assertIsNotEnabled()

        // 5a1. System does not send anything and keeps the chat open
        composeRule.onNode(chatTitle()).assertIsDisplayed()

        // 5. User types a message and clicks "Send"
        messageField().performTextClearance()
        messageField().performTextInput(helloText)
        sendButton().assertIsEnabled()
        sendButton().performClick()

        // 6. System sends the message to the friend
        messageField().assert(hasText("Message"))
        waitForSentMessage(helloText)
        askUserB("Open the chat with User A and check User A's new message arrived")

        // 6a. The message could not be sent (e.g. no internet connection)
        setNetwork(on = false)
        composeRule.waitUntil(RECONNECT_TIMEOUT_MILLIS) { chatIsOffline() }
        messageField().performTextInput(offlineText)
        sendButton().performClick()

        // 6a1. System displays a message saying the message could not be sent and lets the user try again
        waitForText(NOT_SENT_TEXT)
        setNetwork(on = true)
        waitForNetwork()
        composeRule.waitUntil(RECONNECT_TIMEOUT_MILLIS) { !chatIsOffline() }
        composeRule.onNodeWithText(NOT_SENT_TEXT).performClick()
        waitForSentMessage(offlineText)
        composeRule.onAllNodes(hasText(NOT_SENT_TEXT)).assertCountEquals(0)
    }

    /*  Use case 11: Receive Friend Message, as a notification since the user doesn't have the
        chat open. User B sends one message, which viewFriendMessages then finds in the chat.
        GameChatTest covers a message arriving live in an open chat
     */
    private fun receiveFriendMessage() {
        // 1. The friend sends the user a message while the user doesn't have the chat open
        composeRule.onNodeWithContentDescription("Back").performClick()
        clearNotifications()
        askUserB("In the chat with User A, send any message")

        // 2. System notifies the user with a push notification
        device.openNotification()
        val notified = device.wait(Until.hasObject(By.text(USER_B_FIRST_NAME)), MESSAGE_TIMEOUT_MILLIS)
        device.pressBack()
        if (!notified) throw AssertionError("No notification titled \"$USER_B_FIRST_NAME\" arrived")
    }

    // Use case 10: View Friend Messages, starting on the Friends screen
    private fun viewFriendMessages(helloText: String, offlineText: String) {
        // 1. User opens the chat with a friend
        composeRule.onNode(personCard(USER_B_NAME)).performClick()

        // 2. System shows the earlier messages, oldest at the top and newest at the bottom,
        //    each with who sent it, User B's message from receiveFriendMessage newest
        waitForText(offlineText, MESSAGE_TIMEOUT_MILLIS)
        if (topOf(hasText(helloText)) >= topOf(hasText(offlineText))) {
            throw AssertionError("\"$helloText\" isn't above \"$offlineText\"")
        }
        composeRule.waitUntil(MESSAGE_TIMEOUT_MILLIS) { messagesFromUserBBelow(offlineText) >= 1 }
    }

    // Use case 12: Remove Friend, starting in the chat with User B
    private fun removeFriend() {
        // 1. User opens a friend's options on the Friends screen and selects "Remove friend"
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForPersonCard(USER_B_NAME)
        composeRule.onNodeWithContentDescription("More options for $USER_B_NAME").performClick()
        composeRule.onNodeWithText("Remove friend").performClick()

        // 2. System asks the user to confirm   3. User confirms
        composeRule.onNodeWithText("Remove $USER_B_NAME?").assertIsDisplayed()
        composeRule.onNode(hasText("Remove") and hasClickAction()).performClick()

        // 4. System ends the friendship and removes the friend from the list
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(personCard(USER_B_NAME)).fetchSemanticsNodes().isEmpty()
        }
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

    private fun waitForRequestCard(name: String, timeoutMillis: Long = SEARCH_TIMEOUT_MILLIS) {
        composeRule.waitUntil(timeoutMillis) {
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

    // a person card showing the name. A friend's card can be tapped, which merges its texts into it
    private fun personCard(name: String): SemanticsMatcher =
        hasTestTag(PERSON_CARD_TAG) and (hasText(name) or hasAnyDescendant(hasText(name)))

    // the chat's title, User B's name without the tap to open a profile that a message's sender name has
    private fun chatTitle(): SemanticsMatcher = hasText(USER_B_NAME) and !hasClickAction()

    // the sender name on User B's messages, which opens their profile when tapped
    private fun userBSenderName(): SemanticsMatcher = hasText(USER_B_NAME) and hasClickAction()

    // the chat's box to write in, the only text field on the chat screen
    private fun messageField() = composeRule.onNode(hasSetTextAction())

    private fun sendButton() = composeRule.onNodeWithContentDescription("Send message")

    // waits until the message is on the screen and nothing is still being sent
    private fun waitForSentMessage(text: String) {
        composeRule.waitUntil(MESSAGE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(hasText("Sending...")).fetchSemanticsNodes().isEmpty()
        }
    }

    // the chat shows it lost its connection: reconnecting, or the messages couldn't load
    private fun chatIsOffline(): Boolean =
        composeRule.onAllNodes(hasText("Connecting...")).fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodes(hasText("Couldn't reach the server, try again")).fetchSemanticsNodes().isNotEmpty()

    // how many of User B's messages are below the message, i.e. newer than it
    private fun messagesFromUserBBelow(text: String): Int {
        val top = topOf(hasText(text))
        return composeRule.onAllNodes(userBSenderName()).fetchSemanticsNodes().count { it.boundsInRoot.top > top }
    }

    // how far down the screen the first node matching the matcher starts
    private fun topOf(matcher: SemanticsMatcher): Float =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().first().boundsInRoot.top

    // Android 13+ only shows the app's notifications once it's allowed to
    private fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.executeShellCommand("pm grant $APP_PACKAGE android.permission.POST_NOTIFICATIONS")
        }
    }

    // clears the notification shade, so a notification from an earlier run can't be mistaken for a new one
    private fun clearNotifications() {
        device.openNotification()
        val clearAll = device.wait(Until.findObject(By.text("Clear all")), 3_000L)
        if (clearAll != null) clearAll.click() else device.pressBack()
        device.waitForIdle()
    }

    // turns the phone's Wi-Fi and mobile data on or off, outside the app
    private fun setNetwork(on: Boolean) {
        val state = if (on) "enable" else "disable"
        device.executeShellCommand("svc wifi $state")
        device.executeShellCommand("svc data $state")
    }

    // Wi-Fi takes a few seconds to reconnect once it's turned back on
    private fun waitForNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        composeRule.waitUntil(RECONNECT_TIMEOUT_MILLIS) {
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }
    }

    private fun waitForPersonCard(name: String) {
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(personCard(name)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = SEARCH_TIMEOUT_MILLIS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
