package com.example.cpen321application.ui.components.badges

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.cpen321application.review.model.Badge
import com.example.cpen321application.review.model.BadgeTier

/*  What each badge is made of, drawn by BadgeCanvas. A badge is a stack of layers, back to
    front. Each layer is one part the unlock animation can move on its own (the frame, the
    icon, a laurel leaf...), so the animations can build a badge up piece by piece
 */

// the colours of one badge: its fill fades from light at the top to dark at the bottom
data class BadgePalette(val light: Color, val dark: Color, val ring: Color)

// how a shape is filled
sealed interface BadgeFill {
    data class Solid(val color: Color) : BadgeFill
    // the badge's light-to-dark fill
    data object Body : BadgeFill
    data object Gold : BadgeFill
}

/*  one shape: filled, or outlined when strokeWidth is set. Outlines are always solid colours
    with round ends
 */
data class BadgeShape(
    val path: Path,
    val fill: BadgeFill,
    val strokeWidth: Float? = null,
    val alpha: Float = 1f
)

// text in the badge, centred on x with its baseline at baseline, size in grid units
data class BadgeLabel(val text: String, val x: Float, val baseline: Float, val size: Float, val color: Color)

// the part of a badge a layer is, which says how the unlock animation moves it
enum class BadgePart { Plain, Ribbon, Frame, Inner, Ring, Core, Drop, Banner, Leaf, Crown, Gloss }

/*  inIcon layers are part of the badge's icon, drawn at the tier's icon size and moved with
    the whole icon. index tells laurel leaves apart, so they pop on one after another
 */
data class BadgeLayer(
    val part: BadgePart,
    val shapes: List<BadgeShape>,
    val inIcon: Boolean = false,
    val labels: List<BadgeLabel> = emptyList(),
    val index: Int = 0
)

/*  everything needed to draw a badge. clip is the badge's outline, which the shine and the
    gloss stay inside. outline is drawn in as the first step of building a milestone shield
 */
data class BadgeArt(
    val tier: BadgeTier,
    val palette: BadgePalette,
    val layers: List<BadgeLayer>,
    val clip: Path,
    val iconScale: Float,
    val outline: Path? = null
)

private const val SHIELD = "M60 4 L104 18 V54 C104 84 86 102 60 112 C34 102 16 84 16 54 V18 Z"
private const val INNER_SCALE = 0.85f
private const val ROSETTE_POINTS = 14
private const val MILESTONE_ICON_SCALE = 0.95f
private const val REPUTATION_ICON_SCALE = 0.8f
private const val GLOSS_ALPHA = 0.14f
private const val RIM_ALPHA = 0.35f

val White = Color.White

// the art for a badge, built once per badge
fun badgeArt(badge: Badge): BadgeArt {
    val palette = paletteOf(badge)
    val icon = iconLayers(badge, palette)
    return when (badge.tier) {
        BadgeTier.GettingStarted -> BadgeArt(
            tier = badge.tier,
            palette = palette,
            layers = medalLayers(palette) + icon + glossLayer(),
            clip = circlePath(cx = CENTER_X, cy = CENTER_Y, r = 54f),
            iconScale = 1f
        )
        BadgeTier.Milestone -> BadgeArt(
            tier = badge.tier,
            palette = palette,
            layers = shieldLayers(palette) + icon + glossLayer(),
            clip = svgPath(SHIELD),
            iconScale = MILESTONE_ICON_SCALE,
            outline = svgPath(SHIELD)
        )
        BadgeTier.Reputation -> BadgeArt(
            tier = badge.tier,
            palette = palette,
            layers = rosetteLayers(palette) + icon + glossLayer(),
            clip = rosettePath(),
            iconScale = REPUTATION_ICON_SCALE
        )
    }
}

private fun rosettePath(): Path =
    starPath(cx = CENTER_X, cy = CENTER_Y, outer = 57f, inner = 50f, points = ROSETTE_POINTS)

// a soft highlight across the top, kept inside the badge's outline
private fun glossLayer() = BadgeLayer(
    part = BadgePart.Gloss,
    shapes = listOf(
        BadgeShape(
            ellipsePath(cx = CENTER_X, cy = 26f, rx = 34f, ry = 12f),
            BadgeFill.Solid(White),
            alpha = GLOSS_ALPHA
        )
    )
)

// the thin light line just inside a badge's edge
private fun rim(path: Path, width: Float) =
    BadgeShape(path, BadgeFill.Solid(White), strokeWidth = width, alpha = RIM_ALPHA)

// getting started: a round medal
private fun medalLayers(palette: BadgePalette) = listOf(
    BadgeLayer(
        part = BadgePart.Plain,
        shapes = listOf(
            BadgeShape(circlePath(cx = CENTER_X, cy = CENTER_Y, r = 54f), BadgeFill.Solid(palette.ring)),
            BadgeShape(circlePath(cx = CENTER_X, cy = CENTER_Y, r = 46f), BadgeFill.Body),
            rim(circlePath(cx = CENTER_X, cy = CENTER_Y, r = 46f), width = 2f)
        )
    )
)

// milestones: a shield, its rim then its coloured inside
private fun shieldLayers(palette: BadgePalette) = listOf(
    BadgeLayer(part = BadgePart.Frame, shapes = listOf(BadgeShape(svgPath(SHIELD), BadgeFill.Solid(palette.ring)))),
    BadgeLayer(
        part = BadgePart.Inner,
        shapes = listOf(
            BadgeShape(svgPath(SHIELD).scaled(INNER_SCALE), BadgeFill.Body),
            rim(svgPath(SHIELD).scaled(INNER_SCALE), width = 2.2f)
        )
    )
)

// reputation: two ribbon tails behind a gold rosette with a coloured centre
private fun rosetteLayers(palette: BadgePalette) = listOf(
    BadgeLayer(
        part = BadgePart.Ribbon,
        shapes = listOf(
            BadgeShape(svgPath("M42 88 L28 120 L41 114 L49 123 L58 96 Z"), BadgeFill.Solid(palette.dark)),
            BadgeShape(svgPath("M78 88 L92 120 L79 114 L71 123 L62 96 Z"), BadgeFill.Solid(palette.dark))
        )
    ),
    BadgeLayer(part = BadgePart.Ring, shapes = listOf(BadgeShape(rosettePath(), BadgeFill.Gold))),
    BadgeLayer(
        part = BadgePart.Core,
        shapes = listOf(
            BadgeShape(circlePath(cx = CENTER_X, cy = CENTER_Y, r = 43f), BadgeFill.Body),
            rim(circlePath(cx = CENTER_X, cy = CENTER_Y, r = 43f), width = 2f)
        )
    )
)
