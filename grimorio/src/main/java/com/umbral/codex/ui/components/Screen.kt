package com.umbral.codex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umbral.codex.model.Catalog
import com.umbral.codex.model.Character
import com.umbral.codex.ui.theme.Codex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cabeçalho das telas: voltar opcional, título serifado, subtítulo e ações. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Codex.colors
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = if (onBack != null) 4.dp else 16.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack, tint = c.text)
        Column(Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
fun formatDate(ms: Long): String = if (ms <= 0) "—" else dateFmt.format(Date(ms))

fun Character.displayName() = name.ifBlank { "Sem nome" }

fun Character.subtitleLine(): String = listOf(race, charClass).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Personagem" }

/** Cartão de personagem usado na biblioteca, no início e nos catálogos. */
@Composable
fun CharacterCard(
    character: Character,
    catalog: Catalog?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    compact: Boolean = false,
) {
    val c = Codex.colors
    val accent = catalog?.let { Color(it.accent) } ?: c.accent
    OrnatePanel(modifier.fillMaxWidth(), accent = accent, onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(12.dp), ornaments = !compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Portrait(character.portraitId, character.displayName(), Modifier.size(if (compact) 46.dp else 58.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(character.displayName(), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        if (character.isDemo) {
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = c.warning)); append("Exemplo fictício · "); pop()
                        }
                        append(character.subtitleLine())
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag("Nível ${character.level}", c.gold)
                    if (catalog != null && !compact) Tag(catalog.name, accent, Modifier.weight(1f, fill = false))
                }
            }
            RoundIconButton(
                if (character.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (character.favorite) "Remover dos favoritos" else "Favoritar",
                onToggleFavorite,
                tint = if (character.favorite) c.danger else c.faint,
            )
            trailing()
        }
    }
}

/** Mini cartão vertical para carrosséis horizontais da tela inicial. */
@Composable
fun CharacterTile(character: Character, catalog: Catalog?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Codex.colors
    val accent = catalog?.let { Color(it.accent) } ?: c.accent
    Column(
        modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).background(c.panel).clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Portrait(character.portraitId, character.displayName(), Modifier.size(72.dp))
        Spacer(Modifier.height(8.dp))
        Text(character.displayName(), style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Nv. ${character.level} · ${character.charClass.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Spacer(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(accent.copy(alpha = 0.7f)))
    }
}
