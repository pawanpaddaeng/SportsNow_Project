package com.example.cpen321application.navigation.state

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.cpen321application.core.rememberSafeCoroutineScope
import com.example.cpen321application.core.session.AuthTokenStore
import com.example.cpen321application.core.session.jwtToken
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.SIGN_IN_MESSAGE_KEY
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.user.NotificationRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
    State shared by the app's screens, kept for the whole app so loaded data survives
    going to other screens and back. scope shows a message instead of crashing on an
    unexpected error, so every load resets its loading flag in a finally block
 */
class AppState(
    val navController: NavHostController,
    private val context: Context,
    val scope: CoroutineScope
) {
    val homeState = HomeState(context, scope)
    val discoverState = DiscoverState(context, scope)
    val profileState = ProfileState(context, scope)

    // a saved token from a previous session signs in automatically, an expired one is renewed later
    fun startDestination(): String {
        val savedToken = AuthTokenStore.load(context) ?: return Routes.SIGN_IN
        jwtToken = savedToken
        return Routes.HOME
    }

    // bottom bar: Home, Discover and Profile are built, the other tabs aren't yet
    fun openTab(tab: BottomTab) {
        when (tab) {
            BottomTab.Home -> navController.popBackStack(Routes.HOME, inclusive = false)
            // Home stays underneath so back from Discover or Profile returns to it
            BottomTab.Discover -> navController.navigate(Routes.DISCOVER) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
            BottomTab.Profile -> navController.navigate(Routes.PROFILE) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
            else -> Toast.makeText(context, "${tab.label} isn't available yet", Toast.LENGTH_SHORT).show()
        }
    }

    // forgets the session and shows sign in with a message, clearing every other screen
    fun returnToSignIn() {
        jwtToken = null
        AuthTokenStore.clear(context)
        // the next user to sign in shouldn't see this user's data
        homeState.clear()
        discoverState.clear()
        profileState.clear()
        navController.navigate(Routes.SIGN_IN) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
        navController.currentBackStackEntry?.savedStateHandle?.set(
            SIGN_IN_MESSAGE_KEY,
            "Your session ended. Please sign in again."
        )
    }

    fun registerNotifications() {
        scope.launch {
            NotificationRegistration.register(context)
        }
    }

    // keeps the token, then goes home and removes sign in/up so back doesn't return to them
    fun completeSignIn(token: String) {
        jwtToken = token
        AuthTokenStore.save(context, token)
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.SIGN_IN) { inclusive = true }
        }
        registerNotifications()
    }
}

@Composable
fun rememberAppState(): AppState {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberSafeCoroutineScope()
    return remember(navController, context, scope) { AppState(navController, context, scope) }
}
