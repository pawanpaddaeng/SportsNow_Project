package com.example.cpen321application.nfr

import android.content.Context
import android.util.Base64
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cpen321application.MainActivity
import com.example.cpen321application.core.session.AuthTokenStore
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*  NFR 1 (Security): only signed in users can use SportsNow. When the server turns down the
    app's token, the app must send the user back to sign in and clear the token, instead of
    showing old or empty data. The backend tests check every endpoint turns a bad token down;
    this checks what the app does with that answer.

    The app is given a forged token, so this test doesn't need you to sign in. A token you
    already had is put back afterwards, so the other NFR tests stay signed in.
 */
@RunWith(AndroidJUnit4::class)
class SecurityTest {

    // the app is opened in the test, after the forged token is saved
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var previousToken: String? = null
    private var activity: ActivityScenario<MainActivity>? = null

    // keeps the real token, if any, and saves a forged one as if the app had signed in with it
    @Before
    fun setUp() {
        // the app asks for notification permission when it opens, and the pop-up would cover it
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            .close()
        previousToken = AuthTokenStore.load(context)
        AuthTokenStore.save(context, forgedToken())
    }

    // puts the real token back, so the user stays signed in for the other tests
    @After
    fun tearDown() {
        activity?.close()
        previousToken?.let { AuthTokenStore.save(context, it) } ?: AuthTokenStore.clear(context)
    }

    // Input: the app is opened with a saved token the server didn't sign
    // Expected behavior: the server answers 401 invalid_token, and the app ends the session
    //   and clears the saved token
    // Expected output: the sign in screen with "Your session ended. Please sign in again."
    //   and no bottom bar
    @Test
    fun aTokenTheServerTurnsDownSendsTheUserBackToSignIn() {
        // 1. open the app, it starts on Home with the saved token and asks the server for its data
        activity = ActivityScenario.launch(MainActivity::class.java)

        // 2. the server turns the token down, and the app goes back to sign in
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            compose.onAllNodes(hasText(SESSION_ENDED)).fetchSemanticsNodes().isNotEmpty()
        }

        // 3. check the sign in screen is showing, without the signed in app around it
        compose.onNodeWithText(SIGN_IN_TITLE).assertIsDisplayed()
        compose.onAllNodes(hasText(HOME_TAB)).assertCountEquals(0)

        // 4. check the forged token was cleared, so the next launch doesn't use it again
        assertNull(AuthTokenStore.load(context))
    }

    /*  a JWT in the server's format for user 1, with a made-up signature instead of one made
        with the server's secret, as an attacker without the secret would make
     */
    private fun forgedToken(): String {
        val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        fun encode(text: String) = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), flags)
        val nowSeconds = System.currentTimeMillis() / 1000
        val header = encode("""{"alg":"HS256","typ":"JWT"}""")
        val payload = encode("""{"user_id":1,"iat":$nowSeconds,"exp":${nowSeconds + ONE_DAY_SECONDS}}""")
        val signature = Base64.encodeToString(ByteArray(SIGNATURE_BYTES) { it.toByte() }, flags)
        return "$header.$payload.$signature"
    }

    private companion object {
        const val SESSION_ENDED = "Your session ended. Please sign in again."
        const val SIGN_IN_TITLE = "Sign Into the SportsNow Account"
        const val HOME_TAB = "Home"
        const val ONE_DAY_SECONDS = 24 * 60 * 60
        const val SIGNATURE_BYTES = 32
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
