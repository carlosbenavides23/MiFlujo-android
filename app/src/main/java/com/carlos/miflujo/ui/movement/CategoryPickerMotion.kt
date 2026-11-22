package com.carlos.miflujo.ui.movement

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale

private const val openDurationMillis = 200
private const val closeDurationMillis = 165

internal class CategoryPickerMotion(
    private val expanded: Boolean,
    private val motionDisabled: Boolean,
    private val visibility: MutableTransitionState<Boolean>,
    private val playhead: State<Float>,
) {
    val isVisible: Boolean
        get() = expanded || (!motionDisabled && (visibility.currentState || !visibility.isIdle))

    val progress: Float
        get() = if (motionDisabled) {
            if (expanded) 1f else 0f
        } else playhead.value.coerceIn(0f, 1f)
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
