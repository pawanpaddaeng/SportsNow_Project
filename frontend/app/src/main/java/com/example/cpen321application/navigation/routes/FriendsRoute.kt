package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.friends.FriendsActions
import com.example.cpen321application.ui.screens.friends.FriendsScreen
import com.example.cpen321application.ui.screens.friends.FriendsUiState

// the Friends tab, its lists stay empty until the screen is connected to the server
@Composable
fun FriendsRoute(app: AppState, modifier: Modifier) {
    var query by remember { mutableStateOf("") }

    FriendsScreen(
        state = FriendsUiState(query = query),
        actions = FriendsActions(
            onQueryChange = { query = it },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}
