package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.profile.EditProfileScreen
import com.example.cpen321application.ui.screens.profile.ProfileScreen

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
    OnResume(backStackEntry) { profileState.refresh() }

    ProfileScreen(
        profile = profileState.profile,
        modifier = modifier,
        isLoading = profileState.isLoading,
        errorMessage = profileState.error,
        onEdit = {
            profileState.saveError = null
            app.navController.navigate(Routes.EDIT_PROFILE)
        },
        onRetry = { profileState.refresh() },
        onTabSelected = { tab -> app.openTab(tab) }
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
