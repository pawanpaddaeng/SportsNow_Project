package com.example.cpen321application.ui.screens.games

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.cpen321application.BuildConfig
import com.example.cpen321application.ui.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "PickLocation"

// UBC campus, where the map starts when no location was picked yet
private val UBC_CAMPUS = LatLng(49.2606, -123.2460)
private const val DEFAULT_ZOOM = 15f
private const val PICKED_ZOOM = 17f

// place search results are biased towards UBC and the area around it
private val SEARCH_BOUNDS = RectangularBounds.newInstance(
    LatLng(49.23, -123.27),
    LatLng(49.29, -123.20)
)

// map space covered by the search bar and the bottom sheet, the map's centre is
// moved into the visible area between them so the pin marks the real centre
private val MAP_TOP_PADDING = 88.dp
private val MAP_BOTTOM_PADDING = 240.dp
private val PIN_SIZE = 48.dp

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
    val scope = rememberCoroutineScope()
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

    // location permission only adds the blue dot and "my location" button, the picker works without it
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasLocationPermission = results.values.any { it } }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // nothing picked yet: start with the place at the starting point
    LaunchedEffect(Unit) {
        if (selected == null) {
            isResolving = true
            selected = describePoint(context, start)
            isResolving = false
        }
    }

    // when the user stops dragging, the place under the pin becomes the selection
    LaunchedEffect(cameraState.isMoving) {
        if (!cameraState.isMoving &&
            cameraState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            isResolving = true
            try {
                selected = describePoint(context, cameraState.position.target)
            } finally {
                isResolving = false
            }
        }
    }

    // search suggestions once typing pauses
    LaunchedEffect(query) {
        val text = query.trim()
        if (placesClient == null || text.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = findSuggestions(placesClient, text, sessionToken)
    }

    val isBusy = isResolving || cameraState.isMoving

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            contentPadding = PaddingValues(top = MAP_TOP_PADDING, bottom = MAP_BOTTOM_PADDING),
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = hasLocationPermission,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false
            )
        )

        // fixed pin over the map's centre, its tip marks the chosen point
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = Color(0xFFD93025),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (MAP_TOP_PADDING - MAP_BOTTOM_PADDING) / 2 - PIN_SIZE / 2)
                .size(PIN_SIZE)
        )

        // search bar and suggestions
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SportsOnSurface
                        )
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        enabled = placesClient != null,
                        singleLine = true,
                        placeholder = {
                            Text(if (placesClient != null) "Search for a place" else "Search unavailable")
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = SportsPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = SportsOnSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        suggestions.forEach { prediction ->
                            SuggestionRow(
                                prediction = prediction,
                                onClick = {
                                    val client = placesClient ?: return@SuggestionRow
                                    query = ""
                                    suggestions = emptyList()
                                    focusManager.clearFocus()
                                    scope.launch {
                                        val place = fetchPlace(client, prediction.placeId, sessionToken)
                                            ?: return@launch
                                        selected = place
                                        cameraState.animate(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(place.latitude, place.longitude),
                                                PICKED_ZOOM
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // bottom sheet with the selected place
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SportsOutlineVariant)
                )
                Text(
                    text = "SELECTED LOCATION",
                    color = SportsOnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isBusy) "Finding address…" else selected?.name ?: "Move the map to choose a place",
                    color = SportsOnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isBusy) " " else selected?.address ?: " ",
                    color = SportsOnSurfaceVariant,
                    fontSize = 14.sp
                )
                Text(
                    text = "Drag the map to move the pin, or search above.",
                    color = SportsOnSurfaceVariant,
                    fontSize = 12.sp
                )
                Button(
                    onClick = { selected?.let(onLocationPicked) },
                    enabled = selected != null && !isBusy,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(48.dp)
                ) {
                    Text(text = "Confirm Location", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(prediction: AutocompletePrediction, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = Color(0xFFD93025),
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = prediction.getPrimaryText(null).toString(),
                color = SportsOnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = prediction.getSecondaryText(null).toString(),
                color = SportsOnSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

// Places client for search, null if no MAPS_API_KEY is set (search is then disabled)
private fun createPlacesClient(context: Context): PlacesClient? {
    val apiKey = BuildConfig.MAPS_API_KEY
    if (apiKey.isBlank()) return null
    return try {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
        }
        Places.createClient(context)
    } catch (e: Exception) {
        Log.w(TAG, "Could not start Places search", e)
        null
    }
}

private suspend fun findSuggestions(
    client: PlacesClient,
    query: String,
    token: AutocompleteSessionToken
): List<AutocompletePrediction> {
    val request = FindAutocompletePredictionsRequest.builder()
        .setQuery(query)
        .setSessionToken(token)
        .setLocationBias(SEARCH_BOUNDS)
        .build()
    return try {
        client.findAutocompletePredictions(request).await().autocompletePredictions
    } catch (e: Exception) {
        Log.w(TAG, "Place search failed", e)
        emptyList()
    }
}

// name, address and coordinates of a place picked from the search suggestions
private suspend fun fetchPlace(
    client: PlacesClient,
    placeId: String,
    token: AutocompleteSessionToken
): GameLocation? {
    val request = FetchPlaceRequest.builder(
        placeId,
        listOf(Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)
    ).setSessionToken(token).build()
    return try {
        val place = client.fetchPlace(request).await().place
        val point = place.location ?: return null
        GameLocation(
            name = place.displayName ?: place.formattedAddress ?: "Selected place",
            address = place.formattedAddress ?: "",
            latitude = point.latitude,
            longitude = point.longitude
        )
    } catch (e: Exception) {
        Log.w(TAG, "Could not load the place", e)
        null
    }
}

/*  turns a point on the map into a place with a name and address using the
    phone's geocoder. Falls back to the coordinates if no address is found
 */
private suspend fun describePoint(context: Context, point: LatLng): GameLocation {
    val address = reverseGeocode(context, point)
    val street = address?.thoroughfare?.let { listOfNotNull(address.subThoroughfare, it).joinToString(" ") }
    val coordinates = "%.5f, %.5f".format(point.latitude, point.longitude)
    return GameLocation(
        name = address?.featureName?.takeUnless { it == address.subThoroughfare } ?: street ?: "Dropped pin",
        address = address?.getAddressLine(0) ?: coordinates,
        latitude = point.latitude,
        longitude = point.longitude
    )
}

private suspend fun reverseGeocode(context: Context, point: LatLng): Address? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context)
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(point.latitude, point.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not find an address for the point", e)
        null
    }
}

// waits for a Google Play services Task without blocking
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
