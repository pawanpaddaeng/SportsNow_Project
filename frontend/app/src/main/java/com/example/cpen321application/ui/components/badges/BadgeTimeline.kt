package com.example.cpen321application.ui.components.badges

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import com.example.cpen321application.review.model.Badge
import com.example.cpen321application.review.model.BadgeTier

/*  Where every part of a badge is at one moment of its unlock animation. Times are in seconds
    from when the popup opens, and match the design mockup. Getting started badges flip from
    grey to colour, milestone shields are built piece by piece, and reputation rosettes spin in
    before their trophy plops into the centre. Every animation ends on the badge at rest
 */

/*  how one part of a badge is moved at one moment: faded to alpha, shrunk or grown around pivot
    (the part's own centre when null), turned by rotation degrees and moved down by dy, all in
    grid units. A part with no size yet (a scale of 0) isn't drawn
 */
data class PartMotion(
    val alpha: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = scaleX,
    val rotation: Float = 0f,
    val dy: Float = 0f,
    val pivot: Offset? = null
)

private val Still = PartMotion()

/*  one moment of the animation, at rest by default. parts says how each part moves, any part
    not in it stays still. leaf is how laurel leaf number index moves, and icon how the whole
    icon moves. sparkle is how far the rays and sparkles have played, 0 to 1, and shineX how far
    the shine has swept across, in grid units
 */
data class BadgeFrame(
    val bodyScale: Float = 1f,
    val bodyAlpha: Float = 1f,
    val rotationY: Float = 0f,
    val saturation: Float = 1f,
    val flash: Float = 0f,
    val outlineDrawn: Float = 1f,
    val outlineAlpha: Float = 0f,
    val parts: Map<BadgePart, PartMotion> = emptyMap(),
    val leaf: (Int) -> PartMotion = { Still },
    val icon: PartMotion = Still,
    val sparkle: Float = 0f,
    val shineX: Float = SHINE_START
) {
    // how this layer moves at this frame
    fun motionOf(layer: BadgeLayer): PartMotion =
        if (layer.part == BadgePart.Leaf) leaf(layer.index) else parts[layer.part] ?: Still

    companion object {
        // the badge sitting still, as on the profile
        val Rest = BadgeFrame()
    }
}

const val SHINE_START = -90f
private const val SHINE_END = 190f
private const val SPARKLE_SECONDS = 0.95f
private const val SHINE_DELAY = 0.15f
private const val SHINE_SECONDS = 0.55f

/*  when a kind of badge is complete (the rays and sparkles burst out then), and how long its
    popup stays open, enough to see the animation finish
 */
private class Timing(val doneAt: Float, val popupMillis: Long)

private val TierTimings = mapOf(
    BadgeTier.GettingStarted to Timing(doneAt = 0.8f, popupMillis = 2600L),
    BadgeTier.Milestone to Timing(doneAt = 2.05f, popupMillis = 3500L),
    BadgeTier.Reputation to Timing(doneAt = 1.45f, popupMillis = 3200L)
)
// Mega MVP's crown drops on after the trophy, so it finishes later
private val CrownTiming = Timing(doneAt = 1.9f, popupMillis = 3600L)

private fun timingOf(badge: Badge): Timing =
    if (badge == Badge.MegaMvp) CrownTiming else TierTimings.getValue(badge.tier)

// how long each badge's animation lasts, and how long its popup stays open
fun animationSeconds(badge: Badge): Float = timingOf(badge).doneAt + SPARKLE_SECONDS

fun popupMillis(badge: Badge): Long = timingOf(badge).popupMillis

fun badgeFrameAt(badge: Badge, seconds: Float): BadgeFrame {
    val start = timingOf(badge).doneAt
    val built = when (badge.tier) {
        BadgeTier.GettingStarted -> flipFrame(seconds)
        BadgeTier.Milestone -> buildFrame(seconds, thumpAt = start)
        BadgeTier.Reputation -> spinFrame(seconds, thumpAt = start)
    }
    return built.copy(
        sparkle = progress(seconds, start = start, length = SPARKLE_SECONDS),
        shineX = lerp(
            SHINE_START,
            SHINE_END,
            eased(progress(seconds, start = start + SHINE_DELAY, length = SHINE_SECONDS))
        )
    )
}

private const val FLIP_SECONDS = 1.7f
private val FlipScale = Keyframes("0 0.35, 0.12 0.9, 0.42 1.14, 0.52 1.24, 0.7 0.95, 0.84 1.04, 1 1")
private val FlipTurn = Keyframes("0 0, 0.12 60, 0.42 360, 1 360")
private val FlipAlpha = Keyframes("0 0, 0.12 1, 1 1")
private val FlipColour = Keyframes("0 0, 0.42 0, 0.52 1, 1 1")
private val FlipFlash = Keyframes("0 0, 0.42 0, 0.52 0.45, 0.7 0.1, 0.84 0, 1 0")

// getting started: flips over while grey, flashes into colour and settles with a bounce
private fun flipFrame(seconds: Float): BadgeFrame {
    val x = progress(seconds, start = 0f, length = FLIP_SECONDS)
    return BadgeFrame(
        bodyScale = FlipScale.at(x),
        bodyAlpha = FlipAlpha.at(x),
        rotationY = FlipTurn.at(x),
        saturation = FlipColour.at(x),
        flash = FlipFlash.at(x)
    )
}

// the points parts grow, shrink and turn around, in grid units
private val BadgeCenter = Offset(x = CENTER_X, y = CENTER_Y)
// the shield's inside fills up from its bottom
private val ShieldBottom = Offset(x = CENTER_X, y = 104f)
private val BannerMiddle = Offset(x = CENTER_X, y = 76f)
// the trophy squashes around its base when it plops down
private val TrophyBase = Offset(x = CENTER_X, y = 72f)

private val Thump = Keyframes("0 1, 0.35 1.12, 0.7 0.97, 1 1")
private val Drop = Keyframes("0 -70, 0.6 5, 0.8 -3, 1 0")
private val AppearEarly = Keyframes("0 0, 0.6 1, 1 1")
private val Pop = Keyframes("0 0, 0.7 1.2, 1 1")
private val PopAlpha = Keyframes("0 0, 0.7 1, 1 1")
private val OutlineFade = Keyframes("0 1, 0.7 1, 1 0")

// a part popping on: growing from nothing past its size and back, fading in as it grows
private fun popping(amount: Float, pivot: Offset? = null) =
    PartMotion(alpha = PopAlpha.at(amount), scaleX = Pop.at(amount), pivot = pivot)

// a part dropping in from above with a small bounce
private fun dropping(amount: Float) = PartMotion(alpha = AppearEarly.at(amount), dy = Drop.at(amount))

/*  milestones: the shield's outline draws itself, the rim appears and the inside fills from
    the bottom, the icon drops in, then the banner and laurel leaves pop on and it thumps
 */
private fun buildFrame(seconds: Float, thumpAt: Float): BadgeFrame {
    val outline = progress(seconds, start = 0f, length = 0.9f)
    val frame = eased(progress(seconds, start = 0.55f, length = 0.35f))
    return BadgeFrame(
        outlineDrawn = eased(outline / OUTLINE_DRAWN_BY),
        outlineAlpha = OutlineFade.at(outline),
        parts = mapOf(
            BadgePart.Frame to PartMotion(
                alpha = frame,
                scaleX = lerp(FRAME_START_SCALE, 1f, frame),
                pivot = BadgeCenter
            ),
            BadgePart.Inner to PartMotion(
                scaleX = 1f,
                scaleY = eased(progress(seconds, start = 0.8f, length = 0.5f)),
                pivot = ShieldBottom
            ),
            BadgePart.Gloss to PartMotion(alpha = progress(seconds, start = 0.9f, length = 0.3f)),
            BadgePart.Drop to dropping(progress(seconds, start = 1.25f, length = 0.6f)),
            BadgePart.Banner to popping(progress(seconds, start = 1.65f, length = 0.35f), BannerMiddle)
        ),
        leaf = { index -> popping(progress(seconds, start = LEAVES_START + index * LEAF_GAP, length = 0.25f)) },
        bodyScale = Thump.at(progress(seconds, start = thumpAt, length = 0.5f))
    )
}

private const val OUTLINE_DRAWN_BY = 0.7f
private const val FRAME_START_SCALE = 0.94f
private const val LEAVES_START = 1.7f
private const val LEAF_GAP = 0.05f
private const val SPINS_DEGREES = -720f
private const val RING_START_SCALE = 0.3f
private const val RIBBON_RISE = -16f
private val SpinIn = CubicBezierEasing(a = 0.15f, b = 0.7f, c = 0.2f, d = 1f)
private val RingAlpha = Keyframes("0 0, 0.15 1, 1 1")
private val PlopX = Keyframes("0 2.4, 0.45 1, 0.62 1.22, 0.8 0.93, 1 1")
private val PlopY = Keyframes("0 2.4, 0.45 1, 0.62 0.8, 0.8 1.07, 1 1")
private val PlopAlpha = Keyframes("0 0, 0.45 1, 1 1")

/*  reputation: the gold rosette spins in from small, the centre and ribbons appear, then the
    trophy plops into the middle, squashing as it lands. Mega MVP's crown drops on last
 */
private fun spinFrame(seconds: Float, thumpAt: Float): BadgeFrame {
    val spin = progress(seconds, start = 0f, length = 1.1f)
    val turned = SpinIn.transform(spin)
    val ribbon = eased(progress(seconds, start = 1f, length = 0.3f))
    val plop = progress(seconds, start = 1.15f, length = 0.6f)
    return BadgeFrame(
        parts = mapOf(
            BadgePart.Ring to PartMotion(
                alpha = RingAlpha.at(spin),
                scaleX = lerp(RING_START_SCALE, 1f, turned),
                rotation = SPINS_DEGREES * (1f - turned),
                pivot = BadgeCenter
            ),
            BadgePart.Core to popping(progress(seconds, start = 0.8f, length = 0.3f), BadgeCenter),
            BadgePart.Gloss to PartMotion(alpha = progress(seconds, start = 0.9f, length = 0.3f)),
            BadgePart.Ribbon to PartMotion(alpha = ribbon, dy = lerp(RIBBON_RISE, 0f, ribbon)),
            BadgePart.Crown to dropping(progress(seconds, start = 1.6f, length = 0.45f))
        ),
        icon = PartMotion(
            alpha = PlopAlpha.at(plop),
            scaleX = PlopX.at(plop),
            scaleY = PlopY.at(plop),
            pivot = TrophyBase
        ),
        bodyScale = Thump.at(progress(seconds, start = thumpAt, length = 0.45f))
    )
}

private val RaysAlpha = Keyframes("0 0, 0.25 0.6, 1 0")
private val RaysScale = Keyframes("0 0.4, 0.25 1, 1 1.15")
private val RaysTurn = Keyframes("0 0, 0.25 25, 1 70")
private val SparkAlpha = Keyframes("0 0, 0.18 1, 1 0")

// the rays behind the badge after this much of the burst: their alpha, scale and turn in degrees
fun raysAt(amount: Float): Triple<Float, Float, Float> =
    Triple(RaysAlpha.at(amount), RaysScale.at(amount), RaysTurn.at(amount))

fun sparkAlpha(amount: Float): Float = SparkAlpha.at(amount)

// 0 before start, 1 after start + length, and how far through it in between
fun progress(seconds: Float, start: Float, length: Float): Float = ((seconds - start) / length).coerceIn(0f, 1f)

fun lerp(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun eased(amount: Float): Float = FastOutSlowInEasing.transform(amount.coerceIn(0f, 1f))

/*  values that change over an animation, written like CSS keyframes: "time value" pairs from
    time 0 to 1, e.g. "0 0, 0.5 1, 1 0". Between two keyframes the value eases in and out.
    Before the first keyframe it holds the first value, after the last the last value
 */
class Keyframes(stops: String) {
    private val points = stops.split(",").map { stop -> floatsOf(stop).let { (time, value) -> time to value } }

    fun at(amount: Float): Float {
        val time = amount.coerceIn(points.first().first, points.last().first)
        // the keyframe at or after time, never the first so there's always one before it
        val next = points.indexOfFirst { (keyTime, _) -> keyTime >= time }.coerceAtLeast(1)
        val (fromTime, fromValue) = points[next - 1]
        val (toTime, toValue) = points[next]
        return lerp(fromValue, toValue, eased((time - fromTime) / (toTime - fromTime)))
    }
}
