package com.umbral.codex.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbral.codex.core.SheetMath
import com.umbral.codex.model.Catalog
import com.umbral.codex.ui.LocalAppState
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.RootTab
import com.umbral.codex.ui.Route
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.ArcaneSigil
import com.umbral.codex.ui.components.CatalogCover
import com.umbral.codex.ui.components.CharacterCard
import com.umbral.codex.ui.components.CharacterTile
import com.umbral.codex.ui.components.EmptyState
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.GlowBar
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.Portrait
import com.umbral.codex.ui.components.RuneDivider
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.displayName
import com.umbral.codex.ui.components.formatDate
import com.umbral.codex.ui.components.subtitleLine
import com.umbral.codex.ui.loadDemo
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont
import com.umbral.codex.ui.toggleFavorite

@Composable
fun HomeScreen() {
    val vm = LocalAppState.current
    val repo = LocalRepository.current
    val chars by repo.characters.collectAsStateWithLifecycle()
    val catalogs by repo.catalogs.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val c = Codex.colors
    val catById = catalogs.associateBy { it.id }

    val continueEditing = chars.maxByOrNull { it.updatedAt }
    val recent = chars.filter { it.lastOpenedAt > 0 }.sortedByDescending { it.lastOpenedAt }.take(10)
    val favorites = chars.filter { it.favorite }.sortedBy { it.name.lowercase() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Hero(onCreate = { vm.openTab(RootTab.CREATE) }, onExplore = { vm.openTab(RootTab.CATALOGS) }) }

        if (chars.isEmpty()) {
            item {
                OrnatePanel(Modifier.padding(16.dp).fillMaxWidth()) {
                    EmptyState(
                        "Seu códice está em branco",
                        "Crie a primeira ficha do zero, a partir de um modelo dos catálogos, ou carregue personagens fictícios de exemplo para conhecer o app.",
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArcaneButton("Criar primeira ficha", { vm.openTab(RootTab.CREATE) }, icon = Icons.Filled.Add)
                            if (settings.offerDemo) GhostButton("Carregar exemplos fictícios", { vm.loadDemo() })
                        }
                    }
                }
            }
        }

        continueEditing?.let { last ->
            item { SectionTitle("Continuar editando", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            item {
                val cat = catById[last.catalogId]
                OrnatePanel(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    accent = cat?.let { Color(it.accent) } ?: c.accent,
                    glow = true,
                    onClick = { vm.open(Route.Sheet(last.id)) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Portrait(last.portraitId, last.displayName(), Modifier.size(72.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(last.displayName(), style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${last.subtitleLine()} · Nível ${last.level}", style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            last.resources.take(2).forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.name, Modifier.width(70.dp), style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    GlowBar(if (r.max == r.min) 0f else (r.current - r.min).toFloat() / (r.max - r.min), Color(r.color), Modifier.weight(1f), height = 7.dp)
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            Text("Editado em ${formatDate(last.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = c.faint)
                        }
                        Icon(Icons.Filled.Edit, "Editar", tint = c.accent)
                    }
                    if (SheetMath.canLevelUp(last)) {
                        Spacer(Modifier.height(8.dp))
                        Text("✦ Experiência suficiente para subir de nível!", color = c.gold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionTitle("Acessados recentemente", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it.id }) { ch -> CharacterTile(ch, catById[ch.catalogId], { vm.open(Route.Sheet(ch.id)) }) }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item { SectionTitle("Fichas favoritas", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }
            items(favorites.take(6), key = { "fav-" + it.id }) { ch ->
                CharacterCard(
                    ch, catById[ch.catalogId], { vm.open(Route.Sheet(ch.id)) }, { vm.toggleFavorite(ch.id) },
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), compact = true,
                )
            }
        }

        item {
            SectionTitle("Categorias de RPG", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text("Ver todas", Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.openTab(RootTab.CATALOGS) }.padding(8.dp), color = c.accent, style = MaterialTheme.typography.labelLarge)
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(catalogs.sortedBy { it.order }, key = { it.id }) { cat ->
                    CategoryTile(cat, chars.count { it.catalogId == cat.id }) { vm.open(Route.CatalogDetail(cat.id)) }
                }
            }
        }

        item {
            Column(Modifier.padding(16.dp)) {
                RuneDivider()
                Text(
                    "Tudo é salvo automaticamente neste aparelho. Faça backups em Ajustes para levar suas fichas a outro dispositivo.",
                    style = MaterialTheme.typography.bodySmall, color = c.faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Hero(onCreate: () -> Unit, onExplore: () -> Unit) {
    val c = Codex.colors
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.accentDeep.copy(alpha = 0.45f), c.abyss, Color.Transparent)))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        ArcaneSigil(Modifier.align(Alignment.TopEnd).padding(top = 4.dp).size(220.dp).padding(end = 0.dp), color = c.accent.copy(alpha = 0.55f))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 16.dp)) {
            Text("GRIMÓRIO DE PERSONAGENS", style = MaterialTheme.typography.labelSmall, color = c.gold)
            Spacer(Modifier.height(6.dp))
            Text("Códice\nUmbral", style = MaterialTheme.typography.displaySmall, color = c.text, fontFamily = TitleFont, lineHeight = MaterialTheme.typography.displaySmall.fontSize * 1.05f)
            Spacer(Modifier.height(8.dp))
            Text(
                "Fichas, universos e segredos de seus personagens — vampiros, magos, cavaleiros malditos e tudo o que habita a escuridão.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.fillMaxWidth(0.88f),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcaneButton("Nova ficha", onCreate, Modifier.weight(1f), icon = Icons.Filled.Add)
                GhostButton("Catálogos", onExplore, Modifier.weight(1f), icon = Icons.Filled.AutoStories)
            }
        }
    }
}

@Composable
private fun CategoryTile(cat: Catalog, count: Int, onClick: () -> Unit) {
    val c = Codex.colors
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).background(c.panel)
            .border(1.dp, Color(cat.accent).copy(alpha = 0.45f), RoundedCornerShape(14.dp)).clickable(onClick = onClick)
    ) {
        CatalogCover(cat, Modifier.fillMaxWidth().aspectRatio(1.25f), glyphSize = 30.dp)
        Column(Modifier.padding(10.dp)) {
            Text(cat.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (count == 1) "1 personagem" else "$count personagens", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.faint, modifier = Modifier.size(14.dp))
            }
        }
    }
}
