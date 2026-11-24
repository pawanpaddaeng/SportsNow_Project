package com.example.cpen321application.ui.components.badges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*  Small helpers that build the badges' shapes. Every badge is drawn on a 120 × 124 grid, the
    same one as the design mockup, with the badge's centre at (CENTER_X, CENTER_Y). Shapes are
    written as SVG path data where that's shortest, so they match the mockup exactly
 */

const val BADGE_WIDTH = 120f
const val BADGE_HEIGHT = 124f
const val CENTER_X = 60f
const val CENTER_Y = 58f

private const val HALF_TURN_DEGREES = 180f

// a path from SVG path data, e.g. "M42 50 h36"
fun svgPath(data: String): Path = PathParser().parsePathString(data).toPath()

fun circlePath(cx: Float, cy: Float, r: Float): Path =
    Path().apply { addOval(Rect(left = cx - r, top = cy - r, right = cx + r, bottom = cy + r)) }

fun roundRectPath(x: Float, y: Float, width: Float, height: Float, radius: Float): Path =
    svgPath(
        "M${x + radius} $y h${width - 2 * radius} a$radius $radius 0 0 1 $radius $radius " +
            "v${height - 2 * radius} a$radius $radius 0 0 1 -$radius $radius h${2 * radius - width} " +
            "a$radius $radius 0 0 1 -$radius -$radius v${2 * radius - height} a$radius $radius 0 0 1 $radius -$radius Z"
    )

// an ellipse centred on (cx, cy), turned clockwise by rotation degrees
fun ellipsePath(cx: Float, cy: Float, rx: Float, ry: Float, rotation: Float = 0f): Path =
    Path().apply {
        addOval(Rect(left = cx - rx, top = cy - ry, right = cx + rx, bottom = cy + ry))
        transform(Matrix().apply { aroundPoint(cx, cy) { rotateZ(rotation) } })
    }

// a star with points tips, its tips at outer and the dips between them at inner, first tip straight up
fun starPath(cx: Float, cy: Float, outer: Float, inner: Float, points: Int = 5): Path {
    // tips and dips take turns going round
    val radii = listOf(outer, inner)
    val corners = (0 until points * 2).map { corner ->
        val angle = -PI / 2 + corner * PI / points
        val radius = radii[corner % 2]
        Offset(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat())
    }
    return Path().apply {
        moveTo(corners.first().x, corners.first().y)
        corners.drop(1).forEach { corner -> lineTo(corner.x, corner.y) }
        close()
    }
}

// the point radius away from the badge's centre at angle degrees (0 is right, 90 is down)
fun pointOnCircle(radius: Float, angle: Float): Pair<Float, Float> {
    val radians = angle * PI / HALF_TURN_DEGREES
    return (CENTER_X + radius * cos(radians).toFloat()) to (CENTER_Y + radius * sin(radians).toFloat())
}

// this path turned clockwise by degrees around (pivotX, pivotY)
fun Path.rotated(degrees: Float, pivotX: Float, pivotY: Float): Path =
    apply { transform(Matrix().apply { aroundPoint(pivotX, pivotY) { rotateZ(degrees) } }) }

// this path shrunk or grown by scale around (pivotX, pivotY), then moved by (dx, dy)
fun Path.scaled(
    scale: Float,
    pivotX: Float = CENTER_X,
    pivotY: Float = CENTER_Y,
    dx: Float = 0f,
    dy: Float = 0f
): Path =
    apply {
        transform(Matrix().apply {
            translate(x = dx)
            translate(y = dy)
            aroundPoint(pivotX, pivotY) { scale(x = scale, y = scale) }
        })
    }

// applies change around (x, y) instead of around the grid's corner
private fun Matrix.aroundPoint(x: Float, y: Float, change: Matrix.() -> Unit) {
    translate(x, y)
    change()
    translate(-x, -y)
}

// numbers written in a string, e.g. "212 189 166", for lists of angles or keyframes
fun floatsOf(numbers: String): List<Float> = numbers.trim().split(Regex("\\s+")).map { it.toFloat() }
