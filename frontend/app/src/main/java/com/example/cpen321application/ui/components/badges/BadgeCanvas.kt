package com.example.cpen321application.ui.components.badges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.cpen321application.review.model.Badge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// how faint a badge that isn't earned yet is, on top of being grey
private const val LOCKED_ALPHA = 0.55f
// the bounce and flip turn around this point, just above the badge's middle
private const val PIVOT_Y = 58.3f
// how far behind the screen the camera sits for the flip, in multiples of the badge's size
private const val CAMERA_DISTANCE = 12f
private const val SHINE_ALPHA = 0.5f
private const val SHINE_PATH = "M27.3 -20 L49.3 -20 L-12.6 150 L-34.6 150 Z"
private const val OUTLINE_WIDTH = 3f
// a part popping on (the banner) grows from here
private const val BANNER_PIVOT_Y = 76f
private const val SHIELD_BOTTOM = 104f
// the trophy squashes around its base when it plops down
private const val ICON_PIVOT_Y = 72f

/*  draws a badge at one moment of its animation (BadgeFrame.Rest when it isn't animating).
    A badge that isn't earned is drawn grey and faded. Its height is 124/120 of its width
 */
@Composable
fun BadgeDrawing(badge: Badge, earned: Boolean, frame: BadgeFrame, modifier: Modifier = Modifier) {
    val art = remember(badge) { badgeArt(badge) }
    val textMeasurer = rememberTextMeasurer()
    val saturation = if (earned) frame.saturation else 0f
    Canvas(
        modifier = modifier
            .aspectRatio(BADGE_WIDTH / BADGE_HEIGHT)
            .graphicsLayer {
                rotationY = frame.rotationY
                cameraDistance = CAMERA_DISTANCE * density
                alpha = if (earned) frame.bodyAlpha else LOCKED_ALPHA
            }
    ) {
        val scale = size.width / BADGE_WIDTH
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            val painter = BadgePainter(art, frame, saturation, textMeasurer)
            if (frame.sparkle > 0f) painter.drawRays(this)
            withTransform({ scale(frame.bodyScale, frame.bodyScale, pivot = Offset(CENTER_X, PIVOT_Y)) }) {
                art.layers.forEach { layer -> painter.drawLayer(this, layer) }
                painter.drawOutline(this)
                painter.drawShineAndFlash(this)
            }
            if (frame.sparkle > 0f) painter.drawSparks(this)
        }
    }
}

// draws one badge's layers at one frame, in the badge's 120 × 124 grid
private class BadgePainter(
    private val art: BadgeArt,
    private val frame: BadgeFrame,
    private val saturation: Float,
    private val textMeasurer: TextMeasurer
) {
    // named so it can't be confused with DrawScope.center, the canvas's own centre in pixels
    private val badgeCenter = Offset(CENTER_X, CENTER_Y)

    fun drawLayer(scope: DrawScope, layer: BadgeLayer) = with(scope) {
        if (layer.part == BadgePart.Gloss) {
            clipPath(art.clip) { drawParts(this, layer, frame.glossAlpha) }
            return@with
        }
        if (!layer.inIcon) {
            withPartTransform(layer) { alpha -> drawParts(this, layer, alpha) }
            return@with
        }
        withTransform({
            scale(art.iconScale, art.iconScale, pivot = badgeCenter)
            scale(frame.iconScaleX, frame.iconScaleY, pivot = Offset(CENTER_X, ICON_PIVOT_Y))
        }) {
            withPartTransform(layer) { alpha -> drawParts(this, layer, alpha * frame.iconAlpha) }
        }
    }

    // moves the layer the way its part moves at this frame, then draws it with the part's alpha
    private fun DrawScope.withPartTransform(layer: BadgeLayer, draw: DrawScope.(Float) -> Unit) {
        when (layer.part) {
            BadgePart.Frame -> withTransform({ scale(frame.frameScale, frame.frameScale, pivot = badgeCenter) }) {
                draw(frame.frameAlpha)
            }
            BadgePart.Inner -> if (frame.innerRise > 0f) {
                withTransform({ scale(1f, frame.innerRise, pivot = Offset(CENTER_X, SHIELD_BOTTOM)) }) { draw(1f) }
            }
            BadgePart.Ring -> withTransform({
                rotate(frame.ringRotation, pivot = badgeCenter)
                scale(frame.ringScale, frame.ringScale, pivot = badgeCenter)
            }) { draw(frame.ringAlpha) }
            BadgePart.Core -> popped(frame.corePop, badgeCenter, draw)
            BadgePart.Banner -> popped(frame.bannerPop, Offset(CENTER_X, BANNER_PIVOT_Y), draw)
            BadgePart.Leaf -> popped(frame.leaves(layer.index), layer.shapes.first().path.getBounds().center, draw)
            BadgePart.Ribbon -> withTransform({ translate(top = frame.ribbonY) }) { draw(frame.ribbonAlpha) }
            BadgePart.Drop -> withTransform({ translate(top = frame.dropY) }) { draw(frame.dropAlpha) }
            BadgePart.Crown -> withTransform({ translate(top = frame.crownY) }) { draw(frame.crownAlpha) }
            BadgePart.Plain, BadgePart.Gloss -> draw(1f)
        }
    }

    // a part that hasn't started popping on has no size, and is skipped: drawing through a zero
    // scale can crash some GPU renderers
    private fun DrawScope.popped(amount: Float, pivot: Offset, draw: DrawScope.(Float) -> Unit) {
        val scale = popScale(amount)
        if (scale <= 0f) return
        withTransform({ scale(scale, scale, pivot = pivot) }) { draw(popAlpha(amount)) }
    }

    private fun drawParts(scope: DrawScope, layer: BadgeLayer, alpha: Float) = with(scope) {
        if (alpha <= 0f) return@with
        layer.shapes.forEach { shape ->
            val style = shape.strokeWidth?.let { Stroke(width = it, cap = StrokeCap.Round, join = StrokeJoin.Round) }
            if (style == null) {
                drawPath(shape.path, brushFor(shape), alpha = shape.alpha * alpha)
            } else {
                drawPath(shape.path, brushFor(shape), alpha = shape.alpha * alpha, style = style)
            }
        }
        layer.labels.forEach { label -> drawLabel(this, label, alpha) }
    }

    private fun brushFor(shape: BadgeShape): Brush {
        val bounds = shape.path.getBounds()
        return when (val fill = shape.fill) {
            is BadgeFill.Solid -> SolidColor(tint(fill.color))
            BadgeFill.Body -> Brush.verticalGradient(
                listOf(tint(art.palette.light), tint(art.palette.dark)),
                startY = bounds.top,
                endY = bounds.bottom
            )
            BadgeFill.Gold -> Brush.linearGradient(
                listOf(tint(GoldLight), tint(GoldDark)),
                start = bounds.topLeft,
                end = bounds.bottomRight
            )
        }
    }

    // text is measured in pixels, which are grid units here since the canvas is scaled to the grid
    private fun drawLabel(scope: DrawScope, label: BadgeLabel, alpha: Float) = with(scope) {
        val layout = textMeasurer.measure(
            label.text,
            TextStyle(fontSize = label.size.toSp(), fontWeight = FontWeight.Bold, color = tint(label.color))
        )
        drawText(
            layout,
            topLeft = Offset(label.x - layout.size.width / 2f, label.baseline - layout.firstBaseline),
            alpha = alpha
        )
    }

    // a milestone's outline drawing itself in, the first step of building the shield
    fun drawOutline(scope: DrawScope) = with(scope) {
        val outline = art.outline ?: return@with
        if (frame.outlineAlpha <= 0f) return@with
        val measure = PathMeasure().apply { setPath(outline, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * frame.outlineDrawn, drawn, true)
        drawPath(
            drawn,
            tint(art.palette.light),
            alpha = frame.outlineAlpha,
            style = Stroke(width = OUTLINE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // the shine sweeping across, and the white flash as a getting started badge turns to colour
    fun drawShineAndFlash(scope: DrawScope) = with(scope) {
        val shining = frame.shineX > SHINE_START
        if (!shining && frame.flash <= 0f) return@with
        clipPath(art.clip) {
            if (shining) {
                withTransform({ translate(left = frame.shineX) }) {
                    drawPath(svgPath(SHINE_PATH), Color.White, alpha = SHINE_ALPHA)
                }
            }
            if (frame.flash > 0f) drawRect(Color.White, size = Size(BADGE_WIDTH, BADGE_HEIGHT), alpha = frame.flash)
        }
    }

    // light rays turning behind the badge once it's complete, fading as they spread
    fun drawRays(scope: DrawScope) = with(scope) {
        val (alpha, scale, turn) = raysAt(frame.sparkle)
        if (alpha <= 0f) return@with
        val radius = RAYS_RADIUS * scale
        val glow = tint(art.palette.light)
        val brush = Brush.radialGradient(
            RAYS_SOLID_UNTIL to glow,
            RAYS_GONE_BY to Color.Transparent,
            center = Offset(CENTER_X, PIVOT_Y),
            radius = radius
        )
        for (ray in 0 until RAY_COUNT) {
            drawArc(
                brush = brush,
                startAngle = turn + ray * RAY_EVERY_DEGREES,
                sweepAngle = RAY_WIDTH_DEGREES,
                useCenter = true,
                topLeft = Offset(CENTER_X - radius, PIVOT_Y - radius),
                size = Size(radius * 2, radius * 2),
                alpha = alpha
            )
        }
    }

    // little stars bursting out from the centre, every other one gold
    fun drawSparks(scope: DrawScope) = with(scope) {
        val alpha = sparkAlpha(frame.sparkle)
        if (alpha <= 0f) return@with
        val sparkSize = lerp(SPARK_START_SIZE, SPARK_END_SIZE, frame.sparkle)
        for (spark in 0 until SPARK_COUNT) {
            val angle = (spark * SPARK_EVERY_DEGREES + SPARK_FIRST_DEGREES) * PI / HALF_TURN
            val reach = if (spark % 2 == 0) 1f else SHORT_SPARK
            val distance = frame.sparkle * reach * SPARK_DISTANCE
            val x = CENTER_X + distance * sin(angle).toFloat()
            val y = PIVOT_Y - distance * cos(angle).toFloat()
            val color = if (spark % 2 == 0) SparkGold else tint(art.palette.light)
            val star = starPath(cx = x, cy = y, outer = sparkSize, inner = sparkSize * SPARK_WAIST, points = 4)
            drawPath(star, color, alpha = alpha)
        }
    }

    // the colour as it looks at this frame's saturation: grey at 0, full colour at 1
    private fun tint(color: Color): Color {
        if (saturation >= 1f) return color
        val grey = color.red * RED_WEIGHT + color.green * GREEN_WEIGHT + color.blue * BLUE_WEIGHT
        return Color(
            red = lerp(grey, color.red, saturation),
            green = lerp(grey, color.green, saturation),
            blue = lerp(grey, color.blue, saturation),
            alpha = color.alpha
        )
    }
}

// how much red, green and blue count towards how light a colour looks, for greying it out
private const val RED_WEIGHT = 0.299f
private const val GREEN_WEIGHT = 0.587f
private const val BLUE_WEIGHT = 0.114f
private const val HALF_TURN = 180f
private const val RAYS_RADIUS = 98f
private const val RAYS_SOLID_UNTIL = 0.18f
private const val RAYS_GONE_BY = 0.68f
private const val RAY_COUNT = 15
private const val RAY_EVERY_DEGREES = 24f
private const val RAY_WIDTH_DEGREES = 9f
private const val SPARK_COUNT = 12
private const val SPARK_EVERY_DEGREES = 30f
private const val SPARK_FIRST_DEGREES = 15f
private const val SPARK_DISTANCE = 75f
private const val SHORT_SPARK = 0.72f
private const val SPARK_START_SIZE = 1.5f
private const val SPARK_END_SIZE = 5.5f
private const val SPARK_WAIST = 0.3f
private val SparkGold = Color(0xFFFFE27A)
