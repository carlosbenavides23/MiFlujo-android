package com.carlos.miflujo.cataloglab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable

@Composable
internal fun CatalogIcon(
    key: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Icon(painterResource(resolveCatalogIcon(key).resource), description, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogIconPicker(selectedKey: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<IconGroup?>(null) }
    val matches = remember(query, group) { searchCatalogIcons(query, group) }
    val selected = resolveCatalogIcon(selectedKey).key
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Elegir icono", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(),
                label = { Text("Buscar iconos") }, singleLine = true,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(group == null, { group = null }, label = { Text("Todos") }) }
                items(IconGroup.entries) { option ->
                    FilterChip(group == option, { group = option }, label = { Text(option.label) })
                }
            }
            TextButton(onClick = { onSelect(DEFAULT_CATALOG_ICON) }) { Text("Usar icono predeterminado") }
            if (matches.isEmpty()) {
                Text("No se encontraron iconos.", Modifier.padding(bottom = 24.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(matches, key = { it.key }) { option ->
                        val isSelected = selected == option.key
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.selectable(isSelected, role = Role.RadioButton) { onSelect(option.key) },
                        ) {
                            Column(
                                Modifier.heightIn(min = 76.dp).padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CatalogIcon(option.key)
                                Text(option.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
