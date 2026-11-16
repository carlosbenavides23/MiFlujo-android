package com.carlos.miflujo.ui.movement

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnitType
import kotlin.math.abs

private data class PositionedGlyph(val outline: GlyphOutline, val position: GlyphPoint) {
    fun placed() = outline.translate(position.x, position.y)
}

/** Android adapter only: the matching/interpolation/layout code stays independent of Android. */
internal fun createCurrencyMorphModel(
    style: TextStyle, typeface: Typeface, density: Density,
    cordoba: TextLayoutResult, dollar: TextLayoutResult,
    cordobaOrigin: IntOffset, dollarOrigin: IntOffset,
): CurrencyMorphModel? {
    if (cordoba.hasVisualOverflow || dollar.hasVisualOverflow) return null
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        hinting = Paint.HINTING_ON
        textSize = with(density) { style.fontSize.toPx() }
        textAlign = Paint.Align.LEFT
        textScaleX = style.textGeometricTransform?.scaleX ?: 1f
        textSkewX = style.textGeometricTransform?.skewX ?: 0f
        letterSpacing = when (style.letterSpacing.type) {
            TextUnitType.Sp -> with(density) { style.letterSpacing.toPx() } / (textSize * textScaleX)
            TextUnitType.Em -> style.letterSpacing.value
            else -> 0f
        }
        fontFeatureSettings = style.fontFeatureSettings
    }
    val c = readLabel("C$", cordoba, cordobaOrigin, paint) ?: return null
    val us = readLabel("US$", dollar, dollarOrigin, paint) ?: return null
    val startDollarBounds = outlineBounds(listOf(c[1].outline))
    val endDollarBounds = outlineBounds(listOf(us[2].outline))
    // Same typeface and same glyph: retain its contour, only adjust its positioned origin.
    if (abs(startDollarBounds.width - endDollarBounds.width) > 0.1f ||
        abs(startDollarBounds.height - endDollarBounds.height) > 0.1f) return null
    return prepareCurrencyMorph(
        c[0].placed(), us[0].placed(), us[1].placed(), c[1].outline,
        c[1].position,
        us[2].position.translate(endDollarBounds.left - startDollarBounds.left, endDollarBounds.top - startDollarBounds.top),
    )
}

private fun readLabel(
    text: String, layout: TextLayoutResult, origin: IntOffset, paint: Paint,
): List<PositionedGlyph>? {
    // Extract the whole run, preserving shaping/tracking, then assign its contours to glyph cells.
    val path = AndroidPath()
    paint.getTextPath(text, 0, text.length, 0f, 0f, path)
    val contours = flattenFontPath(path) ?: return null
    val advances = List(text.length + 1) { offset ->
        paint.getRunAdvance(text, 0, text.length, 0, text.length, false, offset)
    }
    val groups = List(text.length) { mutableListOf<List<GlyphPoint>>() }
    contours.forEach { contour ->
        val center = outlineBounds(listOf(GlyphOutline(listOf(contour)))).center.x
        val index = text.indices.minBy { i -> abs(center - (advances[i] + advances[i + 1]) / 2f) }
        groups[index] += contour.map { it.translate(-advances[index], 0f) }
    }
    if (groups.any { it.isEmpty() }) return null
    return text.indices.map { index ->
        PositionedGlyph(
            GlyphOutline(groups[index]),
            GlyphPoint(origin.x + layout.getHorizontalPosition(index, true), origin.y + layout.firstBaseline),
        )
    }
}

/** Conic conversion <= 0.01 px + native curve flattening <= 0.02 px, in physical pixels. */
private fun flattenFontPath(path: AndroidPath): List<List<GlyphPoint>>? {
    val result = mutableListOf<List<GlyphPoint>>()
    val iterator = path.asComposePath().iterator(PathIterator.ConicEvaluation.AsQuadratics, 0.01f)
    var contour: AndroidPath? = null
    segments@ while (iterator.hasNext()) {
        val segment = iterator.next()
        val p = segment.points
        when (segment.type) {
            PathSegment.Type.Move -> {
                if (contour != null) return null // Font contours must be closed.
                contour = AndroidPath().apply { moveTo(p[0], p[1]) }
            }
            PathSegment.Type.Line -> contour?.lineTo(p[2], p[3]) ?: return null
            PathSegment.Type.Quadratic -> contour?.quadTo(p[2], p[3], p[4], p[5]) ?: return null
            PathSegment.Type.Cubic -> contour?.cubicTo(p[2], p[3], p[4], p[5], p[6], p[7]) ?: return null
            PathSegment.Type.Close -> {
                val closed = contour ?: return null
                closed.close()
                val approximation = closed.approximate(0.02f)
                val points = mutableListOf<GlyphPoint>()
                for (i in approximation.indices step 3) {
                    val point = GlyphPoint(approximation[i + 1], approximation[i + 2])
                    if (!point.x.isFinite() || !point.y.isFinite()) return null
                    if (points.lastOrNull() != point) points += point
                }
                if (points.size > 1 && points.first() == points.last()) points.removeAt(points.lastIndex)
                if (points.size < 3 || abs(contourArea(points)) < 0.001f) return null
                result += points
                contour = null
            }
            PathSegment.Type.Done -> break@segments
            else -> return null
        }
    }
    return result.takeIf { it.isNotEmpty() && contour == null }
}
