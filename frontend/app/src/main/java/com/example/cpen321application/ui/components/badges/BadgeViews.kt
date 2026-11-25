package com.example.cpen321application.ui.components.badges

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cpen321application.review.model.Badge
import com.example.cpen321application.ui.components.pillBackground
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import kotlinx.coroutines.delay

private const val BADGES_PER_ROW = 3
// a badge takes this much of its cell's width, leaving room between badges
private const val BADGE_WIDTH_IN_CELL = 0.82f
private const val MILLIS_PER_SECOND = 1000
private const val POPUP_FADE_MS = 300
private val PopupBadgeWidth = 200.dp
private val PopupTagBackground = Color.White.copy(alpha = 0.16f)
// darkens the screen behind the popup on top of the dialog's own dimming, so the white text reads
private val PopupScrim = Color.Black.copy(alpha = 0.45f)

// a badge sitting still: in colour when earned, grey and faded when not
@Composable
fun BadgeImage(badge: Badge, earned: Boolean, modifier: Modifier = Modifier) {
    BadgeDrawing(badge = badge, earned = earned, frame = BadgeFrame.Rest, modifier = modifier)
}

// a badge playing its unlock animation once, from the start, ending on the badge at rest
@Composable
fun AnimatedBadge(badge: Badge, modifier: Modifier = Modifier) {
    val seconds = remember(badge) { Animatable(0f) }
    LaunchedEffect(badge) {
        seconds.snapTo(0f)
        val length = animationSeconds(badge)
        seconds.animateTo(length, tween(durationMillis = (length * MILLIS_PER_SECOND).toInt(), easing = LinearEasing))
    }
    BadgeDrawing(badge = badge, earned = true, frame = badgeFrameAt(badge, seconds.value), modifier = modifier)
}

/*  the badges three to a row, each with its name underneath. Badges in earned are in colour,
    the rest grey. When onOpen is given, tapping an earned badge calls it. showHints adds how
    to earn each badge under its name, on the user's own profile
 */
@Composable
fun BadgeGrid(
    badges: List<Badge>,
    earned: Set<Badge>,
    modifier: Modifier = Modifier,
    onOpen: ((Badge) -> Unit)? = null,
    showHints: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        badges.chunked(BADGES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { badge ->
                    BadgeCell(
                        badge = badge,
                        earned = badge in earned,
                        onOpen = onOpen,
                        hint = badge.hint.takeIf { showHints },
                        modifier = Modifier.weight(1f)
                    )
                }
                // keeps the last row's badges the same size as the others
                repeat(BADGES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// how a badge's name reads under it, and what a screen reader says about it
private class CellLook(val nameColor: Color, val nameWeight: FontWeight, val status: String)

private val EarnedLook = CellLook(SportsOnSurface, FontWeight.Medium, status = "earned")
private val LockedLook = CellLook(SportsOnSurfaceVariant, FontWeight.Normal, status = "not earned yet")

@Composable
private fun BadgeCell(badge: Badge, earned: Boolean, onOpen: ((Badge) -> Unit)?, hint: String?, modifier: Modifier) {
    val look = if (earned) EarnedLook else LockedLook
    val canOpen = earned && onOpen != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (canOpen) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Show badge") { onOpen?.invoke(badge) }
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull("${badge.title} badge, ${look.status}", hint).joinToString(". ")
            }
            .padding(vertical = 4.dp)
    ) {
        BadgeImage(badge = badge, earned = earned, modifier = Modifier.fillMaxWidth(BADGE_WIDTH_IN_CELL))
        Text(
            text = badge.title,
            color = look.nameColor,
            fontSize = 12.sp,
            fontWeight = look.nameWeight,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        // how to earn it, in a few words, when the grid shows hints
        hint?.let {
            Text(
                text = it,
                color = SportsOnSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/*  the popup for an earned badge: the screen dims, the badge plays its animation with its name
    and goal underneath, then the popup fades and closes by itself. Tapping anywhere closes it sooner
 */
@Composable
fun BadgePopup(badge: Badge, onDismiss: () -> Unit) {
    val fade = remember(badge) { Animatable(1f) }
    LaunchedEffect(badge) {
        delay(popupMillis(badge))
        fade.animateTo(0f, tween(durationMillis = POPUP_FADE_MS))
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .alpha(fade.value)
                .background(PopupScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(24.dp)
        ) {
            AnimatedBadge(badge = badge, modifier = Modifier.width(PopupBadgeWidth))
            Text(
                text = badge.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(text = badge.goal, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
            Text(
                text = "EARNED",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.pillBackground(PopupTagBackground, borderColor = null, horizontalPadding = 10.dp)
            )
        }
    }
}
