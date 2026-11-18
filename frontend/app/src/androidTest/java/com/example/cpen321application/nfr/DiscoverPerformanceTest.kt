package com.example.cpen321application.nfr

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cpen321application.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*  NFR 1 (Performance): Discover should finish loading and display the nearby games in under
    5 seconds from when the user selects the compass. This measures the whole path on the phone:
    getting the location, the request to the server, and drawing the list.

    Run it with scripts/run-frontend-nfr-tests, which starts the backend and the app and waits
    for you to sign in, since Google Sign-In can't be automated.
 */
@RunWith(AndroidJUnit4::class)
class DiscoverPerformanceTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val testProviders = mutableListOf<String>()

    // gives the app location access and puts the phone at the test location, so the
    // permission pop-up doesn't appear and the location doesn't depend on the emulator
    @Before
    fun setUp() {
        val app = context.packageName
        shell("pm grant $app android.permission.ACCESS_FINE_LOCATION")
        shell("pm grant $app android.permission.ACCESS_COARSE_LOCATION")
        shell("appops set $app android:mock_location allow")
        setPhoneLocation(TEST_LATITUDE, TEST_LONGITUDE)
    }

    @After
    fun tearDown() {
        testProviders.forEach { locationManager.removeTestProvider(it) }
    }

    // Input: a signed in user on the Home screen, with the phone at the test location
    // Expected behavior: after clicking the compass, Discover shows its result in under 5 s
    // Expected output: "N games nearby" with the game cards, or the "No games nearby" message
    @Test
    fun discoverShowsNearbyGamesWithin5Seconds() {
        // 1. wait for the app to open on the Home screen, not timed
        compose.waitUntil(timeoutMillis = APP_START_TIMEOUT_MS) {
            compose.onAllNodes(hasText(DISCOVER_TAB)).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. select the compass, starting the clock
        val start = SystemClock.elapsedRealtime()
        compose.onNodeWithText(DISCOVER_TAB).performClick()

        // 3. wait for the result; waitUntil fails the test if it isn't shown within 5 s
        compose.waitUntil(timeoutMillis = LIMIT_MS) {
            compose.onAllNodes(hasText(LOADED_TEXT, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        Log.i(TAG, "Discover showed its result in ${SystemClock.elapsedRealtime() - start} ms")
    }

    // runs a shell command as the test, e.g. to grant a permission
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    // replaces each provider the app reads from with a test one that is at this location
    @Suppress("DEPRECATION") // the non-deprecated addTestProvider needs Android 12, minSdk is 26
    private fun setPhoneLocation(latitude: Double, longitude: Double) {
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        providers.forEach { provider ->
            locationManager.addTestProvider(
                provider, false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(provider, true)
            locationManager.setTestProviderLocation(provider, Location(provider).apply {
                this.latitude = latitude
                this.longitude = longitude
                accuracy = 1f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            })
            testProviders.add(provider)
        }
    }

    private companion object {
        const val TAG = "NFR1"
        // UBC, the area the app's test games are made in
        const val TEST_LATITUDE = 49.2606
        const val TEST_LONGITUDE = -123.2460
        const val DISCOVER_TAB = "Discover"
        // in both "3 games nearby" and "No games nearby right now...", which only show once loaded
        const val LOADED_TEXT = "nearby"
        const val LIMIT_MS = 5_000L
        const val APP_START_TIMEOUT_MS = 15_000L
    }
}
