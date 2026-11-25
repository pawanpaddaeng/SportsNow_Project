package com.example.cpen321application.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.review.model.MAX_STARS
import com.example.cpen321application.review.model.formatReputationScore
import com.example.cpen321application.ui.components.SurfaceCard
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary

// size of each star under the reputation score
private val SCORE_STAR_SIZE = 32.dp

// the signed in user's current reputation score, as the number and as stars, or that they aren't rated yet
@Composable
internal fun ReputationCard(score: Double?) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Your rating",
                color = SportsOnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            ScoreText(score)
        }
        score?.let { ScoreStars(it) }
    }
}

// the score as "4.7/6", or "Not rated yet" in smaller, quieter text
@Composable
private fun ScoreText(score: Double?) {
    val (color, fontSize) = if (score == null) SportsOnSurfaceVariant to 16.sp else SportsPrimary to 22.sp
    Text(text = formatReputationScore(score), color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
}

/*  the score as MAX_STARS stars lit from left to right, e.g. 3.2 lights three stars fully
    and the left fifth of the fourth
 */
@Composable
private fun ScoreStars(score: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Rating ${formatReputationScore(score)} stars"
        }
    ) {
        for (star in 0 until MAX_STARS) {
            val lit = (score - star).coerceIn(0.0, 1.0).toFloat()
            Box {
                Icon(Icons.Filled.Star, null, tint = SportsOutlineVariant, modifier = Modifier.size(SCORE_STAR_SIZE))
                Icon(
                    Icons.Filled.Star,
                    null,
                    tint = SportsPrimary,
                    modifier = Modifier
                        .size(SCORE_STAR_SIZE)
                        .drawWithContent { clipRect(right = size.width * lit) { this@drawWithContent.drawContent() } }
                )
            }
        }
    }
}
