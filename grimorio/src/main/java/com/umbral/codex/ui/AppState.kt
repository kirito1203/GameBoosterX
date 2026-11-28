package com.umbral.codex.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.umbral.codex.CodexApplication
import com.umbral.codex.data.CodexRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

val LocalRepository = staticCompositionLocalOf<CodexRepository> { error("Repositório não fornecido") }
val LocalAppState = staticCompositionLocalOf<AppViewModel> { error("Estado do app não fornecido") }

/** Destinos do app. As cinco primeiras são as abas da barra inferior. */
sealed interface Route {
    data object Home : Route
    data object Catalogs : Route
    data object Library : Route
    data class Create(val catalogId: String? = null, val templateId: String? = null) : Route
    data object Settings : Route
    data class CatalogDetail(val id: String) : Route
    data class Sheet(val id: String) : Route
}

enum class RootTab(val route: Route, val label: String) {
    HOME(Route.Home, "Início"),
    CATALOGS(Route.Catalogs, "Catálogos"),
    CREATE(Route.Create(), "Criar"),
    LIBRARY(Route.Library, "Biblioteca"),
    SETTINGS(Route.Settings, "Ajustes"),
}

/**
 * Navegação com pilha própria (sem dependências extras) e mensagens de feedback (snackbar).
 * Sobrevive a rotações por ser um ViewModel.
 */
class AppViewModel(val repository: CodexRepository) : ViewModel() {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun open(route: Route) { stack.add(route) }

    fun openTab(tab: RootTab) {
        stack.clear(); stack.add(tab.route)
    }

    /** Substitui o destino atual (ex.: tela Criar → ficha recém-criada). */
    fun replace(route: Route) {
        if (stack.size <= 1) {
            // Criada a partir da aba "Criar": a ficha abre sobre a Biblioteca (voltar leva até ela).
            stack.clear(); stack.add(Route.Library); stack.add(route); return
        }
        stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    fun back(): Boolean {
        if (stack.size <= 1) {
            if (current != Route.Home) { stack.clear(); stack.add(Route.Home); return true }
            return false
        }
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun toast(message: String) { viewModelScope.launch { _messages.send(message) } }

    fun activeTab(): RootTab = when (stack.firstOrNull()) {
        Route.Catalogs -> RootTab.CATALOGS
        Route.Library -> RootTab.LIBRARY
        is Route.Create -> RootTab.CREATE
        Route.Settings -> RootTab.SETTINGS
        else -> RootTab.HOME
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel((this[APPLICATION_KEY] as CodexApplication).repository) }
        }
    }
}
