package com.memesoundboardx.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memesoundboardx.core.util.Formatters
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.model.SoundSource
import com.memesoundboardx.ui.theme.MonoLabel
import com.memesoundboardx.ui.theme.NeonColors

/** Cartão com borda em gradiente azul→roxo. */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.5.dp, NeonColors.NeonGradient, shape)
            .background(NeonColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) { content() }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    NeonCard(modifier) {
        Column {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = NeonColors.Text)
            Text(label.uppercase(), style = MonoLabel, color = NeonColors.TextDim)
        }
    }
}

/** Aviso fixo, usado para limitações e etapas pendentes. Nunca esconde a informação. */
@Composable
fun NoticeBanner(title: String, message: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NeonColors.Warning.copy(alpha = 0.10f))
            .border(1.dp, NeonColors.Warning.copy(alpha = 0.5f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = NeonColors.Warning)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = NeonColors.Warning)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = NeonColors.Text)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = NeonColors.Purple, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonColors.TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Linha de som. Na ETAPA 1 não há botão de tocar: o motor de áudio chega na ETAPA 3
 * e nenhum botão finge reproduzir. O favorito funciona de verdade (salvo no Room).
 */
@Composable
fun SoundRow(
    sound: Sound,
    categoryName: String?,
    onToggleFavorite: (Sound) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val favColor by animateColorAsState(
        if (sound.isFavorite) NeonColors.Purple else NeonColors.TextDim, label = "favColor",
    )
    val favScale by animateFloatAsState(if (sound.isFavorite) 1.15f else 1f, label = "favScale")
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NeonColors.Surface)
            .border(1.dp, NeonColors.Outline, shape)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                sound.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (sound.isAvailable) NeonColors.Text else NeonColors.TextDim,
            )
            val meta = buildList {
                add(categoryName ?: "Sem categoria")
                add(Formatters.duration(sound.durationMs))
                add(Formatters.size(sound.sizeBytes))
                if (sound.source == SoundSource.IMPORTED) add("importado")
                if (!sound.isAvailable) add("ARQUIVO INDISPONÍVEL")
            }.joinToString(" · ")
            Text(meta, style = MonoLabel, color = NeonColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { onToggleFavorite(sound) }) {
            Icon(
                if (sound.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (sound.isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                tint = favColor,
                modifier = Modifier.scale(favScale),
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun SoundList(
    sounds: List<Sound>,
    categoryNames: Map<String, String>,
    onToggleFavorite: (Sound) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    trailing: (@Composable (Sound) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) item(key = "header") { header() }
        items(sounds, key = { it.id }) { sound ->
            SoundRow(
                sound = sound,
                categoryName = sound.categoryId?.let { categoryNames[it] },
                onToggleFavorite = onToggleFavorite,
                trailing = trailing?.let { t -> @Composable { t(sound) } },
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MonoLabel.copy(fontWeight = FontWeight.Bold),
        color = NeonColors.Cyan,
    )
}
