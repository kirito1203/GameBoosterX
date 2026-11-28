package com.umbral.codex.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbral.codex.data.SaveStatus
import com.umbral.codex.model.Character
import com.umbral.codex.model.RollRecord
import com.umbral.codex.model.SheetSection
import com.umbral.codex.ui.LocalAppState
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.components.ConfirmDialog
import com.umbral.codex.ui.components.EmptyState
import com.umbral.codex.ui.components.Portrait
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.ScreenHeader
import com.umbral.codex.ui.components.displayName
import com.umbral.codex.ui.components.subtitleLine
import com.umbral.codex.ui.deleteCharacter
import com.umbral.codex.ui.duplicateCharacter
import com.umbral.codex.ui.settings.rememberCharacterExporter
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.toggleFavorite
import kotlinx.coroutines.launch

@Composable
fun SheetScreen(id: String) {
    val vm = LocalAppState.current
    val repo = LocalRepository.current
    val chars by repo.characters.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val status by repo.status.collectAsStateWithLifecycle()
    val character = chars.firstOrNull { it.id == id }
    val haptics = LocalHapticFeedback.current
    val popup = remember { mutableStateOf<RollRecord?>(null) }
    val controller = remember(id, settings) { SheetController(id, repo, vm, settings, popup, haptics) }

    LaunchedEffect(id) { repo.markOpened(id) }

    if (character == null) {
        Column { ScreenHeader("Ficha", onBack = { vm.back() }); EmptyState("Ficha não encontrada", "Ela pode ter sido excluída.") }
        return
    }

    val sections = SheetSection.entries.filter { !it.canHide || it.key !in character.hiddenSections }
    val pager = rememberPagerState { sections.size }
    val scope = rememberCoroutineScope()
    val c = Codex.colors

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SheetHeader(character, status, onBack = { vm.back() })
            ScrollableTabRow(
                selectedTabIndex = pager.currentPage.coerceAtMost(sections.lastIndex),
                containerColor = c.abyss,
                contentColor = c.text,
                edgePadding = 12.dp,
                divider = { Spacer(Modifier.fillMaxWidth()) },
                indicator = { positions ->
                    val i = pager.currentPage.coerceAtMost(positions.lastIndex)
                    if (i >= 0) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[i]), color = c.gold)
                },
            ) {
                sections.forEachIndexed { i, s ->
                    Tab(
                        selected = pager.currentPage == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        text = {
                            Text(
                                s.label, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                                color = if (pager.currentPage == i) c.text else c.muted,
                            )
                        },
                    )
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, key = { sections[it].key }) { page ->
                when (sections[page]) {
                    SheetSection.SHEET -> SheetTab(character, controller)
                    SheetSection.ATTRIBUTES -> AttributesTab(character, controller)
                    SheetSection.SKILLS -> SkillsTab(character, controller)
                    SheetSection.MAGIC -> MagicTab(character, controller)
                    SheetSection.POWERS -> PowersTab(character, controller)
                    SheetSection.INVENTORY -> InventoryTab(character, controller)
                    SheetSection.DICE -> DiceTab(character, controller)
                    SheetSection.STORY -> StoryTab(character, controller)
                    SheetSection.OTHER -> OtherTab(character, controller)
                }
            }
        }
        RollPopup(popup, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun SheetHeader(c: Character, status: SaveStatus, onBack: () -> Unit) {
    val vm = LocalAppState.current
    val col = Codex.colors
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val export = rememberCharacterExporter()
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack, tint = col.text)
        Portrait(c.portraitId, c.displayName(), Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.displayName(), style = MaterialTheme.typography.titleLarge, color = col.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${c.subtitleLine()} · Nv. ${c.level}", style = MaterialTheme.typography.bodySmall, color = col.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SaveIndicator(status)
        RoundIconButton(
            if (c.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (c.favorite) "Remover dos favoritos" else "Favoritar",
            { vm.toggleFavorite(c.id) }, tint = if (c.favorite) col.danger else col.muted,
        )
        Box {
            RoundIconButton(Icons.Filled.MoreVert, "Mais ações", { menu = true }, tint = col.text)
            DropdownMenu(menu, { menu = false }, containerColor = col.panelHigh) {
                DropdownMenuItem({ Text("Duplicar ficha") }, { menu = false; vm.duplicateCharacter(c.id) }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) })
                DropdownMenuItem({ Text("Exportar ficha (.json)") }, { menu = false; export(c.id) }, leadingIcon = { Icon(Icons.Filled.FileUpload, null) })
                DropdownMenuItem({ Text("Excluir ficha", color = col.danger) }, { menu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = col.danger) })
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Excluir ficha?", "“${c.displayName()}” será apagada permanentemente, com magias, itens e história.", "Excluir",
            { confirmDelete = false; vm.back(); vm.deleteCharacter(c.id) }, { confirmDelete = false }, destructive = true,
        )
    }
}

/** Indicador de salvamento automático. */
@Composable
private fun SaveIndicator(status: SaveStatus) {
    val c = Codex.colors
    val (icon, tint, text) = when (status) {
        is SaveStatus.Failed -> Triple(Icons.Filled.CloudOff, c.danger, "Erro ao salvar")
        SaveStatus.Pending, SaveStatus.Saving -> Triple(Icons.Filled.CloudSync, c.warning, "Salvando…")
        else -> Triple(Icons.Filled.CloudDone, c.success, "Salvo")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(icon, text, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
