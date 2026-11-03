package com.example.cpen321application.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.theme.SportsOutlineVariant

// what a confirmation dialog says: its question, what happens, and its two buttons
data class ConfirmText(
    val title: String,
    val body: String,
    val confirm: String,
    val dismiss: String,
    // red confirm button for actions that take something away
    val destructive: Boolean = true
)

// asks the user to confirm an action before it's sent
@Composable
fun ConfirmDialog(text: ConfirmText, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.title) },
        text = { Text(text.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                if (text.destructive) {
                    Text(text.confirm, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(text.confirm)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text.dismiss) }
        }
    )
}

/*  full width outlined pill for actions like leaving a game or signing out, red text
    unless another color is given. isBusy shows a spinner instead
 */
@Composable
fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    isBusy: Boolean = false,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.error
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isBusy,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, SportsOutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                color = color,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
