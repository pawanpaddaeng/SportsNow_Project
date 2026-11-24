package com.example.cpen321application.ui.components.badges

import androidx.compose.ui.graphics.Color
import com.example.cpen321application.review.model.Badge

// the icon in the middle of each badge, and each badge's colours, matching the design mockup

private val GreenLight = Color(0xFF34C182)
private val GreenDark = Color(0xFF12814D)
private val GreenRing = Color(0xFF0C6A3E)
private val OrangeLight = Color(0xFFFFA24A)
private val OrangeDark = Color(0xFFE2611A)
private val OrangeRing = Color(0xFFB84A10)
private val BlueLight = Color(0xFF5A93D6)
private val BlueDark = Color(0xFF2D5E98)
private val BlueRing = Color(0xFF1F4F85)
private val PurpleLight = Color(0xFF9A7BEA)
private val PurpleDark = Color(0xFF5E42B6)
private val PurpleRing = Color(0xFF47308F)
private val TealLight = Color(0xFF2EB5C7)
private val TealDark = Color(0xFF10737F)
private val TealRing = Color(0xFF0B5962)
private val RedLight = Color(0xFFE2555A)
private val RedDark = Color(0xFFA1222C)
private val RedRing = Color(0xFF7E1820)
private val PinkLight = Color(0xFFF470AE)
private val PinkDark = Color(0xFFC2307A)
private val PinkRing = Color(0xFF9A2260)
private val NavyLight = Color(0xFF3570B5)
private val NavyDark = Color(0xFF163C6B)
private val VioletLight = Color(0xFF7A4BDB)
private val VioletDark = Color(0xFF311572)
val GoldLight = Color(0xFFFFE08A)
val GoldDark = Color(0xFFD99A1E)
private val LaurelGold = Color(0xFFF5C451)
private val TrophyGold = Color(0xFFFFD66B)

// faint parts of an icon, e.g. the friend standing behind in Squad Up
private const val FAINT = 0.72f
private const val FAINTER = 0.35f
// Veteran's laurel leaves: their angles around the centre on one side, mirrored on the other
private const val LEAF_ANGLES = "212 189 166 143 120"
private const val LEAF_RADIUS = 36f
private const val MIRROR_DEGREES = 180f
private const val MEGA_TROPHY_SCALE = 0.82f
private const val FINGER_SHIFT = 3f

fun paletteOf(badge: Badge): BadgePalette = when (badge) {
    Badge.FirstWhistle -> BadgePalette(GreenLight, GreenDark, GreenRing)
    Badge.GameMaker -> BadgePalette(OrangeLight, OrangeDark, OrangeRing)
    Badge.SquadUp -> BadgePalette(BlueLight, BlueDark, BlueRing)
    Badge.FairJudge -> BadgePalette(PurpleLight, PurpleDark, PurpleRing)
    Badge.Regular -> BadgePalette(TealLight, TealDark, TealRing)
    Badge.Veteran -> BadgePalette(RedLight, RedDark, RedRing)
    Badge.SocialButterfly -> BadgePalette(PinkLight, PinkDark, PinkRing)
    Badge.Mvp -> BadgePalette(NavyLight, NavyDark, GoldDark)
    Badge.MegaMvp -> BadgePalette(VioletLight, VioletDark, GoldDark)
}

fun iconLayers(badge: Badge, palette: BadgePalette): List<BadgeLayer> = when (badge) {
    Badge.FirstWhistle -> listOf(foamFinger(palette.dark))
    Badge.GameMaker -> listOf(calendar(palette.dark))
    Badge.SquadUp -> listOf(people())
    Badge.FairJudge -> listOf(award())
    Badge.Regular -> listOf(ball(palette.dark), banner("10", palette.dark))
    Badge.Veteran -> laurels() + listOf(ball(palette.dark), banner("50", palette.dark))
    Badge.SocialButterfly -> listOf(butterfly(palette))
    Badge.Mvp -> listOf(BadgeLayer(BadgePart.Plain, trophy(White, palette.dark), inIcon = true))
    Badge.MegaMvp -> megaTrophy(palette.dark)
}

private fun solid(color: Color) = BadgeFill.Solid(color)

// a foam finger pointing up, with a 1 on the palm. Moved right so the thumb doesn't pull it off centre
private fun foamFinger(accent: Color) = BadgeLayer(
    part = BadgePart.Plain,
    inIcon = true,
    shapes = fingerShapes(accent).map { shape -> shape.copy(path = shape.path.scaled(scale = 1f, dx = FINGER_SHIFT)) },
    labels = listOf(BadgeLabel(text = "1", x = 61f + FINGER_SHIFT, baseline = 76.5f, size = 19f, color = accent))
)

private fun fingerShapes(accent: Color) = listOf(
    BadgeShape(roundRectPath(x = 51f, y = 21f, width = 14f, height = 38f, radius = 7f), solid(White)),
    BadgeShape(roundRectPath(x = 62f, y = 47f, width = 20f, height = 13f, radius = 6.5f), solid(White)),
    BadgeShape(svgPath("M68 49 v9 M75 49 v9"), solid(accent), strokeWidth = 1.6f, alpha = FAINTER),
    BadgeShape(
        roundRectPath(x = 33f, y = 55f, width = 16f, height = 10f, radius = 5f)
            .rotated(degrees = -28f, pivotX = 41f, pivotY = 60f),
        solid(White)
    ),
    BadgeShape(svgPath("M42 54 h38 v17 a12 12 0 0 1 -12 12 h-14 a12 12 0 0 1 -12 -12 Z"), solid(White)),
    BadgeShape(roundRectPath(x = 49f, y = 83f, width = 25f, height = 7f, radius = 2f), solid(White), alpha = 0.7f)
)

// a calendar with a plus on it
private fun calendar(accent: Color) = BadgeLayer(
    part = BadgePart.Plain,
    inIcon = true,
    shapes = listOf(
        BadgeShape(roundRectPath(x = 38f, y = 40f, width = 44f, height = 40f, radius = 6f), solid(White)),
        BadgeShape(svgPath("M38 52 h44"), solid(accent), strokeWidth = 2f, alpha = FAINTER),
        BadgeShape(ringPath(x = 47f), solid(White)),
        BadgeShape(ringPath(x = 47f), solid(accent), strokeWidth = 1.5f),
        BadgeShape(ringPath(x = 68f), solid(White)),
        BadgeShape(ringPath(x = 68f), solid(accent), strokeWidth = 1.5f),
        BadgeShape(roundRectPath(x = 57f, y = 57f, width = 6f, height = 18f, radius = 2f), solid(accent)),
        BadgeShape(roundRectPath(x = 51f, y = 63f, width = 18f, height = 6f, radius = 2f), solid(accent))
    )
)

// one of the calendar's two binder rings, its left edge at x
private fun ringPath(x: Float) = roundRectPath(x = x, y = 34f, width = 5f, height = 12f, radius = 2.5f)

// two people, a friend standing behind
private fun people() = BadgeLayer(
    part = BadgePart.Plain,
    inIcon = true,
    shapes = listOf(
        BadgeShape(circlePath(cx = 72f, cy = 48f, r = 8f), solid(White), alpha = FAINT),
        BadgeShape(svgPath("M58 80 a14 13 0 0 1 28 0 Z"), solid(White), alpha = FAINT),
        BadgeShape(circlePath(cx = 52f, cy = 50f, r = 10f), solid(White)),
        BadgeShape(svgPath("M34 82 a18 16 0 0 1 36 0 Z"), solid(White))
    )
)

// an award ribbon drawn in outline: a circle with a star, and two tails
private fun award() = BadgeLayer(
    part = BadgePart.Plain,
    inIcon = true,
    shapes = listOf(
        BadgeShape(circlePath(cx = CENTER_X, cy = 49f, r = 16f), solid(White), strokeWidth = 3.6f),
        BadgeShape(svgPath("M51 62 L46 83 L53.5 79.5 L58 86 L60.5 66"), solid(White), strokeWidth = 3.6f),
        BadgeShape(svgPath("M69 62 L74 83 L66.5 79.5 L62 86 L59.5 66"), solid(White), strokeWidth = 3.6f),
        BadgeShape(starPath(cx = CENTER_X, cy = 49.5f, outer = 8f, inner = 3.6f), solid(White), strokeWidth = 2.4f)
    )
)

// a ball, which drops in when a milestone is built
private fun ball(accent: Color) = BadgeLayer(
    part = BadgePart.Drop,
    inIcon = true,
    shapes = listOf(
        BadgeShape(circlePath(cx = CENTER_X, cy = 50f, r = 18f), solid(White)),
        BadgeShape(
            svgPath("M42 50 L78 50 M60 32 L60 68 M47 37 Q55 50 47 63 M73 37 Q65 50 73 63"),
            solid(accent),
            strokeWidth = 2.2f
        )
    )
)

// the number of games under the ball
private fun banner(number: String, accent: Color) = BadgeLayer(
    part = BadgePart.Banner,
    inIcon = true,
    shapes = listOf(BadgeShape(roundRectPath(x = 42f, y = 68f, width = 36f, height = 16f, radius = 4f), solid(White))),
    labels = listOf(BadgeLabel(text = number, x = CENTER_X, baseline = 80.6f, size = 14f, color = accent))
)

// Veteran's gold laurel leaves, both sides from the bottom up so they pop on in pairs
private fun laurels(): List<BadgeLayer> =
    floatsOf(LEAF_ANGLES).flatMap { angle -> listOf(angle, MIRROR_DEGREES - angle) }.mapIndexed { index, angle ->
        val (x, y) = pointOnCircle(radius = LEAF_RADIUS, angle = angle)
        BadgeLayer(
            part = BadgePart.Leaf,
            inIcon = true,
            index = index,
            shapes = listOf(
                BadgeShape(ellipsePath(cx = x, cy = y, rx = 3.2f, ry = 7f, rotation = angle), solid(LaurelGold))
            )
        )
    }

private fun butterfly(palette: BadgePalette) = BadgeLayer(
    part = BadgePart.Drop,
    inIcon = true,
    shapes = listOf(
        BadgeShape(ellipsePath(cx = 46f, cy = 49f, rx = 14f, ry = 12f, rotation = -25f), solid(White)),
        BadgeShape(ellipsePath(cx = 74f, cy = 49f, rx = 14f, ry = 12f, rotation = 25f), solid(White)),
        BadgeShape(ellipsePath(cx = 49f, cy = 69f, rx = 9f, ry = 10f, rotation = 20f), solid(White), alpha = 0.85f),
        BadgeShape(ellipsePath(cx = 71f, cy = 69f, rx = 9f, ry = 10f, rotation = -20f), solid(White), alpha = 0.85f),
        BadgeShape(circlePath(cx = 44f, cy = 48f, r = 4f), solid(palette.dark), alpha = 0.45f),
        BadgeShape(circlePath(cx = 76f, cy = 48f, r = 4f), solid(palette.dark), alpha = 0.45f),
        BadgeShape(roundRectPath(x = 57.5f, y = 44f, width = 5f, height = 34f, radius = 2.5f), solid(palette.ring)),
        BadgeShape(svgPath("M59 45 q-4 -9 -9 -11 M61 45 q4 -9 9 -11"), solid(White), strokeWidth = 2f)
    )
)

// a trophy in color, with a star in accent on the cup
private fun trophy(color: Color, accent: Color): List<BadgeShape> = listOf(
    BadgeShape(svgPath("M44 38 h32 v12 a16 16 0 0 1 -32 0 Z"), solid(color)),
    BadgeShape(svgPath("M44 42 h-6 a7 7 0 0 0 7 11 M76 42 h6 a7 7 0 0 1 -7 11"), solid(color), strokeWidth = 3.5f),
    BadgeShape(roundRectPath(x = 56.5f, y = 64f, width = 7f, height = 10f, radius = 0.5f), solid(color)),
    BadgeShape(roundRectPath(x = 46f, y = 74f, width = 28f, height = 7f, radius = 2f), solid(color)),
    BadgeShape(starPath(cx = CENTER_X, cy = 50f, outer = 7f, inner = 3f), solid(accent))
)

// a smaller gold trophy with a gold crown above it, which drops on last
private fun megaTrophy(accent: Color): List<BadgeLayer> = listOf(
    BadgeLayer(
        part = BadgePart.Plain,
        inIcon = true,
        shapes = trophy(TrophyGold, accent).map { shape ->
            shape.copy(path = shape.path.scaled(scale = MEGA_TROPHY_SCALE, dy = 6f))
        }
    ),
    BadgeLayer(
        part = BadgePart.Crown,
        inIcon = true,
        shapes = listOf(
            BadgeShape(svgPath("M45 37 L48 23 L54.5 30 L60 19 L65.5 30 L72 23 L75 37 Z"), solid(TrophyGold)),
            BadgeShape(circlePath(cx = CENTER_X, cy = 19f, r = 2.6f), solid(White)),
            BadgeShape(circlePath(cx = 48f, cy = 23f, r = 2.2f), solid(White)),
            BadgeShape(circlePath(cx = 72f, cy = 23f, r = 2.2f), solid(White))
        )
    )
)
