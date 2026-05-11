package com.carlos.miflujo.ui.movement

data class MovementFeedback(
    val message: String,
    val type: MovementFeedbackType,
)

enum class MovementFeedbackType {
    SUCCESS,
    ERROR,
}
