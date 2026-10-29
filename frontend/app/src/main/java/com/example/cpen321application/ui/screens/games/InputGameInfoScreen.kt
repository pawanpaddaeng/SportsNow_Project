package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.SKILL_LEVELS
import com.example.cpen321application.game.SPORTS
import com.example.cpen321application.game.model.GameInfoForm
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.ui.components.ChipGroup
import com.example.cpen321application.ui.components.LabeledTextField
import com.example.cpen321application.ui.components.PickerField
import com.example.cpen321application.ui.theme.*
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// limits matching the games table in the database
private const val MIN_PLAYERS = 2
private const val MAX_PLAYERS = 100
private const val DEFAULT_PLAYERS = 10
private const val MAX_TITLE_LENGTH = 100
private const val MAX_DESCRIPTION_LENGTH = 1000

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

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

/*  onSubmit gets the filled in form, onPickLocation should open the map location
    picker and pass the result back through the state's location
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
    a game. Every game needs the organizer's approval to join, so there is no approval
    toggle. When editing, the button is only enabled once something changed
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
        max = MAX_PLAYERS,
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
