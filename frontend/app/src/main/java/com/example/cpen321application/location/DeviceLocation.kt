package com.example.cpen321application.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// a fix older than this is too stale for "games near you", a new one is requested instead
private const val MAX_LAST_FIX_AGE_MS = 2 * 60 * 1000L

// how long to wait for a new fix before giving up
private const val CURRENT_FIX_TIMEOUT_MS = 10_000L

// latitude and longitude of the phone, in degrees
data class DeviceLocation(val latitude: Double, val longitude: Double)

// whether the user allowed the app to use their precise or approximate location
fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

// whether location is turned on in the phone's settings
fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return LocationManagerCompat.isLocationEnabled(manager)
}

/*  the phone's location, asked for once (not tracked) to save battery. Uses a recent
    last known fix when there is one, otherwise waits up to 10 seconds for a new one,
    and falls back to an older last known fix if no new one arrives (a phone that hasn't
    moved, or an emulator, may not send one). Returns null without permission, with
    location turned off, or when the phone has never had a fix
 */
suspend fun currentDeviceLocation(context: Context): DeviceLocation? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    if (!LocationManagerCompat.isLocationEnabled(manager)) return null

    val providers = usableProviders(manager)
    if (providers.isEmpty()) return null

    return try {
        val lastKnown = providers
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
        val recent = lastKnown?.takeIf { System.currentTimeMillis() - it.time <= MAX_LAST_FIX_AGE_MS }
        val location = recent ?: withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MS) {
            requestCurrentLocation(context, manager, providers.first())
        } ?: lastKnown
        location?.let { DeviceLocation(it.latitude, it.longitude) }
    } catch (e: SecurityException) {
        // permission was taken away while the app was running
        null
    }
}

// enabled providers, best first: fused (Android 12+), then network, then GPS
private fun usableProviders(manager: LocationManager): List<String> {
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }
    val enabled = manager.getProviders(true)
    return candidates.filter { it in enabled }
}

// one new location fix from provider, null if the provider can't get one
private suspend fun requestCurrentLocation(
    context: Context,
    manager: LocationManager,
    provider: String
): Location? = suspendCancellableCoroutine { continuation ->
    if (!hasLocationPermission(context)) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val cancellation = CancellationSignal()
    continuation.invokeOnCancellation { cancellation.cancel() }
    LocationManagerCompat.getCurrentLocation(
        manager,
        provider,
        cancellation,
        ContextCompat.getMainExecutor(context)
    ) { location ->
        if (continuation.isActive) continuation.resume(location)
    }
}
