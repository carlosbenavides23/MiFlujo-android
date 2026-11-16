package com.carlos.miflujo.ui.movement

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

internal data class GlyphPoint(val x: Float, val y: Float) {
    fun interpolate(other: GlyphPoint, progress: Float) = GlyphPoint(
        x + (other.x - x) * progress, y + (other.y - y) * progress,
    )
    fun translate(dx: Float, dy: Float) = GlyphPoint(x + dx, y + dy)
}

internal data class GlyphOutline(val contours: List<List<GlyphPoint>>) {
    fun translate(dx: Float, dy: Float) = GlyphOutline(contours.map { contour -> contour.map { it.translate(dx, dy) } })
    val points: List<GlyphPoint> get() = contours.flatten()
}

internal data class CurrencyMorphBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: GlyphPoint get() = GlyphPoint((left + right) / 2f, (top + bottom) / 2f)
}

internal fun outlineBounds(outlines: List<GlyphOutline>): CurrencyMorphBounds {
    val points = outlines.flatMap { it.points }
    return CurrencyMorphBounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
}

internal fun contourArea(points: List<GlyphPoint>): Float = points.indices.sumOf { i ->
    val a = points[i]
    val b = points[(i + 1) % points.size]
    (a.x * b.y - b.x * a.y).toDouble()
}.toFloat() / 2f

private fun distance(a: GlyphPoint, b: GlyphPoint) = hypot(b.x - a.x, b.y - a.y)
private fun polylineLength(points: List<GlyphPoint>) = (0 until points.lastIndex).sumOf { i ->
    distance(points[i], points[i + 1]).toDouble()
}.toFloat()

private fun resampleArc(points: List<GlyphPoint>, count: Int): List<GlyphPoint> {
    val lengths = (0 until points.lastIndex).map { distance(points[it], points[it + 1]) }
    val total = lengths.sum()
    var edge = 0
    var edgeStart = 0f
    return List(count) { index ->
        val at = total * index / (count - 1)
        while (edge < lengths.lastIndex && edgeStart + lengths[edge] < at) edgeStart += lengths[edge++]
        points[edge].interpolate(points[edge + 1], ((at - edgeStart) / lengths[edge]).coerceIn(0f, 1f))
    }
}

private data class StrokeEdges(val outer: List<GlyphPoint>, val inner: List<GlyphPoint>)

/** Recognize the two terminal edges from actual outlines, not hand-drawn glyph coordinates. */
private fun strokeEdges(outline: GlyphOutline, isC: Boolean): StrokeEdges? {
    val points = outline.contours.single()
    val bounds = outlineBounds(listOf(outline))
    fun cap(first: Boolean): Int? = points.indices.filter { i ->
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val midpoint = a.interpolate(b, 0.5f)
        if (isC) {
            midpoint.x > bounds.left + bounds.width * 0.65f &&
                (if (first) midpoint.y < bounds.center.y else midpoint.y > bounds.center.y)
        } else {
            midpoint.y < bounds.top + bounds.height * 0.15f &&
                abs(b.y - a.y) < abs(b.x - a.x) * 0.25f &&
                (if (first) midpoint.x < bounds.left + bounds.width * 0.35f
                 else midpoint.x > bounds.left + bounds.width * 0.65f)
        }
    }.maxByOrNull { distance(points[it], points[(it + 1) % points.size]) }
    val a = cap(true) ?: return null
    val b = cap(false) ?: return null
    if (a == b) return null
    if (listOf(a, b).any { distance(points[it], points[(it + 1) % points.size]) > minOf(bounds.width, bounds.height) * 0.45f }) return null
    fun arc(start: Int, end: Int): List<GlyphPoint> {
        val result = mutableListOf(points[start])
        var i = start
        while (i != end) {
            i = (i + 1) % points.size
            result += points[i]
        }
        return result
    }
    val forward = arc((a + 1) % points.size, b)
    val backward = arc((b + 1) % points.size, a)
    if (forward.size < 3 || backward.size < 3) return null
    // Canonical traversal: first terminal -> outer edge -> second terminal -> inner edge.
    return if (polylineLength(forward) >= polylineLength(backward)) StrokeEdges(forward, backward)
    else StrokeEdges(backward.reversed(), forward.reversed())
}

internal data class MatchedGlyphContours(val from: List<GlyphPoint>, val to: List<GlyphPoint>)

/** Match terminal caps and each wall separately so the counter does not collapse mid-morph. */
internal fun matchGlyphContours(from: GlyphOutline, to: GlyphOutline): MatchedGlyphContours? {
    if (from.contours.size != 1 || to.contours.size != 1) return null
    val a = from.contours.single()
    val b = to.contours.single()
    if (a.size < 3 || b.size < 3 || abs(contourArea(a)) < 0.001f || abs(contourArea(b)) < 0.001f) return null
    val c = strokeEdges(from, isC = true) ?: return null
    val u = strokeEdges(to, isC = false) ?: return null
    fun count(p: List<GlyphPoint>, q: List<GlyphPoint>) =
        maxOf(16, ceil(maxOf(polylineLength(p), polylineLength(q)) / 0.14f).toInt() + 1)
    val outerCount = count(c.outer, u.outer)
    val innerCount = count(c.inner, u.inner)
    if (outerCount + innerCount > 4096) return null
    // Native flattening contributes <= 0.03 px; wall resampling adds <= 0.07 px.
    return MatchedGlyphContours(
        resampleArc(c.outer, outerCount) + resampleArc(c.inner, innerCount),
        resampleArc(u.outer, outerCount) + resampleArc(u.inner, innerCount),
    )
}

internal data class CurrencyMorphModel(
    val leading: MatchedGlyphContours,
    val s: GlyphOutline,
    val dollar: GlyphOutline,
    val dollarStart: GlyphPoint,
    val dollarEnd: GlyphPoint,
    val emergence: GlyphPoint,
    val startCenter: GlyphPoint,
    val endCenter: GlyphPoint,
)

internal fun prepareCurrencyMorph(
    c: GlyphOutline, u: GlyphOutline, s: GlyphOutline, dollar: GlyphOutline,
    dollarStart: GlyphPoint, dollarEnd: GlyphPoint,
): CurrencyMorphModel? {
    val leading = matchGlyphContours(c, u) ?: return null
    val startDollar = dollar.translate(dollarStart.x, dollarStart.y)
    val endDollar = dollar.translate(dollarEnd.x, dollarEnd.y)
    val cBounds = outlineBounds(listOf(c))
    val dollarBounds = outlineBounds(listOf(startDollar))
    return CurrencyMorphModel(
        leading, s, dollar, dollarStart, dollarEnd,
        GlyphPoint((cBounds.right + dollarBounds.left) / 2f, outlineBounds(listOf(s)).center.y),
        outlineBounds(listOf(c, startDollar)).center,
        outlineBounds(listOf(u, s, endDollar)).center,
    )
}

/** Gap is transient. Endpoints use measured typographic positions without manual tracking. */
internal fun currencyMorphOutlines(
    model: CurrencyMorphModel, progress: Float, width: Float, height: Float, maxSpread: Float,
): List<GlyphOutline> {
    val t = progress.coerceIn(0f, 1f)
    val leading = GlyphOutline(listOf(model.leading.from.zip(model.leading.to) { a, b -> a.interpolate(b, t) }))
    val middle = GlyphOutline(model.s.contours.map { contour -> contour.map { model.emergence.interpolate(it, t) } })
    val dollarPosition = model.dollarStart.interpolate(model.dollarEnd, t)
    val dollar = model.dollar.translate(dollarPosition.x, dollarPosition.y)
    val base = if (t == 0f) listOf(leading, dollar) else listOf(leading, middle, dollar)
    val baseBounds = outlineBounds(base)
    val desiredCenter = model.startCenter.interpolate(model.endCenter, t)
    val slack = minOf(desiredCenter.x, width - desiredCenter.x) - baseBounds.width / 2f
    val spread = minOf(maxSpread, slack.coerceAtLeast(0f)) * 4f * t * (1f - t)
    val expanded = if (t == 0f) base else listOf(leading.translate(-spread, 0f), middle, dollar.translate(spread, 0f))
    val bounds = outlineBounds(expanded)
    // Follow the original text's optical offset at either end instead of snapping to ink centering.
    val cx = if (bounds.width <= width) desiredCenter.x.coerceIn(bounds.width / 2f, width - bounds.width / 2f) else desiredCenter.x
    val cy = if (bounds.height <= height) desiredCenter.y.coerceIn(bounds.height / 2f, height - bounds.height / 2f) else desiredCenter.y
    return expanded.map { it.translate(cx - bounds.center.x, cy - bounds.center.y) }
}
