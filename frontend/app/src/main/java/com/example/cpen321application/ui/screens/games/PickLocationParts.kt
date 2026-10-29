package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings

// the Pick Location screen's map, pin, search panel and bottom sheet

// map space covered by the search bar and the bottom sheet, the map's centre is
// moved into the visible area between them so the pin marks the real centre
private val MAP_TOP_PADDING = 88.dp
private val MAP_BOTTOM_PADDING = 240.dp
private val PIN_SIZE = 48.dp

@Composable
internal fun PickerMap(cameraState: CameraPositionState, showMyLocation: Boolean) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        contentPadding = PaddingValues(top = MAP_TOP_PADDING, bottom = MAP_BOTTOM_PADDING),
        properties = MapProperties(isMyLocationEnabled = showMyLocation),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = showMyLocation,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false
        )
    )
}

// fixed pin over the map's centre, its tip marks the chosen point
@Composable
internal fun CenterPin(modifier: Modifier) {
    Icon(
        imageVector = Icons.Filled.Place,
        contentDescription = null,
        tint = Color(0xFFD93025),
        modifier = modifier
            .offset(y = (MAP_TOP_PADDING - MAP_BOTTOM_PADDING) / 2 - PIN_SIZE / 2)
            .size(PIN_SIZE)
    )
}

// search bar with the back button, and the suggestions under it while there are any
@Composable
internal fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    searchEnabled: Boolean,
    suggestions: List<AutocompletePrediction>,
    onPick: (AutocompletePrediction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        SearchBar(query = query, onQueryChange = onQueryChange, enabled = searchEnabled, onBack = onBack)

        if (suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    suggestions.forEach { prediction ->
                        SuggestionRow(prediction = prediction, onClick = { onPick(prediction) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, enabled: Boolean, onBack: () -> Unit) {
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
                onValueChange = onQueryChange,
                enabled = enabled,
                singleLine = true,
                placeholder = {
                    Text(if (enabled) "Search for a place" else "Search unavailable")
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

// bottom sheet with the selected place and Confirm Location
@Composable
internal fun SelectedPlaceSheet(selected: GameLocation?, isBusy: Boolean, onConfirm: () -> Unit, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
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
                onClick = onConfirm,
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
