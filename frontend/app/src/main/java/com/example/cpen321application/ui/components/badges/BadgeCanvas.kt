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
import androidx.compose.ui.graphics.drawscope.Fill
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

/*  draws a badge at one moment of its animation (BadgeFrame.Rest when it isn't animating).
    A badge that isn't earned is drawn grey and faded. Its height is 124/120 of its width
 */
@Composable
fun BadgeDrawing(badge: Badge, earned: Boolean, frame: BadgeFrame, modifier: Modifier = Modifier) {
    val art = remember(badge) { badgeArt(badge) }
    val textMeasurer = rememberTextMeasurer()
    // a badge that isn't earned is drawn with no colour, faded
    val (saturation, alpha) = if (earned) frame.saturation to frame.bodyAlpha else 0f to LOCKED_ALPHA
    Canvas(
        modifier = modifier
            .aspectRatio(BADGE_WIDTH / BADGE_HEIGHT)
            .graphicsLayer {
                rotationY = frame.rotationY
                cameraDistance = CAMERA_DISTANCE * density
                this.alpha = alpha
            }
    ) {
        val scale = size.width / BADGE_WIDTH
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            with(BadgePainter(art, frame, saturation, textMeasurer)) {
                if (frame.sparkle > 0f) drawRays()
                withTransform({ scale(frame.bodyScale, frame.bodyScale, pivot = Offset(CENTER_X, PIVOT_Y)) }) {
                    art.layers.forEach { layer -> drawBadgeLayer(layer) }
                    drawShieldOutline()
                    drawShineAndFlash()
                }
                if (frame.sparkle > 0f) drawSparks()
            }
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

    /*  a layer moved the way its part moves at this frame. The gloss stays inside the badge, and
        icon layers are drawn at the icon's size and move with the whole icon too
     */
    fun DrawScope.drawBadgeLayer(layer: BadgeLayer) {
        val bounds = layer.shapes.first().path.getBounds()
        when {
            layer.part == BadgePart.Gloss -> clipPath(art.clip) {
                moved(frame.motionOf(layer), bounds.center) { alpha -> drawParts(layer, alpha) }
            }
            !layer.inIcon -> moved(frame.motionOf(layer), bounds.center) { alpha -> drawParts(layer, alpha) }
            else -> withTransform({ scale(art.iconScale, art.iconScale, pivot = badgeCenter) }) {
                moved(frame.icon, bounds.center) { iconAlpha ->
                    moved(frame.motionOf(layer), bounds.center) { alpha -> drawParts(layer, alpha * iconAlpha) }
                }
            }
        }
    }

    /*  draws with the motion applied, passing on its alpha. ownCenter is the pivot when the motion
        has none. A part with no size yet is skipped: drawing through a zero scale can crash some
        GPU renderers
     */
    private fun DrawScope.moved(motion: PartMotion, ownCenter: Offset, draw: DrawScope.(Float) -> Unit) {
        if (motion.scaleX <= 0f || motion.scaleY <= 0f) return
        val pivot = motion.pivot ?: ownCenter
        withTransform({
            translate(top = motion.dy)
            rotate(motion.rotation, pivot = pivot)
            scale(motion.scaleX, motion.scaleY, pivot = pivot)
        }) { draw(motion.alpha) }
    }

    private fun DrawScope.drawParts(layer: BadgeLayer, alpha: Float) {
        layer.shapes.forEach { shape ->
            val style = shape.strokeWidth?.let(::roundStroke) ?: Fill
            drawPath(shape.path, brushFor(shape), alpha = shape.alpha * alpha, style = style)
        }
        layer.labels.forEach { label -> drawLabel(label, alpha) }
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
    private fun DrawScope.drawLabel(label: BadgeLabel, alpha: Float) {
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

    private fun roundStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /*  a milestone's outline drawing itself in, the first step of building the shield. Only the
        build animation makes the outline visible, so other badges never draw it
     */
    fun DrawScope.drawShieldOutline() {
        if (frame.outlineAlpha <= 0f) return
        val measure = PathMeasure()
        measure.setPath(art.clip, false)
        val drawn = Path()
        measure.getSegment(0f, measure.length * frame.outlineDrawn, drawn, true)
        drawPath(
            drawn,
            tint(art.palette.light),
            alpha = frame.outlineAlpha,
            style = roundStroke(OUTLINE_WIDTH)
        )
    }

    /*  the shine sweeping across, and the white flash as a getting started badge turns to colour.
        Skipped while neither shows: clipping is what crashed GPU renderers while the badge is
        turned edge-on in its flip, before the flash or shine start
     */
    fun DrawScope.drawShineAndFlash() {
        if (frame.shineX <= SHINE_START && frame.flash <= 0f) return
        clipPath(art.clip) {
            withTransform({ translate(left = frame.shineX) }) { drawPath(ShinePath, Color.White, alpha = SHINE_ALPHA) }
            drawRect(Color.White, size = Size(BADGE_WIDTH, BADGE_HEIGHT), alpha = frame.flash)
        }
    }

    // light rays turning behind the badge once it's complete, fading as they spread
    fun DrawScope.drawRays() {
        val (alpha, scale, turn) = raysAt(frame.sparkle)
        val radius = RAYS_RADIUS * scale
        val brush = Brush.radialGradient(
            RAYS_SOLID_UNTIL to tint(art.palette.light),
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

    // little stars bursting out from the centre, every other one gold and reaching further
    fun DrawScope.drawSparks() {
        val alpha = sparkAlpha(frame.sparkle)
        val sparkSize = lerp(SPARK_START_SIZE, SPARK_END_SIZE, frame.sparkle)
        val colors = listOf(SparkGold, tint(art.palette.light))
        for (spark in 0 until SPARK_COUNT) {
            val angle = (spark * SPARK_EVERY_DEGREES + SPARK_FIRST_DEGREES) * PI / HALF_TURN
            val distance = frame.sparkle * SparkReach[spark % 2] * SPARK_DISTANCE
            val x = CENTER_X + distance * sin(angle).toFloat()
            val y = PIVOT_Y - distance * cos(angle).toFloat()
            val star = starPath(cx = x, cy = y, outer = sparkSize, inner = sparkSize * SPARK_WAIST, points = 4)
            drawPath(star, colors[spark % 2], alpha = alpha)
        }
    }

    // the colour as it looks at this frame's saturation: grey at 0, full colour at 1
    private fun tint(color: Color): Color {
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
private const val SPARK_START_SIZE = 1.5f
private const val SPARK_END_SIZE = 5.5f
private const val SPARK_WAIST = 0.3f
private val SparkGold = Color(0xFFFFE27A)
// how far the gold sparks and the coloured sparks reach, as a share of SPARK_DISTANCE
private val SparkReach = floatsOf("1 0.72")
private val ShinePath = svgPath(SHINE_PATH)
