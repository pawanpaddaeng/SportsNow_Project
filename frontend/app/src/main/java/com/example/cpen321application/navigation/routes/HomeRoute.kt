package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.home.HomeActions
import com.example.cpen321application.ui.screens.home.HomeScreen
import com.example.cpen321application.ui.screens.home.HomeUiState

@Composable
fun HomeRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val homeState = app.homeState
    OnResume(backStackEntry) { homeState.refresh() }

    HomeScreen(
        state = HomeUiState(
            joinedGames = homeState.joinedGames,
            organizedGames = homeState.organizedGames,
            isRefreshing = homeState.isRefreshing,
            errorMessage = homeState.error
        ),
        actions = HomeActions(
            onRefresh = { homeState.refresh() },
            onCreateGame = { app.navController.navigate(Routes.CREATE_GAME) },
            onGameClick = { game ->
                game.id.toIntOrNull()?.let { app.navController.navigate(Routes.gameDetails(it)) }
            },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}
