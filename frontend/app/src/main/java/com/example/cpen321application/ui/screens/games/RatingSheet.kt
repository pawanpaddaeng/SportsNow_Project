package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.review.model.PlayerToRate
import com.example.cpen321application.review.model.RatingCategory
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary

private const val MAX_STARS = 5

/*  the rating form, opened from a player on Rate Players. Every category is needed, Submit
    says which are missing. isSending shows progress on Submit while the rating is sent,
    errorMessage shows why it failed (the stars stay so the user can try again)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RatingSheet(
    player: PlayerToRate,
    onDismiss: () -> Unit,
    onSubmit: (Map<RatingCategory, Int>) -> Unit,
    isSending: Boolean = false,
    errorMessage: String? = null
) {
    var scores by remember { mutableStateOf(emptyMap<RatingCategory, Int>()) }
    var missing by remember { mutableStateOf(emptyList<RatingCategory>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isSending) onDismiss() },
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Text(text = "Rate ${player.name}", color = SportsOnSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

            RatingCategory.entries.forEach { category ->
                StarsRow(
                    category = category,
                    stars = scores[category] ?: 0,
                    enabled = !isSending,
                    onStars = { stars ->
                        scores = scores + (category to stars)
                        missing = missing - category
                    }
                )
            }

            val problem = if (missing.isNotEmpty()) {
                "Please rate: ${missing.joinToString(", ") { it.label }}"
            } else {
                errorMessage
            }
            if (problem != null) {
                Text(text = problem, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    missing = RatingCategory.entries.filter { it !in scores }
                    if (missing.isEmpty()) onSubmit(scores)
                },
                enabled = !isSending,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(text = "Submit", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// a category's name with its five stars, tapping a star gives that many
@Composable
private fun StarsRow(category: RatingCategory, stars: Int, enabled: Boolean, onStars: (Int) -> Unit) {
    Column {
        Text(text = category.label, color = SportsOnSurfaceVariant, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            (1..MAX_STARS).forEach { value ->
                IconButton(onClick = { onStars(value) }, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "${category.label} $value of $MAX_STARS",
                        tint = if (value <= stars) SportsPrimary else SportsOutlineVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
