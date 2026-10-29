package com.example.cpen321application.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cpen321application.game.model.GameLocation
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

/*  small non-interactive map with a marker on the place. Lite mode draws it as a
    still image, which is cheaper inside a scrolling screen
 */
@Composable
fun LocationPreviewMap(location: GameLocation, onClick: () -> Unit, height: Dp = 120.dp) {
    val point = LatLng(location.latitude, location.longitude)
    val key = "${location.latitude},${location.longitude}"
    val cameraState = rememberCameraPositionState(key = key) {
        position = CameraPosition.fromLatLngZoom(point, 16f)
    }
    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        cameraPositionState = cameraState,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false
        ),
        onMapClick = { onClick() }
    ) {
        Marker(state = rememberMarkerState(key = key, position = point))
    }
}
