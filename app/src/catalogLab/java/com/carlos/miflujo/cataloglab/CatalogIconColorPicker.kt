package com.carlos.miflujo.cataloglab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.category.CatalogIconColor

internal data class IconSwatch(val label: String, val reference: Color)

internal val iconSwatches = mapOf(
    CatalogIconColor.RED to IconSwatch("Rojo", Color(0xFFD65C62)),
    CatalogIconColor.ORANGE to IconSwatch("Naranja", Color(0xFFD98748)),
    CatalogIconColor.AMBER to IconSwatch("Ámbar", Color(0xFFC69A38)),
    CatalogIconColor.GREEN to IconSwatch("Verde", Color(0xFF4C9B6B)),
    CatalogIconColor.MINT to IconSwatch("Menta", Color(0xFF4A9F92)),
    CatalogIconColor.CYAN to IconSwatch("Celeste", Color(0xFF479CC4)),
    CatalogIconColor.BLUE to IconSwatch("Azul", Color(0xFF5D80D5)),
    CatalogIconColor.VIOLET to IconSwatch("Violeta", Color(0xFF9272C9)),
    CatalogIconColor.PINK to IconSwatch("Rosa", Color(0xFFC67AA8)),
    CatalogIconColor.GRAY to IconSwatch("Gris", Color(0xFF87909B)),
)

internal fun iconContrast(foreground: Color, background: Color): Float {
    val light = maxOf(foreground.luminance(), background.luminance())
    val dark = minOf(foreground.luminance(), background.luminance())
    return (light + 0.05f) / (dark + 0.05f)
}

private fun contrastingInk(background: Color): Color =
    if (iconContrast(Color.Black, background) >= iconContrast(Color.White, background)) Color.Black else Color.White

internal fun catalogTint(reference: Color, background: Color): Color {
    if (iconContrast(reference, background) >= 3f) return reference
    val endpoint = contrastingInk(background)
    var low = 0f
    var high = 1f
    repeat(10) {
        val middle = (low + high) / 2f
        if (iconContrast(lerp(reference, endpoint, middle), background) >= 3f) high = middle else low = middle
    }
    return lerp(reference, endpoint, high)
}

@Composable
internal fun CatalogIconColorPicker(
    color: CatalogIconColor?,
    enabled: Boolean,
    onSelect: (CatalogIconColor?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text("Color del icono", style = MaterialTheme.typography.titleSmall)
        Text("Opcional", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .selectable(color == null, enabled = enabled, role = Role.RadioButton) { onSelect(null) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = color == null, onClick = null, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Text("Usar color predeterminado")
        }
        Spacer(Modifier.height(8.dp))
        CatalogIconColor.entries.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    val swatch = iconSwatches.getValue(option)
                    val selected = color == option
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp)
                            .selectable(selected, enabled = enabled, role = Role.RadioButton) { onSelect(option) }
                            .semantics { contentDescription = swatch.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        val outline = if (selected) colors.onSurface else catalogTint(swatch.reference, colors.surface)
                        Box(
                            Modifier.size(34.dp).border(if (selected) 2.dp else 1.dp, outline, CircleShape)
                                .padding(if (selected) 4.dp else 1.dp)
                                .background(swatch.reference, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = contrastingInk(swatch.reference))
                        }
                    }
                }
            }
        }
    }
}
