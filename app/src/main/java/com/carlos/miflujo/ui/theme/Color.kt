package com.carlos.miflujo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val LightBackground = Color(0xFFFCFCF9)
val LightOnBackground = Color(0xFF1A1C19)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1A1C19)
val LightSurfaceVariant = Color(0xFFEEF1EC)
val LightOnSurfaceVariant = Color(0xFF4F5A53)
val LightOutline = Color(0xFF7D8780)
val LightOutlineVariant = Color(0xFFDDE2DC)
val LightSurfaceDim = Color(0xFFDDDCD8)
val LightSurfaceBright = Color(0xFFFCFCF9)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF7F8F4)
val LightSurfaceContainerHigh = Color(0xFFF1F3EF)
val LightSurfaceContainerHighest = Color(0xFFE9ECE7)

private val FinancePositiveLight = Color(0xFF2E7D4F)
private val FinancePositiveDark = Color(0xFF81C995)
private val FinanceNegativeLight = Color(0xFFB3261E)
private val FinanceNegativeDark = Color(0xFFFFB4AB)

@Composable
fun financePositiveColor(): Color {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        FinancePositiveDark
    } else {
        FinancePositiveLight
    }
}

@Composable
fun financeNegativeColor(): Color {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        FinanceNegativeDark
    } else {
        FinanceNegativeLight
    }
}
