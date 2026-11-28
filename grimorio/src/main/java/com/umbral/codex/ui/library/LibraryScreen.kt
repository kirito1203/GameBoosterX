package com.umbral.codex.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbral.codex.model.LibrarySort
import com.umbral.codex.ui.LocalAppState
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.RootTab
import com.umbral.codex.ui.Route
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.CharacterCard
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.ConfirmDialog
import com.umbral.codex.ui.components.EmptyState
import com.umbral.codex.ui.components.FilterPill
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.ScreenHeader
import com.umbral.codex.ui.components.displayName
import com.umbral.codex.ui.deleteCharacter
import com.umbral.codex.ui.duplicateCharacter
import com.umbral.codex.ui.settings.rememberCharacterExporter
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.toggleFavorite
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

private const val ALL = "__all"
private const val FAVORITES = "__fav"
private const val NONE = "__none"

@Composable
fun LibraryScreen() {
    val vm = LocalAppState.current
    val repo = LocalRepository.current
    val chars by repo.characters.collectAsStateWithLifecycle()
    val catalogs by repo.catalogs.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val c = Codex.colors
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ALL) }
    var sortMenu by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<String?>(null) }
    val export = rememberCharacterExporter()
    val catById = catalogs.associateBy { it.id }
    val sort = settings.librarySort

    val visible = chars
        .filter { ch ->
            when (filter) {
                ALL -> true
                FAVORITES -> ch.favorite
                NONE -> ch.catalogId == null || catById[ch.catalogId] == null
                else -> ch.catalogId == filter
            }
        }
        .filter { ch ->
            query.isBlank() || listOf(ch.name, ch.race, ch.charClass, ch.title, ch.faction).any { it.contains(query.trim(), ignoreCase = true) }
        }
        .let { list ->
            when (sort) {
                LibrarySort.NAME -> list.sortedBy { it.displayName().lowercase() }
                LibrarySort.LEVEL -> list.sortedWith(compareByDescending<com.umbral.codex.model.Character> { it.level }.thenBy { it.name.lowercase() })
                LibrarySort.EDITED -> list.sortedByDescending { it.updatedAt }
                LibrarySort.CREATED -> list.sortedByDescending { it.createdAt }
            }
        }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Biblioteca", "${chars.size} ${if (chars.size == 1) "ficha" else "fichas"} no códice") {
            Box {
                RoundIconButton(Icons.AutoMirrored.Filled.Sort, "Ordenar", { sortMenu = true }, tint = c.text)
                DropdownMenu(sortMenu, { sortMenu = false }, containerColor = c.panelHigh) {
                    LibrarySort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("Ordenar por ${s.label.lowercase()}", color = if (s == sort) c.accent else c.text) },
                            onClick = { repo.updateSettings { it.copy(librarySort = s) }; sortMenu = false },
                        )
                    }
                }
            }
            RoundIconButton(Icons.Filled.Add, "Nova ficha", { vm.openTab(RootTab.CREATE) }, tint = c.accent)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            CodexTextField(query, { query = it }, "Pesquisar por nome, raça, classe…")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill("Todas", filter == ALL, { filter = ALL })
                FilterPill("Favoritas", filter == FAVORITES, { filter = FAVORITES }, color = c.danger)
                catalogs.sortedBy { it.order }.forEach { cat ->
                    FilterPill(cat.name, filter == cat.id, { filter = cat.id }, color = androidx.compose.ui.graphics.Color(cat.accent))
                }
                FilterPill("Sem categoria", filter == NONE, { filter = NONE })
            }
        }
        if (visible.isEmpty()) {
            EmptyState(
                if (chars.isEmpty()) "Nenhuma ficha ainda" else "Nada encontrado",
                if (chars.isEmpty()) "Crie seu primeiro personagem." else "Tente outra pesquisa ou filtro.",
            ) {
                if (chars.isEmpty()) ArcaneButton("Criar ficha", { vm.openTab(RootTab.CREATE) }, icon = Icons.Filled.Add)
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { ch ->
                CharacterCard(
                    ch, catById[ch.catalogId],
                    onClick = { vm.open(Route.Sheet(ch.id)) },
                    onToggleFavorite = { vm.toggleFavorite(ch.id) },
                    compact = settings.compactLibrary,
                    trailing = {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            RoundIconButton(Icons.Filled.MoreVert, "Mais ações", { menu = true })
                            DropdownMenu(menu, { menu = false }, containerColor = c.panelHigh) {
                                DropdownMenuItem({ Text("Editar") }, { menu = false; vm.open(Route.Sheet(ch.id)) }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                                DropdownMenuItem({ Text("Duplicar") }, { menu = false; vm.duplicateCharacter(ch.id) }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) })
                                DropdownMenuItem({ Text("Exportar ficha") }, { menu = false; export(ch.id) }, leadingIcon = { Icon(Icons.Filled.FileUpload, null) })
                                DropdownMenuItem({ Text("Excluir", color = c.danger) }, { menu = false; toDelete = ch.id }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = c.danger) })
                            }
                        }
                    },
                )
            }
        }
    }

    toDelete?.let { id ->
        val name = chars.firstOrNull { it.id == id }?.displayName() ?: ""
        ConfirmDialog(
            "Excluir ficha?", "“$name” e todo o seu conteúdo serão apagados permanentemente. Considere exportar antes.",
            "Excluir", { vm.deleteCharacter(id); toDelete = null }, { toDelete = null }, destructive = true,
        )
    }
}
