package com.carlos.miflujo.ui.movement

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val openDurationMillis = 260
private const val closeDurationMillis = 165
private const val pressDurationMillis = 48

internal class CategoryPickerMotion(
    private val expanded: Boolean,
    private val motionDisabled: Boolean,
    private val visibility: MutableTransitionState<Boolean>,
    private val playhead: State<Float>,
) {
    private val rebound = FloatSpringSpec(dampingRatio = 0.72f, stiffness = 1400f)

    val isVisible: Boolean
        get() = expanded || (!motionDisabled && (visibility.currentState || !visibility.isIdle))

    val progress: Float
        get() = if (motionDisabled) {
            if (expanded) 1f else 0f
        } else playhead.value.coerceIn(0f, 1f)

    fun buttonReaction(): Modifier = if (motionDisabled) Modifier else Modifier.graphicsLayer {
        val phase = progress
        val millis = phase * openDurationMillis
        val compression = if (millis < pressDurationMillis) {
            LinearOutSlowInEasing.transform(millis / pressDurationMillis)
        } else {
            val recoveryNanos = ((millis - pressDurationMillis) * 1_000_000L).toLong()
            rebound.getValueFromNanos(recoveryNanos, 1f, 0f, 0f) *
                ((1f - phase) / 0.08f).coerceAtMost(1f)
        }

        scaleX = 1f - 0.017f * compression
        scaleY = 1f - 0.035f * compression
        rotationZ = 1.4f * compression
        translationX = 1.dp.toPx() * compression
    }
}

@Composable
internal fun rememberCategoryPickerMotion(expanded: Boolean): CategoryPickerMotion {
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val motionDisabled = durationScale?.scaleFactor == 0f
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    val transition = rememberTransition(visibility, label = "CategoryPicker")
    val playhead = transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) openDurationMillis else closeDurationMillis,
                easing = LinearEasing,
            )
        },
        label = "Progress",
    ) { visible -> if (visible) 1f else 0f }

    return remember(expanded, motionDisabled, visibility, playhead) {
        CategoryPickerMotion(expanded, motionDisabled, visibility, playhead)
    }
}
