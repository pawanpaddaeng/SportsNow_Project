package com.example.cpen321application.ui.screens.games

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.example.cpen321application.core.rememberSafeCoroutineScope
import com.example.cpen321application.core.rememberUnexpectedErrorHandler
import com.example.cpen321application.core.runGuarded
import com.example.cpen321application.game.location.createPlacesClient
import com.example.cpen321application.game.location.describePoint
import com.example.cpen321application.game.location.fetchPlace
import com.example.cpen321application.game.location.findSuggestions
import com.example.cpen321application.game.location.hasLocationPermission
import com.example.cpen321application.game.model.GameLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// UBC campus, where the map starts when no location was picked yet
private val UBC_CAMPUS = LatLng(49.2606, -123.2460)
private const val DEFAULT_ZOOM = 15f
private const val PICKED_ZOOM = 17f

/*
    Pick Location screen from the Figma mockup: a full screen Google map with a
    fixed pin in the middle. The user drags the map under the pin or searches for
    a place, then confirms. onLocationPicked gets the chosen place
 */
@Composable
fun PickLocationScreen(
    onLocationPicked: (GameLocation) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialLocation: GameLocation? = null
) {
    val context = LocalContext.current
    val scope = rememberSafeCoroutineScope()
    val safetyNet = rememberUnexpectedErrorHandler()
    val focusManager = LocalFocusManager.current
    val placesClient = remember { createPlacesClient(context) }
    val sessionToken = remember { AutocompleteSessionToken.newInstance() }

    val start = initialLocation?.let { LatLng(it.latitude, it.longitude) } ?: UBC_CAMPUS
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            start,
            if (initialLocation != null) PICKED_ZOOM else DEFAULT_ZOOM
        )
    }

    var selected by remember { mutableStateOf(initialLocation) }
    var isResolving by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    val hasLocationPermission = rememberLocationPermission()

    // the place at point becomes the selection, the sheet says "Finding address…" meanwhile
    suspend fun selectPlaceAt(point: LatLng) {
        isResolving = true
        runGuarded(safetyNet) {
            try {
                selected = describePoint(context, point)
            } finally {
                isResolving = false
            }
        }
    }

    // a search suggestion was tapped: select that place and move the map to it
    fun pickSuggestion(client: PlacesClient, prediction: AutocompletePrediction) {
        query = ""
        suggestions = emptyList()
        focusManager.clearFocus()
        scope.launch {
            val place = fetchPlace(client, prediction.placeId, sessionToken)
            if (place != null) {
                selected = place
                cameraState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), PICKED_ZOOM)
                )
            }
        }
    }

    // nothing picked yet: start with the place at the starting point
    LaunchedEffect(Unit) {
        if (selected == null) selectPlaceAt(start)
    }

    // when the user stops dragging, the place under the pin becomes the selection
    LaunchedEffect(cameraState.isMoving) {
        if (!cameraState.isMoving && cameraState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            selectPlaceAt(cameraState.position.target)
        }
    }

    // search suggestions for text once typing pauses, none for less than 2 letters
    suspend fun showSuggestions(text: String) {
        if (placesClient == null || text.length < 2) {
            suggestions = emptyList()
        } else {
            delay(300)
            runGuarded(safetyNet) {
                suggestions = findSuggestions(placesClient, text, sessionToken)
            }
        }
    }

    LaunchedEffect(query) { showSuggestions(query.trim()) }

    val isBusy = isResolving || cameraState.isMoving

    Box(modifier = modifier.fillMaxSize()) {
        PickerMap(cameraState = cameraState, showMyLocation = hasLocationPermission)
        CenterPin(modifier = Modifier.align(Alignment.Center))
        SearchPanel(
            query = query,
            onQueryChange = { query = it },
            searchEnabled = placesClient != null,
            suggestions = suggestions,
            // suggestions only exist when search is available
            onPick = { prediction -> placesClient?.let { pickSuggestion(it, prediction) } },
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        SelectedPlaceSheet(
            selected = selected,
            isBusy = isBusy,
            onConfirm = { selected?.let(onLocationPicked) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/*  whether the app may use the user's location, asked for once when the screen opens.
    It only adds the blue dot and "my location" button, the picker works without it
 */
@Composable
private fun rememberLocationPermission(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> granted = results.values.any { it } }
    LaunchedEffect(Unit) {
        if (!granted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
    return granted
}
