package com.carlos.miflujo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility

internal enum class MainDestination(
    val label: String,
    val icon: ImageVector,
) {
    Home(label = "Inicio", icon = Icons.Filled.Home),
    Movements(label = "Movimientos", icon = Icons.AutoMirrored.Filled.ReceiptLong),
    Report(label = "Reporte", icon = Icons.Filled.Assessment),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MiFlujoScaffold(
    selectedDestination: MainDestination,
    onSelectDestination: (MainDestination) -> Unit,
    showSettings: Boolean,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onAdd: (() -> Unit)?,
    title: String = "MiFlujo",
    settingsTitle: String = title,
    snackbarHost: @Composable () -> Unit = {},
    settings: @Composable (Modifier) -> Unit,
    content: @Composable (MainDestination) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (showSettings) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier,
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = title)
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Ajustes",
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    MainDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedDestination == destination,
                            onClick = { onSelectDestination(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = {
                                Text(text = destination.label)
                            },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (onAdd != null) {
                    ExtendedFloatingActionButton(onClick = onAdd) { Text("+ Agregar") }
                }
            },
            snackbarHost = snackbarHost,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                content(selectedDestination)
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                },
            enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 200),
                    initialOffsetX = { width -> width / 12 },
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                slideOutHorizontally(
                    animationSpec = tween(durationMillis = 180),
                    targetOffsetX = { width -> width / 12 },
                ),
            label = "Settings transition",
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onCloseSettings) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Volver",
                                )
                            }
                        },
                        title = {
                            Text(text = settingsTitle)
                        },
                    )
                },
            ) { innerPadding ->
                settings(Modifier.padding(innerPadding))
            }
        }
    }
}
