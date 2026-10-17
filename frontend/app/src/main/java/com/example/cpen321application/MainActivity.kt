package com.example.cpen321application

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cpen321application.ui.screens.CreateGameScreen
import com.example.cpen321application.ui.screens.GameLocation
import com.example.cpen321application.ui.screens.PickLocationScreen
import com.example.cpen321application.ui.screens.HomeScreen
import com.example.cpen321application.ui.screens.SignInScreen
import com.example.cpen321application.ui.screens.SignUpScreen
import com.example.cpen321application.ui.screens.sampleJoinedGames
import com.example.cpen321application.ui.screens.sampleOrganizedGames
import com.example.cpen321application.ui.screens.toGameSummary
import com.example.cpen321application.ui.theme.CPEN321ApplicationTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/*
    The main activity class that connects our navigation and
    screen controller to the phone.
    Used as provided in the template
 */
class MainActivity : ComponentActivity() {
    // result ignored: if denied, notifications just aren't shown
    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel(this)
        requestNotificationPermission()
        setContent {
            CPEN321ApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    // Android 13+ asks the user before an app can show notifications
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

//variables to store a logged-in user's name as returned by backend
var firstName: String? = "Not"
var lastName: String? = "Logged In"

// JWT from the server after sign up / sign in, sent with future requests.
// Also saved with AuthTokenStore so the user stays signed in after the app closes
var jwtToken: String? = null

// savedStateHandle key for a message shown on the sign in screen
private const val SIGN_IN_MESSAGE_KEY = "sign_in_message"

// savedStateHandle key for the place chosen on the Pick Location screen
private const val PICKED_LOCATION_KEY = "picked_location"

/*
    App navigation composable:
    Deals with the screens in ui/screens and handles
    their interactions with each other, such as switching
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // sign in automatically with a token from a previous session, if it expired
    // the notification registration below renews it
    val startDestination = remember {
        val savedToken = AuthTokenStore.load(context)
        if (savedToken != null) {
            jwtToken = savedToken
            "home"
        } else {
            "sign_in"
        }
    }

    // games shown on Home. Kept here so they survive going to other screens and back
    // TODO: load the user's games from the backend (get games api),
    // the sample games are placeholders until then
    val organizedGames = remember { mutableStateListOf(*sampleOrganizedGames.toTypedArray()) }

    // forget the session and show sign in with a message, clearing every other screen
    fun returnToSignIn() {
        jwtToken = null
        AuthTokenStore.clear(context)
        navController.navigate("sign_in") {
            popUpTo(navController.graph.id) { inclusive = true }
        }
        navController.currentBackStackEntry?.savedStateHandle?.set(
            SIGN_IN_MESSAGE_KEY,
            "Your session ended. Please sign in again."
        )
    }

    // send this device's Firebase token to the server, renewing an expired JWT
    // silently if needed. Goes back to sign in if the session can't be used
    fun registerNotifications() {
        scope.launch {
            if (NotificationRegistration.register(context) == NotifRegistrationResult.SIGN_IN_REQUIRED) {
                returnToSignIn()
            }
        }
    }

    // keep the token for this session and future launches, then go home
    // and remove sign in/up so back doesn't return to them
    fun completeSignIn(token: String) {
        jwtToken = token
        AuthTokenStore.save(context, token)
        navController.navigate("home") {
            popUpTo("sign_in") { inclusive = true }
        }
        registerNotifications()
    }

    // signed in from a previous session: register notifications once at start
    LaunchedEffect(Unit) {
        if (startDestination == "home") {
            registerNotifications()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("sign_in") { backStackEntry ->
            // message left by another screen, e.g. sign up when the account already exists
            val infoMessage by backStackEntry.savedStateHandle
                .getStateFlow<String?>(SIGN_IN_MESSAGE_KEY, null)
                .collectAsState()

            SignInScreen(
                onSignedIn = { token -> completeSignIn(token) },
                onNavigateToSignUp = {
                    backStackEntry.savedStateHandle[SIGN_IN_MESSAGE_KEY] = null
                    navController.navigate("sign_up")
                },
                modifier = modifier,
                infoMessage = infoMessage
            )
        }

        composable("sign_up") {
            SignUpScreen(
                onSignedUp = { details, token ->
                    firstName = details.firstName
                    lastName = details.lastName
                    completeSignIn(token)
                },
                onAlreadyExists = {
                    // go back to sign in and tell the user to sign in instead
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        SIGN_IN_MESSAGE_KEY,
                        "An account already exists for this Google account. Please sign in."
                    )
                    navController.popBackStack()
                },
                onNavigateToSignIn = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                modifier = modifier
            )
        }

        composable("home") {
            HomeScreen(
                joinedGames = sampleJoinedGames,
                organizedGames = organizedGames,
                modifier = modifier,
                onCreateGame = { navController.navigate("create_game") }
            )
        }

        composable("create_game") { backStackEntry ->
            var isSubmitting by remember { mutableStateOf(false) }
            // set by the Pick Location screen when the user confirms a place
            val pickedLocation by backStackEntry.savedStateHandle
                .getStateFlow<GameLocation?>(PICKED_LOCATION_KEY, null)
                .collectAsState()

            CreateGameScreen(
                isSubmitting = isSubmitting,
                location = pickedLocation,
                onBack = { navController.popBackStack() },
                onPickLocation = { navController.navigate("pick_location") },
                onCreate = { form ->
                    isSubmitting = true
                    scope.launch {
                        val result = createGame(form)
                        isSubmitting = false
                        when (result) {
                            is CreateGameResult.Created -> {
                                // show the new game right away, at the top of Organized Games
                                organizedGames.add(0, result.game.toGameSummary())
                                Toast.makeText(context, "Game created", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                            is CreateGameResult.Failed ->
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = modifier
            )
        }

        composable("pick_location") {
            // start the map at the place already chosen on Create Game, if any
            val currentLocation = remember {
                navController.previousBackStackEntry?.savedStateHandle?.get<GameLocation>(PICKED_LOCATION_KEY)
            }

            PickLocationScreen(
                initialLocation = currentLocation,
                onLocationPicked = { location ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_LOCATION_KEY, location)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
                modifier = modifier
            )
        }
    }
}

//helper to ensure proper context is passed to credential manager
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/*
    Sign in function that handles signing in with Google and returns
    the Google id token if sign in was successful
 */
suspend fun triggerGoogleSignIn(context: Context): String? {
    val activityContext = context.findActivity()
        ?: throw IllegalStateException("Google Sign-In requires an Activity Context")

    val credentialManager = CredentialManager.create(context)

    //see all google account options on the device(signed in or out)
    val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    /*  fallback "Sign in with Google" flow: unlike the bottom sheet above, it
        still opens when no account is on the device and lets the user add one
     */
    val signInWithGoogleRequest = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_CLIENT_ID).build()
        )
        .build()

    //mutable context to avoid memory leak as suggested by google
    val mutableContext = MutableContextWrapper(activityContext)

    return try {
            val result = try {
                credentialManager.getCredential(
                    request = request,
                    context = mutableContext
                )
            } catch (e: NoCredentialException) {
                credentialManager.getCredential(
                    request = signInWithGoogleRequest,
                    context = mutableContext
                )
            }
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {

                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                print("SUCCESS! Signed into google")
                idToken
            } else {
                print("Unexpected credential type returned")
                null
            }
        } catch (e: GetCredentialException) {
            // Handle failures and return null
            print("EROR: $e")
            null
        }

}

/*
    Silent Google sign in used to renew an expired session: only offers accounts
    that already signed into this app and picks one automatically, so the user
    normally sees no prompt. Returns the Google id token, or null if it can't
    sign in without the user's help
 */
suspend fun triggerSilentGoogleSignIn(context: Context): String? {
    val activityContext = context.findActivity() ?: return null
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setAutoSelectEnabled(true)
        .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val credential = credentialManager.getCredential(
            request = request,
            context = MutableContextWrapper(activityContext)
        ).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            null
        }
    } catch (e: GetCredentialException) {
        null
    }
}
