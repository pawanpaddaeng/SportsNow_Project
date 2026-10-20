package com.example.cpen321application.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.example.cpen321application.ui.theme.SportsSurfaceContainer

// small pieces shared by several screens

// "Full", "1 spot left" or "4 spots left"
fun spotsLeftLabel(spotsLeft: Int): String = when (spotsLeft) {
    0 -> "Full"
    1 -> "1 spot left"
    else -> "$spotsLeft spots left"
}

// full width rounded card filled with color
fun Modifier.cardBackground(color: Color): Modifier =
    fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(color)

// rounded pill filled with background, with a 1dp border when borderColor is given
fun Modifier.pillBackground(background: Color, borderColor: Color?, horizontalPadding: Dp = 8.dp): Modifier {
    val shape = RoundedCornerShape(50)
    return clip(shape)
        .background(background)
        .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
        .padding(horizontal = horizontalPadding, vertical = 4.dp)
}

// back button and the screen's title, trailing (e.g. an Edit button) goes on the right
@Composable
fun BackTitleBar(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

// a problem in grey, centred, with a retry button under it when onRetry is given
@Composable
fun RetryMessage(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier, retryLabel: String = "Retry") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text = message, color = SportsOnSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(50)) {
                Text(text = retryLabel, color = SportsPrimary)
            }
        }
    }
}

// grey rounded card with its rows 12dp apart, e.g. a list of LabelValueRow
@Composable
fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .cardBackground(SportsSurfaceContainer)
            .padding(16.dp),
        content = content
    )
}

// "Skill level   Intermediate" row of a details card, labelWidth lines the values up
@Composable
fun LabelValueRow(label: String, value: String, labelWidth: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, color = SportsOnSurfaceVariant, fontSize = 14.sp, modifier = Modifier.width(labelWidth))
        Text(
            text = value,
            color = SportsOnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

// row inside a card: items centred vertically and 12dp apart, with the card's inner padding
@Composable
fun CardRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}
