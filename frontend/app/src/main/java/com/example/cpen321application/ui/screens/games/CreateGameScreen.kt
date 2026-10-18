package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.components.FieldLabel
import com.example.cpen321application.ui.components.LabeledTextField
import com.example.cpen321application.ui.components.PickerField
import com.example.cpen321application.ui.theme.*
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// limits matching the games table in the database
private const val MIN_PLAYERS = 2
private const val MAX_PLAYERS = 100
private const val DEFAULT_PLAYERS = 10
private const val MAX_TITLE_LENGTH = 100
private const val MAX_DESCRIPTION_LENGTH = 1000

// label shown on the chip and the value sent to the server
private val SPORTS = listOf(
    "Basketball" to "basketball",
    "Soccer" to "soccer",
    "Volleyball" to "volleyball",
    "Tennis" to "tennis",
    "Other" to "other"
)
private val SKILL_LEVELS = listOf(
    "Any" to "any",
    "Beginner" to "beginner",
    "Intermediate" to "intermediate",
    "Advanced" to "advanced"
)

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

// place picked for the game on the Pick Location screen. Serializable so it can be
// passed back between screens through the navigation SavedStateHandle
data class GameLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
) : Serializable

/*  everything entered on the Create Game screen. sport and skillLevel use the
    server's values (e.g. "basketball", "any"), startsAt is the start time in UTC
 */
data class NewGameForm(
    val sport: String,
    val title: String,
    val startsAt: Instant,
    val location: GameLocation,
    val maxPlayers: Int,
    val skillLevel: String,
    val description: String?
)

/*
    Create Game screen from the Figma mockup. Every game needs the organizer's
    approval to join, so there is no approval toggle.
    onCreate gets the filled in form, the caller sends it to the server and sets
    isSubmitting while it does. onPickLocation should open the map location
    picker and pass the result back through location
 */
@Composable
fun CreateGameScreen(
    onCreate: (NewGameForm) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isSubmitting: Boolean = false,
    location: GameLocation? = null,
    onPickLocation: () -> Unit = {}
) {
    var sport by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var time by rememberSaveable { mutableStateOf<LocalTime?>(null) }
    var maxPlayers by rememberSaveable { mutableIntStateOf(DEFAULT_PLAYERS) }
    var skillLevel by rememberSaveable { mutableStateOf("any") }
    var description by rememberSaveable { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // start time in the phone's time zone, it must be in the future
    val startsAt = if (date != null && time != null) {
        date!!.atTime(time!!).atZone(ZoneId.systemDefault()).toInstant()
    } else {
        null
    }
    val startsInPast = startsAt != null && startsAt.isBefore(Instant.now())
    val canCreate = sport != null && title.isNotBlank() && startsAt != null &&
            !startsInPast && location != null && !isSubmitting

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // top bar with back button and title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SportsOnSurface
                )
            }
            Text(
                text = "Create Game",
                color = SportsOnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        ChipGroup(
            label = "Sport *",
            options = SPORTS,
            selected = sport,
            onSelect = { sport = it }
        )

        LabeledTextField(
            label = "Title *",
            value = title,
            onValueChange = { if (it.length <= MAX_TITLE_LENGTH) title = it },
            placeholder = "e.g. Pickup Basketball",
            capitalization = KeyboardCapitalization.Sentences
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = "Date *",
                    value = date?.format(dateFormat),
                    placeholder = "Select date",
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                )
                PickerField(
                    label = "Time *",
                    value = time?.format(timeFormat),
                    placeholder = "Select time",
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                )
            }
            if (startsInPast) {
                Text(
                    text = "The game has to start in the future.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        LocationField(location = location, onClick = onPickLocation)

        MaxPlayersStepper(
            value = maxPlayers,
            onValueChange = { maxPlayers = it }
        )

        ChipGroup(
            label = "Skill level",
            options = SKILL_LEVELS,
            selected = skillLevel,
            onSelect = { skillLevel = it }
        )

        // replaces the mockup's approval toggle: approval is always required
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SportsSurfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = SportsPrimary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Players send a join request and need your approval before they join.",
                color = SportsOnSurfaceVariant,
                fontSize = 13.sp
            )
        }

        LabeledTextField(
            label = "Description (optional)",
            value = description,
            onValueChange = { if (it.length <= MAX_DESCRIPTION_LENGTH) description = it },
            placeholder = "Add details for players…",
            singleLine = false,
            minLines = 3,
            capitalization = KeyboardCapitalization.Sentences
        )

        Button(
            onClick = {
                onCreate(
                    NewGameForm(
                        sport = sport!!,
                        title = title.trim(),
                        startsAt = startsAt!!,
                        location = location!!,
                        maxPlayers = maxPlayers,
                        skillLevel = skillLevel,
                        description = description.trim().ifBlank { null }
                    )
                )
            },
            enabled = canCreate,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .height(48.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(text = "Create Game", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showDatePicker) {
        GameDatePickerDialog(
            initialDate = date,
            onDateSelected = { date = it },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        GameTimePickerDialog(
            initialTime = time,
            onTimeSelected = { time = it },
            onDismiss = { showTimePicker = false }
        )
    }
}

// row of selectable chips with a label above, exactly one option is selected
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (text, value) ->
                val isSelected = value == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SportsPrimaryContainer,
                        selectedLabelColor = SportsOnPrimaryContainer,
                        selectedLeadingIconColor = SportsOnPrimaryContainer,
                        labelColor = SportsOnSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = SportsOutlineVariant,
                        selectedBorderColor = SportsPrimaryContainer
                    )
                )
            }
        }
    }
}

/*  shows the picked place with a small map preview, tapping it opens the
    Pick Location screen
 */
@Composable
private fun LocationField(location: GameLocation?, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel("Location *")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, SportsOutline, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
        ) {
            if (location != null) {
                LocationPreviewMap(location = location, onClick = onClick)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = Color(0xFFD93025)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location?.name ?: "Choose a location",
                        color = if (location != null) SportsOnSurface else SportsOnSurfaceVariant,
                        fontSize = 16.sp
                    )
                    Text(
                        text = location?.address ?: "Pick the place on a map",
                        color = SportsOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = if (location != null) "Change" else "Choose",
                    color = SportsPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/*  small non-interactive map with a marker on the picked place. Lite mode draws
    it as a still image, which is cheaper inside a scrolling form
 */
@Composable
private fun LocationPreviewMap(location: GameLocation, onClick: () -> Unit) {
    val point = LatLng(location.latitude, location.longitude)
    val key = "${location.latitude},${location.longitude}"
    val cameraState = rememberCameraPositionState(key = key) {
        position = CameraPosition.fromLatLngZoom(point, 16f)
    }
    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
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

// "Max players  (−) 10 (+)" row
@Composable
private fun MaxPlayersStepper(value: Int, onValueChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Max players",
            color = SportsOnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        OutlinedIconButton(
            onClick = { onValueChange(value - 1) },
            enabled = value > MIN_PLAYERS,
            modifier = Modifier.size(36.dp)
        ) {
            // the core icon set has no minus icon
            Text(text = "−", fontSize = 20.sp, color = SportsOnSurface)
        }
        Text(
            text = value.toString(),
            color = SportsOnSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(min = 28.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        FilledIconButton(
            onClick = { onValueChange(value + 1) },
            enabled = value < MAX_PLAYERS,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = SportsPrimary),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "More players", tint = Color.White)
        }
    }
}

// calendar dialog that only allows today or later
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDatePickerDialog(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // the date picker works with UTC midnight in milliseconds
    val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}

// clock dialog for the start time
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTimePickerDialog(
    initialTime: LocalTime?,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialTime?.hour ?: 18,
        initialMinute = initialTime?.minute ?: 0,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}
