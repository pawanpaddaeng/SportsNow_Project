package com.example.cpen321application.e2e

import android.Manifest
import android.net.ConnectivityManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.cpen321application.MainActivity
import com.example.cpen321application.testutil.expectToast
import com.example.cpen321application.testutil.openTab
import com.example.cpen321application.testutil.scrollTo
import com.example.cpen321application.testutil.waitAndScrollTo
import com.example.cpen321application.testutil.waitForNode
import com.example.cpen321application.testutil.waitForText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// the game backend/tests/e2e/seedGameDiscovery.ts saves for User A, shown once Home has loaded
private const val ORGANIZED_GAME = "E2E Organized"

// how long the phone may take to look up the address of the place under the pin
private const val ADDRESS_TIMEOUT_MILLIS = 30_000L

// how long the phone may take to reconnect after airplane mode is turned off
private const val RECONNECT_TIMEOUT_MILLIS = 30_000L

// the games these tests make start in 3 days, at 6:00 PM, the time picker's default
private const val DAYS_AHEAD = 3L

// where the map starts (UBC), as the app shows it when it finds no address there
private const val MAP_START_COORDINATES = "49.26060, -123.24600"

// the date picker's text input, digits only, with the emulator's language English (United States)
private val DATE_INPUT_FORMAT = DateTimeFormatter.ofPattern("MMddyyyy")

/*  E2E tests for Use Case 3: Create Game (Requirements and Design, section 3.5), use case 17
    in section 3.4. 4a and 8b can't be tested end to end, see Testing.md section 4.2.

    Before the tests:
      - User A is signed in and the app shows its main screen.
      - scripts/run-frontend-e2e-tests ran backend/tests/e2e/seedGameDiscovery.ts, which saved
        "E2E Organized" for User A. The tests only use it to know Home has loaded. Each game
        they make gets a new title starting with "E2E ", which the next seed removes.
      - The emulator is a Google Play or Google APIs image, which can look up addresses, with
        its language set to English (United States).
      - Each test gives the app location permission, which the map asks for when it opens.
 */
@RunWith(AndroidJUnit4::class)
class CreateGameTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    @After
    fun tearDown() {
        setAirplaneMode(false)
    }

    // main success scenario (steps 1 to 8)
    @Test
    fun createGame_createsTheGameAndShowsItOnHome() {
        val title = newGameTitle("E2E New Game")

        // 1. Game Organiser selects "Create game"
        openCreateGame()

        // 2. System displays the game creation form with fields for the sport, title, date, time,
        //    max players, skill level, description (optional), and a map for selecting the location
        listOf("Sport *", "Title *", "Date *", "Time *", "Location *", "Max players", "Skill level", "Description (optional)")
            .forEach { label -> composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithText("Choose a location").performScrollTo().assertIsDisplayed()

        // 3. Game Organiser fills in all the details and clicks on the map
        fillInDetails(title, LocalDate.now().plusDays(DAYS_AHEAD))
        composeRule.onNode(hasContentDescription("More players")).performScrollTo().performClick()
        composeRule.onNode(chip("Intermediate")).performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Add details for players…"))
            .performScrollTo()
            .performTextInput("Bring water, we play two halves.")
        composeRule.onNodeWithText("Choose a location").performScrollTo().performClick()

        // 4. System opens the Map API
        composeRule.waitForText("SELECTED LOCATION")
        composeRule.onNodeWithText("Drag the map to move the pin, or search above.").assertIsDisplayed()

        // 5. Game Organiser selects the game location on the map
        dragMap()

        // 6. System determines and displays the address of the selected location, then closes the map
        waitForAddressLookup()
        composeRule.onNode(hasText("Dropped pin")).assertDoesNotExist()
        composeRule.onNode(confirmLocationButton).performClick()
        composeRule.waitForText("Change")
        composeRule.onNode(hasText("Choose a location")).assertDoesNotExist()

        // 7. Game Organiser clicks "Create game"
        // 8. System creates the game and displays a confirmation message
        expectToast("Game created") {
            composeRule.onNode(createButton).performScrollTo().assertIsEnabled().performClick()
        }

        // the game is on Home, with the 11 players allowed
        composeRule.waitAndScrollTo(homeCard(title) and hasText("1/11"))
        composeRule.onNode(homeCard(title) and hasText("1/11")).assertIsDisplayed()
    }

    // failure scenario 6a: the Map API cannot determine an address
    @Test
    fun createGame_noAddress_usesDroppedPin() {
        openCreateGame()
        fillInDetails(newGameTitle("E2E No Address"), LocalDate.now().plusDays(DAYS_AHEAD))

        // 6a. The Map API cannot determine an address for the selected location. The phone looks
        //     up addresses over the network, so in airplane mode it finds none
        setAirplaneMode(true)
        composeRule.onNodeWithText("Choose a location").performScrollTo().performClick()

        // 6a1. System shows the location as "Dropped pin", with its coordinates in place of the
        //      address. The Game Organiser can confirm it or move the pin to another spot
        composeRule.waitForText("Dropped pin", ADDRESS_TIMEOUT_MILLIS)
        composeRule.onNodeWithText(MAP_START_COORDINATES).assertIsDisplayed()
        composeRule.onNode(confirmLocationButton).assertIsEnabled().performClick()
        composeRule.waitForText("Change")
        composeRule.onNodeWithText("Dropped pin").performScrollTo().assertIsDisplayed()
        composeRule.onNode(createButton).performScrollTo().assertIsEnabled()
    }

    // failure scenario 7a: a compulsory field or the location is missing
    @Test
    fun createGame_missingField_keepsCreateDisabled() {
        val title = newGameTitle("E2E Missing Field")
        openCreateGame()

        // 7a. Game Organiser left a compulsory field empty or did not select a location
        // 7a1. System keeps the "Create game" button disabled until every compulsory field,
        //      marked with *, is filled in and a location is selected
        listOf("Sport *", "Title *", "Date *", "Time *", "Location *")
            .forEach { label -> composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed() }
        composeRule.onNode(createButton).performScrollTo().assertIsNotEnabled()

        // every field but the location
        fillInDetails(title, LocalDate.now().plusDays(DAYS_AHEAD))
        composeRule.onNode(createButton).performScrollTo().assertIsNotEnabled()

        // with the location too, the game can be created
        chooseLocation()
        composeRule.onNode(createButton).performScrollTo().assertIsEnabled()

        // without the title, it can't any more
        composeRule.onNode(hasSetTextAction() and hasText(title)).performScrollTo().performTextReplacement("")
        composeRule.onNode(createButton).performScrollTo().assertIsNotEnabled()
    }

    // failure scenario 7b: an invalid value
    @Test
    fun createGame_startInThePast_showsError() {
        openCreateGame()

        // 7b. Game Organiser entered an invalid value in any field: today at 12:00 AM has
        //     already passed. The form doesn't let any other field get an invalid value
        composeRule.onNode(chip("Basketball")).performScrollTo().performClick()
        composeRule.onNode(titleField).performScrollTo().performTextInput(newGameTitle("E2E Past Start"))
        pickDate(LocalDate.now())
        pickTime(midnight = true)
        chooseLocation()

        // 7b1. System displays an error message identifying the invalid field and the reason
        composeRule.onNodeWithText("12:00 AM").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("The game has to start in the future.").assertIsDisplayed()
        // every field is filled in, so the start time is the only problem
        composeRule.onNode(createButton).performScrollTo().assertIsNotEnabled()
    }

    // failure scenario 8a: the phone cannot reach the server
    @Test
    fun createGame_noConnection_showsErrorAndTryAgain() {
        val title = newGameTitle("E2E Offline Game")
        openCreateGame()
        fillInDetails(title, LocalDate.now().plusDays(DAYS_AHEAD))
        chooseLocation()

        // 8a. The phone cannot reach the server (e.g. no internet connection)
        setAirplaneMode(true)

        // 8a1. System displays "Couldn't reach the server, try again" and lets the Game Organiser try again
        expectToast("Couldn't reach the server, try again") {
            composeRule.onNode(createButton).performScrollTo().performClick()
        }
        composeRule.waitForNode(createButton and isEnabled())
        composeRule.onNode(hasSetTextAction() and hasText(title)).performScrollTo().assertIsDisplayed()

        // the Game Organiser reconnects and creates the same game
        setAirplaneMode(false)
        expectToast("Game created") {
            composeRule.onNode(createButton).performScrollTo().performClick()
        }
        composeRule.waitAndScrollTo(homeCard(title))
    }

    private val createButton = hasText("Create Game") and hasClickAction()
    private val titleField = hasSetTextAction() and hasText("e.g. Pickup Basketball")
    private val confirmLocationButton = hasText("Confirm Location") and hasClickAction()

    // a chip on the form, e.g. "Basketball"
    private fun chip(label: String) = hasText(label) and hasClickAction()

    // a game's card on Home. It's clickable, so its texts are merged into one node
    private fun homeCard(title: String) = hasText(title) and hasClickAction()

    // a title no earlier run used, e.g. "E2E New Game 143052"
    private fun newGameTitle(prefix: String) = "$prefix ${LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))}"

    // Home > "Organize a new game", once Home has loaded User A's games
    private fun openCreateGame() {
        val organizeButton = hasText("Organize a new game") and hasClickAction()
        composeRule.openTab("Home")
        composeRule.waitAndScrollTo(homeCard(ORGANIZED_GAME))
        composeRule.scrollTo(organizeButton)
        composeRule.onNode(organizeButton).performClick()
        composeRule.waitForText("Sport *")
    }

    // a basketball game called title, on date at 6:00 PM. Leaves the location empty
    private fun fillInDetails(title: String, date: LocalDate) {
        composeRule.onNode(chip("Basketball")).performScrollTo().performClick()
        composeRule.onNode(titleField).performScrollTo().performTextInput(title)
        pickDate(date)
        pickTime(midnight = false)
    }

    // types the date in the date picker, which only allows today and later days
    private fun pickDate(date: LocalDate) {
        composeRule.onNodeWithText("Select date").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Switch to text input mode").performClick()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(date.format(DATE_INPUT_FORMAT))
        composeRule.onNode(hasText("OK") and hasClickAction()).performClick()
    }

    // keeps the time picker's 6:00 PM, or picks 12:00 AM (midnight)
    private fun pickTime(midnight: Boolean) {
        composeRule.onNodeWithText("Select time").performScrollTo().performClick()
        if (midnight) {
            composeRule.onNodeWithContentDescription("12 o'clock").performClick()
            composeRule.onNode(hasText("AM") and hasClickAction()).performClick()
        }
        composeRule.onNode(hasText("OK") and hasClickAction()).performClick()
    }

    // opens the map, waits for the address of the place under the pin and confirms it
    private fun chooseLocation() {
        composeRule.onNodeWithText("Choose a location").performScrollTo().performClick()
        composeRule.waitForText("SELECTED LOCATION")
        waitForAddressLookup()
        composeRule.onNode(confirmLocationButton).performClick()
        composeRule.waitForText("Change")
    }

    // "Confirm Location" is enabled once the phone has looked up the place under the pin
    private fun waitForAddressLookup() {
        composeRule.waitForNode(confirmLocationButton and isEnabled(), ADDRESS_TIMEOUT_MILLIS)
    }

    /*  drags the map a little to the left, so the pin is over another place. The map is drawn
        by Google Maps, not Compose, so UI Automator swipes on the screen
     */
    private fun dragMap() {
        val middleY = device.displayHeight * 2 / 5
        device.swipe(device.displayWidth * 3 / 5, middleY, device.displayWidth * 2 / 5, middleY, 50)
        composeRule.waitForIdle()
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