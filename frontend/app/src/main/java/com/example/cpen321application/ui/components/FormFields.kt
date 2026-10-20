package com.example.cpen321application.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsOutline
import com.example.cpen321application.ui.theme.SportsPrimary
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.cpen321application.users.MAX_PROFILE_AGE
import com.example.cpen321application.users.MIN_PROFILE_AGE
import java.time.Year

// small label shown above a form field, as in the mockups
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = SportsOnSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

// outlined field colors used by every form
@Composable
fun sportsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SportsPrimary,
    unfocusedBorderColor = SportsOutline,
    cursorColor = SportsPrimary
)

// text field with its label above the box, a single line unless minLines is more than 1
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 1,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words
) {
    val singleLine = minLines == 1
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            placeholder = placeholder?.let { { Text(it) } },
            shape = RoundedCornerShape(8.dp),
            colors = sportsFieldColors(),
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/*  read only field with a dropdown arrow that calls onClick when tapped,
    used to open pickers (date, time, ...)
 */
@Composable
fun PickerField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        Box {
            OutlinedTextField(
                value = value ?: "",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                placeholder = { Text(placeholder) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                shape = RoundedCornerShape(8.dp),
                colors = sportsFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            // covers the field so a tap anywhere opens the picker
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onClick)
            )
        }
    }
}

@Composable
fun BirthYearField(
    birthYear: Int?,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentYear = Year.now().value
    val years = remember(currentYear) {
        (currentYear - MIN_PROFILE_AGE downTo currentYear - MAX_PROFILE_AGE).toList()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // the menu opens under the field it is anchored to
        Box {
            PickerField(
                label = "Birth year *",
                value = birthYear?.toString(),
                placeholder = "Select year",
                onClick = { expanded = true }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                years.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString()) },
                        onClick = {
                            onYearSelected(year)
                            expanded = false
                        }
                    )
                }
            }
        }
        Text(
            text = "Only your age range is shown to other players.",
            color = SportsOnSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
