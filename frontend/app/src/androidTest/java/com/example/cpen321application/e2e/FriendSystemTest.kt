package com.example.cpen321application.e2e

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
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
import com.example.cpen321application.ui.screens.friends.PERSON_CARD_TAG
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// the name the manual tester gives User B when signing up, no other user may have it
private const val USER_B_NAME = "Bea Tester"

// a name no user has
private const val UNKNOWN_NAME = "Zqxv Wjkplm"

// how long to wait for the server's answer to a search
private const val SEARCH_TIMEOUT_MILLIS = 10_000L

// how long the phone may take to be back online after the network is turned on
private const val NETWORK_TIMEOUT_MILLIS = 30_000L

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

    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    // a test that fails while offline must not leave the emulator without a network
    @After
    fun turnNetworkBackOn() {
        setNetwork(on = true)
    }

    // Use case 4: Search Users, its main success scenario and failure scenarios 3a and 3b
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

        // 3b. The search could not be done (e.g. no internet connection)
        setNetwork(on = false)
        searchField().performTextClearance()
        searchField().performTextInput(USER_B_NAME)

        // 3b1. System displays an error message and lets the user try again
        waitForText("Couldn't reach the server, try again")
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        setNetwork(on = true)
        waitForNetwork()
        composeRule.onNodeWithText("Retry").performClick()
        waitForPersonCard(USER_B_NAME)

        // 5. User clears the search
        composeRule.onNodeWithContentDescription("Clear search").performClick()

        // 6. System shows the user's friends again
        composeRule.onNodeWithText("Your Friends").assertIsDisplayed()
        composeRule.onAllNodes(personCard(USER_B_NAME)).assertCountEquals(0)
    }

    // the Friends screen's search field, the only text field on it
    private fun searchField() = composeRule.onNode(hasSetTextAction())

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

    // turns the phone's Wi-Fi and mobile data on or off, outside the app
    private fun setNetwork(on: Boolean) {
        val state = if (on) "enable" else "disable"
        device.executeShellCommand("svc wifi $state")
        device.executeShellCommand("svc data $state")
    }

    // Wi-Fi takes a few seconds to reconnect once it's turned back on, so the retry has to wait
    private fun waitForNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        composeRule.waitUntil(NETWORK_TIMEOUT_MILLIS) {
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }
    }
}
