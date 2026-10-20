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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.network.GameDto
import com.example.cpen321application.ui.components.CardRow
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

// label shown on the chip and the value sent to the server, also the Discover filter
internal val SPORTS = listOf(
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

/*  everything entered on the Input Game Info screen. sport and skillLevel use the
    server's values (e.g. "basketball", "any"), startsAt is the start time in UTC.
    Serializable so the edit route can keep the loaded game with rememberSaveable
 */
data class GameInfoForm(
    val sport: String,
    val title: String,
    val startsAt: Instant,
    val location: GameLocation,
    val maxPlayers: Int,
    val skillLevel: String,
    val description: String?
) : Serializable

// a game from the server as the form's starting values, for editing it
fun GameDto.toGameInfoForm() = GameInfoForm(
    sport = sport,
    title = title,
    startsAt = Instant.parse(startsAt),
    location = GameLocation(locationName, address, latitude, longitude),
    maxPlayers = maxPlayers,
    skillLevel = skillLevel,
    description = description?.takeIf { it.isNotBlank() }
)

/*  what the Input Game Info screen shows. initial fills in the form (null for a new
    game) and minPlayers stops max players going below the players already in the game.
    isSubmitting is set while the caller sends the form. location is the place picked on
    the map. While the game to edit loads, isLoading shows a spinner and loadError a
    problem instead of the form
 */
data class InputGameInfoUiState(
    val screenTitle: String = "Create Game",
    val submitLabel: String = "Create Game",
    val initial: GameInfoForm? = null,
    val minPlayers: Int = MIN_PLAYERS,
    val isSubmitting: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val location: GameLocation? = null
)

/*  what the Input Game Info screen's controls do. onSubmit gets the filled in form,
    onPickLocation should open the map location picker and pass the result back
    through the state's location
 */
data class InputGameInfoActions(
    val onSubmit: (GameInfoForm) -> Unit,
    val onBack: () -> Unit,
    val onPickLocation: () -> Unit = {}
)

/*  the form's values while they're being edited. Serializable so rememberSaveable
    keeps them when the screen is recreated, e.g. after going to Pick Location
 */
private data class GameInfoDraft(
    val sport: String?,
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val maxPlayers: Int,
    val skillLevel: String,
    val description: String
) : Serializable {
    // start time in the phone's time zone, null until both a date and a time are picked
    val startsAt: Instant?
        get() = if (date != null && time != null) date.atTime(time).atZone(ZoneId.systemDefault()).toInstant() else null

    // the form as it would be sent, null while something required is missing or the start is in the past
    fun toForm(location: GameLocation?): GameInfoForm? {
        // null too when the start is in the past
        val start = startsAt?.takeUnless { it.isBefore(Instant.now()) }
        if (sport == null || title.isBlank() || start == null) {
            return null
        }
        return location?.let {
            GameInfoForm(
                sport = sport,
                title = title.trim(),
                startsAt = start,
                location = it,
                maxPlayers = maxPlayers,
                skillLevel = skillLevel,
                description = description.trim().ifBlank { null }
            )
        }
    }

    companion object {
        // starting values in the phone's time zone, empty for a new game
        fun from(initial: GameInfoForm?, lowestMaxPlayers: Int): GameInfoDraft {
            val start = initial?.startsAt?.atZone(ZoneId.systemDefault())
            return GameInfoDraft(
                sport = initial?.sport,
                title = initial?.title ?: "",
                date = start?.toLocalDate(),
                time = start?.toLocalTime(),
                maxPlayers = maxOf(initial?.maxPlayers ?: DEFAULT_PLAYERS, lowestMaxPlayers),
                skillLevel = initial?.skillLevel ?: "any",
                description = initial?.description ?: ""
            )
        }
    }
}

/*
    Input Game Info screen from the Create Game Figma mockup, used to create and to edit
    a game: the create and edit routes in AppNavigation both show it. Every game needs
    the organizer's approval to join, so there is no approval toggle. When editing, the
    button is only enabled once something changed
 */
@Composable
fun InputGameInfoScreen(state: InputGameInfoUiState, actions: InputGameInfoActions, modifier: Modifier = Modifier) {
    if (state.isLoading || state.loadError != null) {
        LoadingGame(title = state.screenTitle, loadError = state.loadError, onBack = actions.onBack, modifier = modifier)
        return
    }

    val lowestMaxPlayers = maxOf(MIN_PLAYERS, state.minPlayers)
    var draft by rememberSaveable { mutableStateOf(GameInfoDraft.from(state.initial, lowestMaxPlayers)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val startsInPast = draft.startsAt?.isBefore(Instant.now()) == true
    val form = draft.toForm(state.location)
    val canSubmit = form != null && form != state.initial && !state.isSubmitting

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TopBar(title = state.screenTitle, onBack = actions.onBack)
        BasicsFields(
            draft = draft,
            onChange = { draft = it },
            startsInPast = startsInPast,
            onPickDate = { showDatePicker = true },
            onPickTime = { showTimePicker = true }
        )
        LocationField(location = state.location, onClick = actions.onPickLocation)
        SettingsFields(draft = draft, onChange = { draft = it }, lowestMaxPlayers = lowestMaxPlayers)
        SubmitButton(
            label = state.submitLabel,
            enabled = canSubmit,
            isSubmitting = state.isSubmitting,
            onClick = { form?.let(actions.onSubmit) }
        )
    }

    if (showDatePicker) {
        GameDatePickerDialog(draft.date, onDateSelected = { draft = draft.copy(date = it) }, onDismiss = { showDatePicker = false })
    }
    if (showTimePicker) {
        GameTimePickerDialog(draft.time, onTimeSelected = { draft = draft.copy(time = it) }, onDismiss = { showTimePicker = false })
    }
}

// the screen's title and a spinner, or loadError, while the game to edit loads
@Composable
private fun LoadingGame(title: String, loadError: String?, onBack: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 16.dp)
    ) {
        TopBar(title = title, onBack = onBack)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp)
        ) {
            if (loadError != null) {
                Text(text = loadError, color = SportsOnSurfaceVariant, fontSize = 14.sp)
            } else {
                CircularProgressIndicator(color = SportsPrimary)
            }
        }
    }
}

// sport, title, and the date and time fields that open their pickers
@Composable
private fun BasicsFields(
    draft: GameInfoDraft,
    onChange: (GameInfoDraft) -> Unit,
    startsInPast: Boolean,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit
) {
    ChipGroup(
        label = "Sport *",
        options = SPORTS,
        selected = draft.sport,
        onSelect = { onChange(draft.copy(sport = it)) }
    )

    LabeledTextField(
        label = "Title *",
        value = draft.title,
        onValueChange = { if (it.length <= MAX_TITLE_LENGTH) onChange(draft.copy(title = it)) },
        placeholder = "e.g. Pickup Basketball",
        capitalization = KeyboardCapitalization.Sentences
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerField(
                label = "Date *",
                value = draft.date?.format(dateFormat),
                placeholder = "Select date",
                onClick = onPickDate,
                modifier = Modifier.weight(1f)
            )
            PickerField(
                label = "Time *",
                value = draft.time?.format(timeFormat),
                placeholder = "Select time",
                onClick = onPickTime,
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
}

// max players, skill level, the approval note and the description
@Composable
private fun SettingsFields(draft: GameInfoDraft, onChange: (GameInfoDraft) -> Unit, lowestMaxPlayers: Int) {
    MaxPlayersStepper(
        value = draft.maxPlayers,
        min = lowestMaxPlayers,
        onValueChange = { onChange(draft.copy(maxPlayers = it)) }
    )

    ChipGroup(
        label = "Skill level",
        options = SKILL_LEVELS,
        selected = draft.skillLevel,
        onSelect = { onChange(draft.copy(skillLevel = it)) }
    )

    ApprovalNote()

    LabeledTextField(
        label = "Description (optional)",
        value = draft.description,
        onValueChange = { if (it.length <= MAX_DESCRIPTION_LENGTH) onChange(draft.copy(description = it)) },
        placeholder = "Add details for players…",
        minLines = 3,
        capitalization = KeyboardCapitalization.Sentences
    )
}

// replaces the mockup's approval toggle: approval is always required
@Composable
private fun ApprovalNote() {
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
}

// "Create Game" / "Save Changes" button, a spinner while the form is being sent
@Composable
private fun SubmitButton(label: String, enabled: Boolean, isSubmitting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
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
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// back button and the screen's title
@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
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
            text = title,
            color = SportsOnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// row of selectable chips with a label above, at most one option is selected
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipGroup(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (text, value) ->
                SelectableChip(text = text, selected = value == selected, onClick = { onSelect(value) })
            }
        }
    }
}

// chip from the mockups, a check mark and blue fill when selected
@Composable
internal fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (selected) {
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
            selected = selected,
            borderColor = SportsOutlineVariant,
            selectedBorderColor = SportsPrimaryContainer
        )
    )
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
            CardRow {
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

/*  small non-interactive map with a marker on the place. Lite mode draws it as a
    still image, which is cheaper inside a scrolling screen. Also used by Game Details
 */
@Composable
internal fun LocationPreviewMap(location: GameLocation, onClick: () -> Unit, height: Dp = 120.dp) {
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

/*  "Max players  (−) 10 (+)" row. min is the lowest allowed value, when editing
    it's the number of players already in the game
 */
@Composable
private fun MaxPlayersStepper(value: Int, min: Int, onValueChange: (Int) -> Unit) {
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
            enabled = value > min,
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
