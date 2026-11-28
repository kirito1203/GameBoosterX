package com.umbral.codex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umbral.codex.data.SaveStatus
import com.umbral.codex.ui.AppViewModel
import com.umbral.codex.ui.LocalAppState
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.RootTab
import com.umbral.codex.ui.Route
import com.umbral.codex.ui.catalog.CatalogDetailScreen
import com.umbral.codex.ui.catalog.CatalogsScreen
import com.umbral.codex.ui.components.ArcaneSigil
import com.umbral.codex.ui.create.CreateScreen
import com.umbral.codex.ui.home.HomeScreen
import com.umbral.codex.ui.library.LibraryScreen
import com.umbral.codex.ui.settings.SettingsScreen
import com.umbral.codex.ui.sheet.SheetScreen
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.CodexTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository get() = (application as CodexApplication).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalRepository provides repository) {
                CodexTheme(settings) { AppRoot() }
            }
        }
    }

    /** Garante que nada pendente se perca ao sair do app. */
    override fun onStop() {
        super.onStop()
        val app = application as CodexApplication
        app.appScope.launch { repository.flush() }
    }
}

@Composable
private fun AppRoot() {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
    val repo = LocalRepository.current
    val loaded by repo.loaded.collectAsStateWithLifecycle()
    val warnings by repo.warnings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val c = Codex.colors

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val status by repo.status.collectAsStateWithLifecycle()
    LaunchedEffect(warnings) { warnings.forEach { vm.toast(it) } }
    LaunchedEffect(status) { (status as? SaveStatus.Failed)?.let { vm.toast(it.message) } }

    CompositionLocalProvider(LocalAppState provides vm) {
        BackHandler(enabled = vm.stack.size > 1 || vm.current != Route.Home) { vm.back() }
        Scaffold(
            containerColor = c.void,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(data, containerColor = c.panelHighest, contentColor = c.text, actionColor = c.accent)
                }
            },
            bottomBar = { if (vm.stack.size == 1) CodexBottomBar(vm) },
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding)
                    .background(Brush.verticalGradient(listOf(c.abyss, c.void, c.void)))
                    // Edge-to-edge: o conteúdo sobe com o teclado e não fica sob a barra de navegação.
                    .windowInsetsPadding(if (vm.stack.size > 1) WindowInsets.ime.union(WindowInsets.navigationBars) else WindowInsets.ime)
            ) {
                if (!loaded) LoadingScreen() else {
                    AnimatedContent(
                        targetState = vm.current,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "route",
                    ) { route ->
                        when (route) {
                            Route.Home -> HomeScreen()
                            Route.Catalogs -> CatalogsScreen()
                            Route.Library -> LibraryScreen()
                            is Route.Create -> CreateScreen(route.catalogId, route.templateId)
                            Route.Settings -> SettingsScreen()
                            is Route.CatalogDetail -> CatalogDetailScreen(route.id)
                            is Route.Sheet -> SheetScreen(route.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodexBottomBar(vm: AppViewModel) {
    val c = Codex.colors
    val active = vm.activeTab()
    NavigationBar(containerColor = c.abyss, tonalElevation = 0.dp) {
        RootTab.entries.forEach { tab ->
            val icon = when (tab) {
                RootTab.HOME -> Icons.Filled.Home
                RootTab.CATALOGS -> Icons.Filled.AutoStories
                RootTab.CREATE -> Icons.Filled.AddCircle
                RootTab.LIBRARY -> Icons.Filled.Style
                RootTab.SETTINGS -> Icons.Filled.Settings
            }
            NavigationBarItem(
                selected = tab == active,
                onClick = { vm.openTab(tab) },
                icon = { Icon(icon, tab.label, modifier = Modifier.size(if (tab == RootTab.CREATE) 30.dp else 24.dp)) },
                label = { Text(tab.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.text,
                    selectedTextColor = c.accent,
                    unselectedIconColor = c.muted,
                    unselectedTextColor = c.muted,
                    indicatorColor = c.accentDeep,
                ),
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    val c = Codex.colors
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ArcaneSigil(Modifier.size(140.dp))
        Spacer(Modifier.height(16.dp))
        Text("Abrindo o códice…", style = MaterialTheme.typography.titleMedium, color = c.muted)
    }
}
