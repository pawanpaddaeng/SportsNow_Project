package com.example.cpen321application.navigation.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.cpen321application.user.clearGoogleSignIn
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
    val friendsState = FriendsState(context, scope)
    val notificationsState = NotificationsState(context, scope)

    var profileUserId by mutableStateOf<Int?>(null)
        private set

    fun openProfile(userId: Int) {
        profileUserId = userId
    }

    fun closeProfile() {
        profileUserId = null
    }

    // a saved token from a previous session signs in automatically, an expired one is renewed later
    fun startDestination(): String {
        val savedToken = AuthTokenStore.load(context) ?: return Routes.SIGN_IN
        jwtToken = savedToken
        return Routes.HOME
    }

    // bottom bar. Home stays underneath the other tabs so back from any of them returns to it
    fun openTab(tab: BottomTab) {
        val route = when (tab) {
            BottomTab.Home -> {
                navController.popBackStack(Routes.HOME, inclusive = false)
                return
            }
            BottomTab.Discover -> Routes.DISCOVER
            BottomTab.Friends -> Routes.FRIENDS
            BottomTab.Notifications -> Routes.NOTIFICATIONS
            BottomTab.Profile -> Routes.PROFILE
        }
        navController.navigate(route) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    // forgets the session and shows sign in with message, clearing every other screen
    fun returnToSignIn(message: String = "Your session ended. Please sign in again.") {
        jwtToken = null
        AuthTokenStore.clear(context)
        // the next user to sign in shouldn't see this user's data
        homeState.clear()
        discoverState.clear()
        profileState.clear()
        friendsState.clear()
        notificationsState.clear()
        closeProfile()
        navController.navigate(Routes.SIGN_IN) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
        navController.currentBackStackEntry?.savedStateHandle?.set(SIGN_IN_MESSAGE_KEY, message)
    }

    // the user chose to sign out, or deleted their account: Google won't pick this account silently again
    fun signOut(message: String, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                clearGoogleSignIn(context)
            } finally {
                onDone()
            }
            returnToSignIn(message)
        }
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
        // the bell's count
        notificationsState.refresh()
    }
}

@Composable
fun rememberAppState(): AppState {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberSafeCoroutineScope()
    return remember(navController, context, scope) { AppState(navController, context, scope) }
}
