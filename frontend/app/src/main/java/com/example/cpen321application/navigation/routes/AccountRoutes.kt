package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.profile.EditProfileScreen
import com.example.cpen321application.ui.screens.profile.ProfileAction
import com.example.cpen321application.ui.screens.profile.ProfileActions
import com.example.cpen321application.ui.screens.profile.ProfileScreen
import com.example.cpen321application.ui.screens.profile.ProfileUiState

/*  calls action every time the screen is shown: when it first opens, when the user
    comes back from another screen, and when the app returns to the foreground
 */
@Composable
fun OnResume(backStackEntry: NavBackStackEntry, action: () -> Unit) {
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(backStackEntry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentAction()
        }
        backStackEntry.lifecycle.addObserver(observer)
        onDispose { backStackEntry.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun ProfileRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val profileState = app.profileState
    var isSigningOut by remember { mutableStateOf(false) }
    OnResume(backStackEntry) { profileState.refresh() }

    // runs an action the user confirmed, both end on sign in
    fun runAction(action: ProfileAction) {
        when (action) {
            ProfileAction.SignOut -> {
                isSigningOut = true
                app.signOut("You signed out.") { isSigningOut = false }
            }
            ProfileAction.DeleteAccount -> profileState.deleteAccount {
                app.signOut("Your account was deleted.")
            }
        }
    }

    ProfileScreen(
        state = ProfileUiState(
            profile = profileState.profile,
            reputationScore = profileState.reputationScore,
            isLoading = profileState.isLoading,
            errorMessage = profileState.error,
            busyAction = when {
                profileState.isDeleting -> ProfileAction.DeleteAccount
                isSigningOut -> ProfileAction.SignOut
                else -> null
            }
        ),
        actions = ProfileActions(
            onEdit = {
                profileState.saveError = null
                app.navController.navigate(Routes.EDIT_PROFILE)
            },
            onRetry = { profileState.refresh() },
            onProfileAction = { action -> runAction(action) },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}

@Composable
fun EditProfileRoute(app: AppState, modifier: Modifier) {
    val navController = app.navController
    val profileState = app.profileState
    val current = profileState.profile
    if (current == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
    } else {
        EditProfileScreen(
            profile = current,
            onSave = { form ->
                profileState.save(form) {
                    // the user may have left Edit Profile while it was saving
                    if (navController.currentDestination?.route == Routes.EDIT_PROFILE) {
                        navController.popBackStack()
                    }
                }
            },
            onCancel = { navController.popBackStack() },
            modifier = modifier,
            isSaving = profileState.isSaving,
            errorMessage = profileState.saveError
        )
    }
}
