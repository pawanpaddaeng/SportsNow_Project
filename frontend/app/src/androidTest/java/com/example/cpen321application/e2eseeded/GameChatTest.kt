package com.example.cpen321application.e2eseeded

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.cpen321application.MainActivity
import com.example.cpen321application.testutil.LOAD_TIMEOUT_MILLIS
import com.example.cpen321application.testutil.TestOrganizer
import com.example.cpen321application.testutil.UserB
import com.example.cpen321application.testutil.openTab
import com.example.cpen321application.testutil.scrollTo
import com.example.cpen321application.testutil.waitForNode
import com.example.cpen321application.testutil.waitForText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// the game backend/tests/e2e/seedGameDiscovery.ts saves with User A as a player, and its chat
private const val CHAT_GAME = "E2E Chat"
private const val ORGANIZER_NAME = "Olive Organizer"
private const val OLDER_MESSAGE = "E2E hello from the organizer"
private const val NEWER_MESSAGE = "E2E bring a ball please"

// the manual tester's account (see Testing.md), who sends any message in Receive
private const val USER_B_NAME = "Bea Tester"

// how long User B's message may take to show once they pressed Enter
private const val USER_B_SYNC_TIMEOUT_MILLIS = 30_000L

/*  E2E tests for the game chat side of Send Message (use case 5, formal Use Case 1), View
    Messages (use case 6) and Receive Message (use case 7) in Requirements and Design, section
    3.4. FriendSystemTest covers their friend chat side. Each test opens the chat from
    Home > the game > Open Chat.

    Before the tests:
      - User A is signed in and the app shows its main screen.
      - backend/tests/e2e/seedGameDiscovery.ts was run (Testing.md section 4.2), which saved
        "E2E Chat", organized by a test organizer and joined by User A and User B, with two
        earlier messages from the organizer.
      - Receive Message only: User B (manual tester) is signed in as "Bea Tester" on another
        device that uses the same back end, and scripts/run-e2e-user-b runs in a second terminal.
 */
@RunWith(AndroidJUnit4::class)
class GameChatTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val userB = UserB(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))

    // View Messages: a player opens the game's chat and reads the earlier messages
    @Test
    fun viewGameChatMessages_showsEarlierMessagesOldestFirst() {
        // the player opens the chat of a game they joined
        openGameChat()

        // the system shows the earlier messages with who sent them, the newest at the bottom
        composeRule.onNodeWithText(OLDER_MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithText(NEWER_MESSAGE).assertIsDisplayed()
        assertTrue("$ORGANIZER_NAME's name should be shown", nodeCount(hasText(ORGANIZER_NAME)) >= 2)
        assertTrue("The newer message should be below the older one", top(NEWER_MESSAGE) > top(OLDER_MESSAGE))
    }

    // Send Message: a player sends a message to the game's other participants
    @Test
    fun sendGameChatMessage_sendsTheMessageToTheChat() {
        val text = "E2E sent by the player ${System.currentTimeMillis()}"
        openGameChat()

        // a blank message can't be sent
        composeRule.onNode(hasSetTextAction()).performTextInput("   ")
        composeRule.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextClearance()

        // the player types a message and sends it
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
        composeRule.onNodeWithContentDescription("Send message").assertIsEnabled().performClick()

        // the system shows it in the chat, empties the box, and saves it for the other participants
        composeRule.waitForText(text)
        composeRule.onNodeWithText(text).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        composeRule.waitUntil(LOAD_TIMEOUT_MILLIS) { text in TestOrganizer.chatMessageTexts(CHAT_GAME) }
    }

    // Receive Message: a player with the game's chat open sees a new message as it's sent
    @Test
    fun receiveGameChatMessage_showsNewMessageLive() {
        // the player has the game's chat open
        openGameChat()

        // another player (User B) sends a message in the game's chat
        userB.ask("On Home, open \"$CHAT_GAME\", click Open Chat and send any message")

        // the system shows it below the earlier messages, from User B, without the player doing anything.
        // Only User B's name is checked, since their message can say anything
        composeRule.waitUntil(USER_B_SYNC_TIMEOUT_MILLIS) { messagesFromUserBBelow(NEWER_MESSAGE) >= 1 }
        composeRule.onNode(userBSenderName()).assertIsDisplayed()
    }

    // the sender name on User B's messages, which opens their profile when tapped
    private fun userBSenderName(): SemanticsMatcher = hasText(USER_B_NAME) and hasClickAction()

    // how many of User B's messages are below the message, i.e. newer than it
    private fun messagesFromUserBBelow(text: String): Int {
        val top = top(text)
        return composeRule.onAllNodes(userBSenderName()).fetchSemanticsNodes().count { it.boundsInRoot.top > top }
    }

    /*  Home > "E2E Chat" > Open Chat, then waits until the earlier messages have loaded and the
        chat is connected, so new messages arrive live
     */
    private fun openGameChat() {
        val card = hasText(CHAT_GAME) and hasClickAction()
        composeRule.openTab("Home")
        composeRule.waitForNode(card)
        composeRule.scrollTo(card)
        composeRule.onNode(card).performClick()

        val openChat = hasText("Open Chat") and hasClickAction()
        composeRule.waitForNode(openChat)
        composeRule.onNode(openChat).performScrollTo().performClick()

        composeRule.waitForText(NEWER_MESSAGE)
        composeRule.waitUntil(LOAD_TIMEOUT_MILLIS) { nodeCount(hasText("Connecting...")) == 0 }
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    private fun nodeCount(matcher: SemanticsMatcher): Int =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().size

    // how far down the screen a message is
    private fun top(text: String): Float =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
}
