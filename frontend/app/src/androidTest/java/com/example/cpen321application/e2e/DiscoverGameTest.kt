package com.example.cpen321application.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.cpen321application.MainActivity
import com.example.cpen321application.testutil.DISCOVER_SPOT
import com.example.cpen321application.testutil.NO_GAMES_SPOT
import com.example.cpen321application.testutil.TestLocation
import com.example.cpen321application.testutil.openTab
import com.example.cpen321application.testutil.scrollTo
import com.example.cpen321application.testutil.waitForText
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// the games backend/tests/e2e/seedGameDiscovery.ts saves around DISCOVER_SPOT, closest first
private const val NEAR_GAME = "E2E Basketball Near"
private const val FAR_GAME = "E2E Basketball Far"
private const val SOCCER_GAME = "E2E Soccer"

/*  E2E tests for Use Case 4: Discover Game (Requirements and Design, section 3.5).

    Before the tests:
      - User A is signed in and the app shows its main screen.
      - scripts/run-frontend-e2e-tests ran backend/tests/e2e/seedGameDiscovery.ts, which saved
        three games by a test organizer around DISCOVER_SPOT: two basketball games 200 m and
        1 km away and a soccer game 2 km away, with no volleyball games.
      - Each test sets the phone's location with TestLocation.
 */
@RunWith(AndroidJUnit4::class)
class DiscoverGameTest {

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
        location.stop()
    }

    // main success scenario (steps 1 to 5) and failure scenario 5a
    @Test
    fun discoverGame_showsNearbyGamesAndFiltersBySport() {
        // 1. Player selects the compass in the navigation bar
        location.moveTo(DISCOVER_SPOT)
        composeRule.openTab("Discover")
        composeRule.onNode(hasText("Discover") and !hasClickAction()).assertIsDisplayed()

        // 2. System gets the Player's location from the phone
        composeRule.waitForText("3 games nearby")
        composeRule.onNodeWithText("Showing games near").assertIsDisplayed()
        composeRule.onNodeWithText("Your location").assertIsDisplayed()
        assertNoLocationError()

        // 3. System displays the nearby games sorted by distance with their information,
        //    and a selector bar at the top with various sports
        composeRule.onNodeWithText("Sorted by distance").assertIsDisplayed()
        assertGameCard(1, NEAR_GAME, "200 m")
        assertGameCard(2, FAR_GAME, "1.0 km")
        assertGameCard(3, SOCCER_GAME, "2.0 km")
        composeRule.scrollTo(sportChip("All"))
        composeRule.onNode(sportChip("All")).assertIsSelected()
        listOf("Basketball", "Soccer", "Volleyball").forEach { sport ->
            composeRule.onNode(sportChip(sport)).assertIsDisplayed().assertIsNotSelected()
        }

        // 4. Player selects a sport in the selector bar
        composeRule.onNode(sportChip("Basketball")).performClick()

        // 5. System updates the list to show only the games of the selected sport
        composeRule.waitForText("2 basketball games nearby")
        composeRule.onNode(sportChip("Basketball")).assertIsSelected()
        assertGameCard(1, NEAR_GAME, "200 m")
        assertGameCard(2, FAR_GAME, "1.0 km")
        assertNoGame(SOCCER_GAME)

        // 5a. No nearby games are found for the selected sport
        composeRule.onNode(sportChip("Volleyball")).performClick()

        // 5a1. System displays a message saying no games were found of the selected sport
        composeRule.waitForText("No volleyball games nearby. Try another sport.")
        listOf(NEAR_GAME, FAR_GAME, SOCCER_GAME).forEach(::assertNoGame)
    }

    // failure scenario 2a: location is turned off
    @Test
    fun discoverGame_locationOff_showsErrorAndTryAgain() {
        // 2a. System cannot get the Player's location (location is turned off)
        location.setLocationEnabled(false)
        composeRule.openTab("Discover")

        // 2a1. System displays an error message and lets the Player try again or choose a
        //      place on the map
        composeRule.waitForText(LOCATION_OFF_MESSAGE)
        composeRule.onNode(hasText("Try again") and hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Choose a location").assertIsDisplayed()
        composeRule.onNodeWithText("Change").assertIsDisplayed()
        listOf(NEAR_GAME, FAR_GAME, SOCCER_GAME).forEach(::assertNoGame)

        // the Player turns location back on and tries again
        location.setLocationEnabled(true)
        location.moveTo(DISCOVER_SPOT)
        composeRule.onNode(hasText("Try again") and hasClickAction()).performClick()
        composeRule.waitForText("3 games nearby")
        assertNoLocationError()
        assertGameCard(1, NEAR_GAME, "200 m")
    }

    // failure scenario 3a: no games are found nearby
    @Test
    fun discoverGame_noGamesNearby_showsMessage() {
        // 3a. No games are found nearby
        location.moveTo(NO_GAMES_SPOT)
        composeRule.openTab("Discover")

        // 3a1. System displays a message saying no nearby games were found
        composeRule.waitForText("No games nearby right now. Check back later or organize your own game.")
        assertNoLocationError()
        listOf(NEAR_GAME, FAR_GAME, SOCCER_GAME).forEach(::assertNoGame)
    }

    // a sport chip in the selector bar, e.g. "Basketball"
    private fun sportChip(label: String) = hasText(label) and hasClickAction()

    /*  the game's card is in the list at rank (its place when sorted by distance) and shows
        its distance. The card is clickable, so its texts are merged into one node
     */
    private fun assertGameCard(rank: Int, title: String, distance: String) {
        val card = hasText("$rank. $title") and hasText(distance) and hasClickAction()
        composeRule.scrollTo(card)
        composeRule.onNode(card).assertIsDisplayed()
    }

    private fun assertNoGame(title: String) {
        val cards = composeRule.onAllNodes(hasText(title, substring = true)).fetchSemanticsNodes()
        assertTrue("$title should not be listed", cards.isEmpty())
    }

    private fun assertNoLocationError() {
        composeRule.onNode(hasText(LOCATION_OFF_MESSAGE)).assertDoesNotExist()
        composeRule.onNode(hasText(NO_FIX_MESSAGE)).assertDoesNotExist()
    }

    private companion object {
        const val LOCATION_OFF_MESSAGE = "Location is turned off. Turn it on in your phone's settings, then try again."
        const val NO_FIX_MESSAGE = "Couldn't find where you are yet. Wait a moment, then try again."
    }
}
