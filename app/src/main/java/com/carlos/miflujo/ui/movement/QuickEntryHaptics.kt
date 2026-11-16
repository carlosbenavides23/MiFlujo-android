package com.carlos.miflujo.ui.movement

import android.os.Build
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Semantic haptics only; Android and the device decide whether an effect is available. */
internal class QuickEntryHaptics(private val feedback: HapticFeedback) {
    fun perform(action: Action) {
        val effect = when (action) {
            Action.ToggleType -> HapticFeedbackType.VirtualKey
            Action.ToggleCurrency -> HapticFeedbackType.SegmentFrequentTick
            Action.Added -> HapticFeedbackType.Confirm
            Action.EditStarted -> HapticFeedbackType.SegmentTick
            Action.Deleted -> HapticFeedbackType.SegmentFrequentTick
            // Older Core compatibility falls back to LONG_PRESS; avoid that longer effect.
            Action.ValidationRejected -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackType.Reject
            } else {
                return
            }
        }
        feedback.performHapticFeedback(effect)
    }

    enum class Action { ToggleType, ToggleCurrency, Added, EditStarted, Deleted, ValidationRejected }
}
