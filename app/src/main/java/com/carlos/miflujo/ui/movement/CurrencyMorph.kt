package com.carlos.miflujo.ui.movement

import android.graphics.Typeface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.model.Currency

@Composable
internal fun CurrencyMorph(currency: Currency, manualProgress: Float? = null) {
    val style = MaterialTheme.typography.titleSmall
    val outlineStyle = LocalTextStyle.current.merge(style).copy(color = Color.Unspecified)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val resolver = LocalFontFamilyResolver.current
    val nativeTypeface = resolver.resolve(
        outlineStyle.fontFamily, outlineStyle.fontWeight ?: FontWeight.Normal,
        outlineStyle.fontStyle ?: FontStyle.Normal, outlineStyle.fontSynthesis ?: FontSynthesis.All,
    ).value as? Typeface
    val measurer = rememberTextMeasurer()
    val foreground = LocalContentColor.current
    val separation = with(density) { 2.dp.toPx() }
    // One reversible spring, with a small threshold so the handoff to Text is not a visible snap.
    val transition = updateTransition(currency, label = "currencyMorph")
    val progress = transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium, visibilityThreshold = 0.0005f) },
        label = "currencyContours",
    ) { if (it == Currency.DOLLAR) 1f else 0f }
    BoxWithConstraints(Modifier.fillMaxSize().clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        val size = IntSize(constraints.maxWidth, constraints.maxHeight)
        val textConstraints = Constraints(maxWidth = size.width, maxHeight = size.height)
        val model = remember(outlineStyle, nativeTypeface, measurer, density, direction, textConstraints) {
            if (nativeTypeface == null) null else {
                val c = measurer.measure("C$", style = outlineStyle, maxLines = 1, constraints = textConstraints)
                val us = measurer.measure("US$", style = outlineStyle, maxLines = 1, constraints = textConstraints)
                createCurrencyMorphModel(
                    outlineStyle, nativeTypeface, density, c, us,
                    Alignment.Center.align(c.size, size, direction), Alignment.Center.align(us.size, size, direction),
                )
            }
        }
        // Keep the whole run's shaping, tracking and baseline, hiding only the morphing prefix.
        // drawText uses Compose's paragraph renderer, including text hinting, for the moving $.
        val dollarText = remember(outlineStyle, nativeTypeface, measurer, density, direction, textConstraints) {
            measurer.measure(
                AnnotatedString("C$", listOf(AnnotatedString.Range(SpanStyle(color = Color.Transparent), 0, 1))),
                style = outlineStyle, maxLines = 1, constraints = textConstraints,
            )
        }
        val dollarTextOrigin = Alignment.Center.align(dollarText.size, size, direction)
        val manual = manualProgress?.coerceIn(0f, 1f)
        val atRest = if (manual == null) !transition.isRunning && transition.currentState == transition.targetState
            else manual == 0f || manual == 1f
        if (model == null || atRest) {
            // Exactly the original composable/style/normal tracking at rest. No forced Text size.
            Text(if (manual?.let { it < 0.5f } ?: (currency == Currency.CORDOBA)) "C$" else "US$", style = style, maxLines = 1)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val outlines = currencyMorphOutlines(model, manual ?: progress.value, size.width.toFloat(), size.height.toFloat(), separation)
                outlines.dropLast(1).forEach { glyph ->
                    drawPath(glyph.toPath(), foreground)
                }
                // Use the same translation as the unchanged dollar contour, including recentering.
                // Do not round: snapping positions would introduce steps on rapid reversals.
                val movingDollar = outlineBounds(listOf(outlines.last()))
                val startDollar = outlineBounds(listOf(model.dollar.translate(model.dollarStart.x, model.dollarStart.y)))
                drawText(
                    dollarText, color = foreground,
                    topLeft = Offset(
                        dollarTextOrigin.x + movingDollar.left - startDollar.left,
                        dollarTextOrigin.y + movingDollar.top - startDollar.top,
                    ),
                )
            }
        }
    }
}

private fun GlyphOutline.toPath() = Path().apply {
    fillType = PathFillType.EvenOdd
    for (contour in contours) {
        if (contour.size < 3) continue
        moveTo(contour.first().x, contour.first().y)
        contour.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
}
