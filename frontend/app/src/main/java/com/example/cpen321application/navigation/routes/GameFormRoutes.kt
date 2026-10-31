package com.example.cpen321application.navigation.routes

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.core.rememberUnexpectedErrorHandler
import com.example.cpen321application.core.runGuarded
import com.example.cpen321application.game.model.GameInfoForm
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.game.model.toGameSummary
import com.example.cpen321application.game.repository.COULD_NOT_LOAD_GAME
import com.example.cpen321application.game.repository.EditableGame
import com.example.cpen321application.game.repository.GameChangeResult
import com.example.cpen321application.game.repository.createGame
import com.example.cpen321application.game.repository.loadEditableGame
import com.example.cpen321application.game.repository.updateGame
import com.example.cpen321application.navigation.PICKED_LOCATION_KEY
import com.example.cpen321application.navigation.PICK_START_KEY
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.games.InputGameInfoActions
import com.example.cpen321application.ui.screens.games.InputGameInfoScreen
import com.example.cpen321application.ui.screens.games.InputGameInfoUiState
import com.example.cpen321application.ui.screens.games.PickLocationScreen
import kotlinx.coroutines.launch

@Composable
fun CreateGameRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val navController = app.navController
    var isSubmitting by remember { mutableStateOf(false) }
    // set by the Pick Location screen when the user confirms a place
    val pickedLocation by backStackEntry.savedStateHandle
        .getStateFlow<GameLocation?>(PICKED_LOCATION_KEY, null)
        .collectAsState()

    fun submit(form: GameInfoForm) {
        isSubmitting = true
        app.scope.launch {
            val result = try {
                createGame(context, form)
            } finally {
                isSubmitting = false
            }
            when (result) {
                is GameChangeResult.Done -> {
                    // show the new game right away, at the top of Organized Games
                    result.game?.let { app.homeState.organizedGames.add(0, it.toGameSummary()) }
                    Toast.makeText(context, "Game created", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                is GameChangeResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    InputGameInfoScreen(
        state = InputGameInfoUiState(isSubmitting = isSubmitting, location = pickedLocation),
        actions = InputGameInfoActions(
            onSubmit = { form -> submit(form) },
            onBack = { navController.popBackStack() },
            onPickLocation = { navController.navigate(Routes.PICK_LOCATION) }
        ),
        modifier = modifier
    )
}

// the same Input Game Info screen as create, filled in with the game
@Composable
fun EditGameRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val navController = app.navController
    val safetyNet = rememberUnexpectedErrorHandler()
    val gameId = backStackEntry.gameId()
    // saveable so the loaded game and its player count survive going to Pick Location
    var initial by rememberSaveable { mutableStateOf<GameInfoForm?>(null) }
    var playerCount by rememberSaveable { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    // set by the Pick Location screen, starts as the game's current place
    val pickedLocation by backStackEntry.savedStateHandle
        .getStateFlow<GameLocation?>(PICKED_LOCATION_KEY, null)
        .collectAsState()

    suspend fun loadGame() {
        when (val game = loadEditableGame(context, gameId)) {
            is EditableGame.Loaded -> {
                playerCount = game.playerCount
                if (backStackEntry.savedStateHandle.get<GameLocation>(PICKED_LOCATION_KEY) == null) {
                    backStackEntry.savedStateHandle[PICKED_LOCATION_KEY] = game.form.location
                }
                initial = game.form
            }
            is EditableGame.Unavailable -> loadError = game.message
        }
    }

    fun submit(form: GameInfoForm) {
        isSubmitting = true
        app.scope.launch {
            val result = try {
                updateGame(context, gameId, form)
            } finally {
                isSubmitting = false
            }
            when (result) {
                // Game Details reloads when it's shown again, so it has the changes
                is GameChangeResult.Done -> {
                    Toast.makeText(context, "Changes saved", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                is GameChangeResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(gameId) {
        if (initial == null) {
            runGuarded(safetyNet) { loadGame() }
            // nothing loaded (an unexpected error): show a message instead of loading forever
            if (initial == null && loadError == null) loadError = COULD_NOT_LOAD_GAME
        }
    }

    InputGameInfoScreen(
        state = InputGameInfoUiState(
            screenTitle = "Edit Game",
            submitLabel = "Save Changes",
            initial = initial,
            minPlayers = playerCount,
            isSubmitting = isSubmitting,
            isLoading = initial == null && loadError == null,
            loadError = loadError,
            location = pickedLocation
        ),
        actions = InputGameInfoActions(
            onSubmit = { form -> submit(form) },
            onBack = { navController.popBackStack() },
            onPickLocation = { navController.navigate(Routes.PICK_LOCATION) }
        ),
        modifier = modifier
    )
}

@Composable
fun PickLocationRoute(app: AppState, modifier: Modifier) {
    val navController = app.navController
    // starts where Discover asked, otherwise at the place already chosen on the form
    val currentLocation = remember {
        val handle = navController.previousBackStackEntry?.savedStateHandle
        handle?.get<GameLocation>(PICK_START_KEY) ?: handle?.get<GameLocation>(PICKED_LOCATION_KEY)
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
