package com.example.cpen321application.ui.components.badges

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import com.example.cpen321application.review.model.Badge
import com.example.cpen321application.review.model.BadgeTier

/*  Where every part of a badge is at one moment of its unlock animation. Times are in seconds
    from when the popup opens, and match the design mockup. Getting started badges flip from
    grey to colour, milestone shields are built piece by piece, and reputation rosettes spin in
    before their trophy plops into the centre. Every animation ends on the badge at rest
 */

/*  one moment of the animation. Scales are 1 and alphas 1 at rest. leaves is how far each
    laurel leaf has popped on, 0 to 1. sparkle is how far the rays and sparkles have played,
    0 to 1, and shineX how far the shine has swept across, in grid units
 */
data class BadgeFrame(
    val bodyScale: Float = 1f,
    val bodyAlpha: Float = 1f,
    val rotationY: Float = 0f,
    val saturation: Float = 1f,
    val flash: Float = 0f,
    val outlineDrawn: Float = 1f,
    val outlineAlpha: Float = 0f,
    val frameAlpha: Float = 1f,
    val frameScale: Float = 1f,
    val innerRise: Float = 1f,
    val glossAlpha: Float = 1f,
    val dropY: Float = 0f,
    val dropAlpha: Float = 1f,
    val bannerPop: Float = 1f,
    val leaves: (Int) -> Float = { 1f },
    val ringRotation: Float = 0f,
    val ringScale: Float = 1f,
    val ringAlpha: Float = 1f,
    val corePop: Float = 1f,
    val ribbonY: Float = 0f,
    val ribbonAlpha: Float = 1f,
    val iconScaleX: Float = 1f,
    val iconScaleY: Float = 1f,
    val iconAlpha: Float = 1f,
    val crownY: Float = 0f,
    val crownAlpha: Float = 1f,
    val sparkle: Float = 0f,
    val shineX: Float = SHINE_START
) {
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

// how long each badge's animation lasts, and how long its popup stays open
fun animationSeconds(badge: Badge): Float = sparkleStart(badge) + SPARKLE_SECONDS

fun popupMillis(badge: Badge): Long = when (badge.tier) {
    BadgeTier.GettingStarted -> FLIP_POPUP_MS
    BadgeTier.Milestone -> BUILD_POPUP_MS
    BadgeTier.Reputation -> if (badge == Badge.MegaMvp) CROWN_POPUP_MS else SPIN_POPUP_MS
}

// when the rays and sparkles burst out, once the badge is complete
private fun sparkleStart(badge: Badge): Float = when (badge.tier) {
    BadgeTier.GettingStarted -> FLIP_DONE
    BadgeTier.Milestone -> BUILD_DONE
    BadgeTier.Reputation -> if (badge == Badge.MegaMvp) CROWN_DONE else SPIN_DONE
}

// how long each kind of popup stays open, enough to see its animation finish
private const val FLIP_POPUP_MS = 2600L
private const val BUILD_POPUP_MS = 3500L
private const val SPIN_POPUP_MS = 3200L
private const val CROWN_POPUP_MS = 3600L
// when each kind of badge is complete
private const val FLIP_DONE = 0.8f
private const val BUILD_DONE = 2.05f
private const val SPIN_DONE = 1.45f
private const val CROWN_DONE = 1.9f

fun badgeFrameAt(badge: Badge, seconds: Float): BadgeFrame {
    val built = when (badge.tier) {
        BadgeTier.GettingStarted -> flipFrame(seconds)
        BadgeTier.Milestone -> buildFrame(seconds)
        BadgeTier.Reputation -> spinFrame(seconds, thumpAt = sparkleStart(badge))
    }
    val start = sparkleStart(badge)
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

private val Thump = Keyframes("0 1, 0.35 1.12, 0.7 0.97, 1 1")
private val Drop = Keyframes("0 -70, 0.6 5, 0.8 -3, 1 0")
private val AppearEarly = Keyframes("0 0, 0.6 1, 1 1")
private val Pop = Keyframes("0 0, 0.7 1.2, 1 1")
private val PopAlpha = Keyframes("0 0, 0.7 1, 1 1")
private val OutlineFade = Keyframes("0 1, 0.7 1, 1 0")

/*  milestones: the shield's outline draws itself, the rim appears and the inside fills from
    the bottom, the icon drops in, then the banner and laurel leaves pop on and it thumps
 */
private fun buildFrame(seconds: Float): BadgeFrame {
    val outline = progress(seconds, start = 0f, length = 0.9f)
    val frame = eased(progress(seconds, start = 0.55f, length = 0.35f))
    val drop = progress(seconds, start = 1.25f, length = 0.6f)
    return BadgeFrame(
        outlineDrawn = eased(outline / OUTLINE_DRAWN_BY),
        outlineAlpha = if (seconds <= 0f || outline >= 1f) 0f else OutlineFade.at(outline),
        frameAlpha = frame,
        frameScale = lerp(FRAME_START_SCALE, 1f, frame),
        innerRise = eased(progress(seconds, start = 0.8f, length = 0.5f)),
        glossAlpha = progress(seconds, start = 0.9f, length = 0.3f),
        dropY = Drop.at(drop),
        dropAlpha = AppearEarly.at(drop),
        bannerPop = progress(seconds, start = 1.65f, length = 0.35f),
        leaves = { index -> progress(seconds, start = LEAVES_START + index * LEAF_GAP, length = 0.25f) },
        bodyScale = Thump.at(progress(seconds, start = 2.05f, length = 0.5f))
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
    val crown = progress(seconds, start = 1.6f, length = 0.45f)
    return BadgeFrame(
        ringRotation = SPINS_DEGREES * (1f - turned),
        ringScale = lerp(RING_START_SCALE, 1f, turned),
        ringAlpha = RingAlpha.at(spin),
        corePop = progress(seconds, start = 0.8f, length = 0.3f),
        glossAlpha = progress(seconds, start = 0.9f, length = 0.3f),
        ribbonY = lerp(RIBBON_RISE, 0f, ribbon),
        ribbonAlpha = ribbon,
        iconScaleX = PlopX.at(plop),
        iconScaleY = PlopY.at(plop),
        iconAlpha = PlopAlpha.at(plop),
        crownY = Drop.at(crown),
        crownAlpha = AppearEarly.at(crown),
        bodyScale = Thump.at(progress(seconds, start = thumpAt, length = 0.45f))
    )
}

// how a part that pops on looks after this much of its pop, as its scale and alpha
fun popScale(amount: Float): Float = Pop.at(amount)

fun popAlpha(amount: Float): Float = PopAlpha.at(amount)

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
    time 0 to 1, e.g. "0 0, 0.5 1, 1 0". Between two keyframes the value eases in and out
 */
class Keyframes(stops: String) {
    private val points = stops.split(",").map { stop -> floatsOf(stop).let { (time, value) -> time to value } }

    fun at(amount: Float): Float {
        val next = points.indexOfFirst { (time, _) -> time >= amount }
        if (next <= 0) return if (next == 0) points.first().second else points.last().second
        val (fromTime, fromValue) = points[next - 1]
        val (toTime, toValue) = points[next]
        return lerp(fromValue, toValue, eased((amount - fromTime) / (toTime - fromTime)))
    }
}
