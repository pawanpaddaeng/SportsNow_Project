package com.example.cpen321application.e2e

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
import com.example.cpen321application.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// the game backend/tests/e2e/seedGameDiscovery.ts saves with User A as a player, and its chat
private const val CHAT_GAME = "E2E Chat"
private const val ORGANIZER_NAME = "Olive Organizer"
private const val OLDER_MESSAGE = "E2E hello from the organizer"
private const val NEWER_MESSAGE = "E2E bring a ball please"

/*  E2E tests for the game chat: Send Game Chat Message (use case 23), View Game Chat
    Messages (use case 24) and Receive Game Chat Message (use case 25) in Requirements and
    Design, section 3.4. Each test opens the chat from Home > the game > Open Chat.

    Before the tests:
      - User A is signed in and the app shows its main screen.
      - scripts/run-frontend-e2e-tests ran backend/tests/e2e/seedGameDiscovery.ts, which saved
        "E2E Chat", organized by a test organizer and joined by User A, with two earlier
        messages from the organizer. Receive Game Chat Message has the organizer send a live
        message as the other participant, through TestOrganizer.
 */
@RunWith(AndroidJUnit4::class)
class GameChatTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // View Game Chat Messages: a player opens the game's chat and reads the earlier messages
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

    // Send Game Chat Message: a player sends a message to the game's other participants
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

    // Receive Game Chat Message: a participant with the chat open sees a new message as it's sent
    @Test
    fun receiveGameChatMessage_showsNewMessageLive() {
        val text = "E2E sent by the organizer ${System.currentTimeMillis()}"
        // the player has the game's chat open
        openGameChat()

        // another participant (the organizer) sends a message
        TestOrganizer.sendChatMessage(CHAT_GAME, text)

        // the system shows it at the bottom of the chat without the player doing anything
        composeRule.waitForText(text)
        composeRule.onNodeWithText(text).assertIsDisplayed()
        assertTrue("The new message should be below the earlier ones", top(text) > top(NEWER_MESSAGE))
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
