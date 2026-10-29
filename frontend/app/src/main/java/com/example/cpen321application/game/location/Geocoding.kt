package com.example.cpen321application.game.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.example.cpen321application.game.model.GameLocation
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

private const val TAG = "Geocoding"

/*  turns a point on the map into a place with a name and address using the
    phone's geocoder. Falls back to the coordinates if no address is found
 */
suspend fun describePoint(context: Context, point: LatLng): GameLocation {
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
    } catch (e: IOException) {
        // the geocoder service couldn't be reached
        Log.w(TAG, "Could not find an address for the point", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Could not find an address for the point", e)
        null
    }
}
