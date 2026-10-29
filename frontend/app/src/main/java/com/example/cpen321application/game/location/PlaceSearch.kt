package com.example.cpen321application.game.location

import android.content.Context
import android.util.Log
import com.example.cpen321application.BuildConfig
import com.example.cpen321application.game.model.GameLocation
import com.google.android.gms.common.api.ApiException
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "PlaceSearch"

// place search results are biased towards UBC and the area around it
private val SEARCH_BOUNDS = RectangularBounds.newInstance(
    LatLng(49.23, -123.27),
    LatLng(49.29, -123.20)
)

// Places client for search, null if no MAPS_API_KEY is set (search is then disabled)
fun createPlacesClient(context: Context): PlacesClient? {
    val apiKey = BuildConfig.MAPS_API_KEY
    if (apiKey.isBlank()) return null
    return try {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
        }
        Places.createClient(context)
    } catch (e: IllegalArgumentException) {
        // the API key was rejected
        Log.w(TAG, "Could not start Places search", e)
        null
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Could not start Places search", e)
        null
    }
}

suspend fun findSuggestions(
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
    } catch (e: ApiException) {
        Log.w(TAG, "Place search failed", e)
        emptyList()
    }
}

// name, address and coordinates of a place picked from the search suggestions
suspend fun fetchPlace(
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
    } catch (e: ApiException) {
        Log.w(TAG, "Could not load the place", e)
        null
    }
}

// waits for a Google Play services Task without blocking
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
