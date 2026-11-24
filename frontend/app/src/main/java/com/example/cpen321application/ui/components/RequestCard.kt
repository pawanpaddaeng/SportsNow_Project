package com.example.cpen321application.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.theme.SportsOutline
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary

// the outlined card a friend or join request sits in, its parts stacked 12dp apart
@Composable
fun RequestCardFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, SportsOutlineVariant, shape)
            .padding(16.dp),
        content = content
    )
}

/*  Decline and the accept button (acceptLabel, e.g. "Accept") side by side, both disabled
    while isBusy and accept also when canAccept is false. showProgress swaps the accept
    button's icon and label for a spinner while the answer is sent
 */
@Composable
fun DecisionButtons(
    acceptLabel: String,
    isBusy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    canAccept: Boolean = true,
    showProgress: Boolean = false
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onDecline,
            enabled = !isBusy,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, SportsOutline),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = SportsPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Decline", color = SportsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Button(
            onClick = onAccept,
            enabled = !isBusy && canAccept,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            if (showProgress) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = acceptLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
