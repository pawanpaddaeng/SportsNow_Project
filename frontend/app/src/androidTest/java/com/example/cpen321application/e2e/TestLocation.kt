package com.example.cpen321application.e2e

import android.Manifest
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

// latitude and longitude of a fake phone location, in degrees
data class TestPoint(val latitude: Double, val longitude: Double)

/*  the fake phone locations the game discovery E2E tests use. They must match DISCOVER_SPOT
    and GAMES_SPOT in backend/tests/e2e/seedGameDiscovery.ts, which saves the test games
    around them, in open ocean so no real game is within Discover's 50 km
 */
val DISCOVER_SPOT = TestPoint(latitude = 45.0, longitude = -135.0)
val GAMES_SPOT = TestPoint(latitude = 46.0, longitude = -135.0)

// about 390 km from both spots, so no test game is near it
val NO_GAMES_SPOT = TestPoint(latitude = 45.0, longitude = -130.0)

/*  sets the phone's location in the E2E tests. The app reads the location from Android's
    LocationManager, so each of its providers is replaced by a test provider that reports
    the location set here. start() before the test, stop() after it.
    Also gives the app location permission, so Android's permission popup never shows.
 */
class TestLocation(private val device: UiDevice) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(LocationManager::class.java)
    private val packageName = context.packageName

    // the providers the app reads, in the order DeviceLocation.kt tries them
    private val providers = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }

    fun start() {
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        // only the app chosen as the mock location app may add test providers
        device.executeShellCommand("appops set $packageName android:mock_location allow")
        setLocationEnabled(true)
        providers.forEach { provider ->
            addTestProvider(provider)
            manager.setTestProviderEnabled(provider, true)
        }
    }

    // the phone is now at point. The fix is new, so the app uses it right away
    fun moveTo(point: TestPoint) {
        providers.forEach { provider ->
            val location = Location(provider).apply {
                latitude = point.latitude
                longitude = point.longitude
                accuracy = 1f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            manager.setTestProviderLocation(provider, location)
        }
    }

    // turns location on or off in the phone's settings, like the quick settings toggle
    fun setLocationEnabled(enabled: Boolean) {
        device.executeShellCommand("cmd location set-location-enabled $enabled")
    }

    // puts the real providers back and turns location back on
    fun stop() {
        providers.forEach { provider -> manager.removeTestProvider(provider) }
        setLocationEnabled(true)
    }

    // a provider that needs no network or satellites and reports precise locations
    private fun addTestProvider(provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val properties = ProviderProperties.Builder()
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build()
            manager.addTestProvider(provider, properties)
        } else {
            manager.addTestProvider(
                provider, false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
        }
    }
}
