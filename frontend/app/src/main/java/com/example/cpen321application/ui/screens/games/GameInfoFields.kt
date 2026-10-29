package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.ui.components.CardRow
import com.example.cpen321application.ui.components.FieldLabel
import com.example.cpen321application.ui.components.LocationPreviewMap
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsOutline
import com.example.cpen321application.ui.theme.SportsPrimary
import com.example.cpen321application.ui.theme.SportsSurfaceContainer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// the Input Game Info screen's larger fields and its date and time dialogs

/*  shows the picked place with a small map preview, tapping it opens the
    Pick Location screen
 */
@Composable
internal fun LocationField(location: GameLocation?, onClick: () -> Unit) {
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

// replaces the mockup's approval toggle: approval is always required
@Composable
internal fun ApprovalNote() {
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

/*  "Max players  (−) 10 (+)" row. min is the lowest allowed value, when editing
    it's the number of players already in the game
 */
@Composable
internal fun MaxPlayersStepper(value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
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
            textAlign = TextAlign.Center
        )
        FilledIconButton(
            onClick = { onValueChange(value + 1) },
            enabled = value < max,
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
internal fun GameDatePickerDialog(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameTimePickerDialog(
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
