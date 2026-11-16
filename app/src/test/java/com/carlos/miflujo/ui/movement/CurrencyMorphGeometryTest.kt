package com.carlos.miflujo.ui.movement

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class CurrencyMorphGeometryTest {
    private fun outline(vararg points: Pair<Float, Float>) = GlyphOutline(listOf(points.map { GlyphPoint(it.first, it.second) }))
    private val c = outline(10f to 0f, 0f to 0f, 0f to 14f, 10f to 14f, 10f to 11f, 3f to 11f, 3f to 3f, 10f to 3f)
    private val u = outline(0f to 0f, 3f to 0f, 3f to 11f, 7f to 11f, 7f to 0f, 10f to 0f, 10f to 14f, 0f to 14f)
    private val s = outline(0f to 0f, 8f to 0f, 8f to 14f, 0f to 14f)
    private val dollar = GlyphOutline(listOf(
        listOf(GlyphPoint(1f, -1f), GlyphPoint(7f, -1f), GlyphPoint(7f, 15f), GlyphPoint(1f, 15f)),
        listOf(GlyphPoint(2f, 3f), GlyphPoint(6f, 3f), GlyphPoint(6f, 9f), GlyphPoint(2f, 9f)),
    ))
    private fun model() = prepareCurrencyMorph(
        c.translate(10f, 16f), u.translate(5f, 16f), s.translate(18f, 16f), dollar,
        GlyphPoint(22f, 16f), GlyphPoint(30f, 16f),
    )!!

    @Test fun `winding and cyclic starts are matched without changing the glyph shape`() {
        val shiftedU = GlyphOutline(listOf(u.contours.single().reversed().let { it.drop(3) + it.take(3) }))
        val matched = matchGlyphContours(c, shiftedU)!!
        assertEquals(matched.from.size, matched.to.size)
        assertTrue(contourArea(matched.from) * contourArea(matched.to) > 0f)
        assertEquals(0f, outlineBounds(listOf(GlyphOutline(listOf(matched.to)))).left, 0.001f)
        assertEquals(14f, outlineBounds(listOf(GlyphOutline(listOf(matched.to)))).bottom, 0.001f)
    }

    @Test fun `unsupported holes in C or U are rejected rather than paired arbitrarily`() {
        assertNull(matchGlyphContours(c.copy(contours = c.contours + s.contours), u))
        assertNull(matchGlyphContours(c, u.copy(contours = emptyList())))
    }

    @Test fun `matching terminal caps keeps a stroke instead of a collapsed solid blob`() {
        val matched = matchGlyphContours(c, u)!!
        val startArea = abs(contourArea(matched.from))
        val endArea = abs(contourArea(matched.to))
        for (step in 0..100) {
            val points = matched.from.zip(matched.to) { a, b -> a.interpolate(b, step / 100f) }
            val area = abs(contourArea(points))
            assertTrue(area >= minOf(startArea, endArea) * 0.5f)
            assertTrue(area <= maxOf(startArea, endArea) * 1.5f)
        }
    }

    @Test fun `normalized endpoints stay within the resampling error of the source contours`() {
        val match = matchGlyphContours(c, u)!!
        listOf(c.contours.single() to match.from, u.contours.single() to match.to).forEach { (source, normalized) ->
            for (i in source.indices) {
                val start = source[i]
                val end = source[(i + 1) % source.size]
                for (step in 0..20) {
                    val point = start.interpolate(end, step / 20f)
                    val error = normalized.indices.minOf { j -> distanceToSegment(point, normalized[j], normalized[(j + 1) % normalized.size]) }
                    assertTrue("Resampling error $error", error <= 0.071f)
                }
            }
        }
    }

    @Test fun `endpoint positions regroup to measured layout and dollar holes retain their shape`() {
        val model = model()
        val start = currencyMorphOutlines(model, 0f, 56f, 48f, 2f)
        val end = currencyMorphOutlines(model, 1f, 56f, 48f, 2f)
        assertEquals(2, start.size)
        assertEquals(3, end.size)
        assertEquals(model.startCenter.x, outlineBounds(start).center.x, 0.001f)
        assertEquals(model.endCenter.x, outlineBounds(end).center.x, 0.001f)
        assertEquals(8f, outlineBounds(listOf(end[1])).width, 0.001f)
        for (step in 0..100) {
            val frame = currencyMorphOutlines(model, step / 100f, 56f, 48f, 2f)
            val rendered = frame.last()
            assertEquals(dollar.contours.map { it.size }, rendered.contours.map { it.size })
            val origin = rendered.contours.first().first()
            val original = dollar.contours.first().first()
            rendered.points.zip(dollar.points).forEach { (a, b) ->
                assertEquals(b.x - original.x, a.x - origin.x, 0.0001f)
                assertEquals(b.y - original.y, a.y - origin.y, 0.0001f)
            }
        }
    }

    @Test fun `separation occurs only during the transition and respects available space`() {
        val model = model()
        assertEquals(currencyMorphOutlines(model, 0f, 56f, 48f, 0f), currencyMorphOutlines(model, 0f, 56f, 48f, 2f))
        assertEquals(currencyMorphOutlines(model, 1f, 56f, 48f, 0f), currencyMorphOutlines(model, 1f, 56f, 48f, 2f))
        val compact = outlineBounds(currencyMorphOutlines(model, 0.5f, 56f, 48f, 0f))
        val separated = outlineBounds(currencyMorphOutlines(model, 0.5f, 56f, 48f, 2f))
        assertTrue(separated.width > compact.width)
        assertEquals(compact.center.x, separated.center.x, 0.001f)
        for (step in 0..100) {
            val bounds = outlineBounds(currencyMorphOutlines(model, step / 100f, 40f, 48f, 20f))
            assertTrue(bounds.left >= -0.001f && bounds.right <= 40.001f)
            assertTrue(bounds.top >= -0.001f && bounds.bottom <= 48.001f)
        }
    }

    @Test fun `interruption samples are continuous and bounded spring overshoot cannot invert S`() {
        val model = model()
        assertEquals(currencyMorphOutlines(model, 0f, 56f, 48f, 2f), currencyMorphOutlines(model, -0.01f, 56f, 48f, 2f))
        assertEquals(currencyMorphOutlines(model, 1f, 56f, 48f, 2f), currencyMorphOutlines(model, 1.01f, 56f, 48f, 2f))
        for (step in 1..999) {
            val a = currencyMorphOutlines(model, step / 1000f, 56f, 48f, 2f).flatMap { it.points }
            val b = currencyMorphOutlines(model, (step + 1) / 1000f, 56f, 48f, 2f).flatMap { it.points }
            a.zip(b).forEach { (p, q) -> assertTrue(hypot(p.x - q.x, p.y - q.y) < 0.1f) }
        }
    }

    private fun distanceToSegment(p: GlyphPoint, a: GlyphPoint, b: GlyphPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = dx * dx + dy * dy
        val t = if (length == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / length).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }
}
