package com.umbral.codex.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbral.codex.core.Ids
import com.umbral.codex.model.Catalog
import com.umbral.codex.ui.LocalAppState
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.Route
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.CatalogCover
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.ColorPicker
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.IconPicker
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.ScreenHeader
import com.umbral.codex.ui.components.Tag
import com.umbral.codex.ui.components.rememberImagePicker
import com.umbral.codex.ui.theme.Codex

fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

val CatalogAccents = listOf(
    0xFF8E2B4AL, 0xFF7A1F3DL, 0xFF2F7A8CL, 0xFF4F7BD1L, 0xFF9C7A3CL, 0xFF6B2020L, 0xFF5E3A9CL, 0xFF6D4AB8L,
    0xFF3E8A5CL, 0xFFB0603AL, 0xFF8A8F9EL, 0xFFB04E9AL,
)

@Composable
fun CatalogsScreen() {
    val vm = LocalAppState.current
    val repo = LocalRepository.current
    val catalogs by repo.catalogs.collectAsStateWithLifecycle()
    val chars by repo.characters.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val c = Codex.colors

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Catálogos de RPG", "Gêneros, universos e sistemas") {
            ArcaneButton("Nova", { creating = true }, Modifier.padding(end = 8.dp), icon = Icons.Filled.Add)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Cada catálogo reúne ambientação, raças, classes, poderes e modelos de ficha. Tudo pode ser editado — e você pode criar os seus.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
            }
            items(catalogs.sortedBy { it.order }, key = { it.id }) { cat ->
                val count = chars.count { it.catalogId == cat.id }
                OrnatePanel(padding = PaddingValues(0.dp), accent = Color(cat.accent), onClick = { vm.open(Route.CatalogDetail(cat.id)) }, ornaments = false) {
                    CatalogCover(cat, Modifier.fillMaxWidth().aspectRatio(1.1f), glyphSize = 34.dp)
                    Column(Modifier.padding(12.dp)) {
                        Text(cat.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(cat.tagline.ifBlank { cat.description }, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag(plural(cat.templates.size, "modelo", "modelos"), c.gold)
                            Tag(plural(count, "ficha", "fichas"), Color(cat.accent))
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CatalogEditDialog(
            initial = Catalog(Ids.new(), "", accent = CatalogAccents.random()),
            isNew = true,
            onDismiss = { creating = false },
            onSave = { cat -> repo.upsertCatalog(cat); creating = false; vm.toast("Categoria “${cat.name}” criada"); vm.open(Route.CatalogDetail(cat.id)) },
        )
    }
}

/** Criação/edição dos dados gerais da categoria: nome, chamada, descrição, ícone, cor e capa. */
@Composable
fun CatalogEditDialog(initial: Catalog, isNew: Boolean, onDismiss: () -> Unit, onSave: (Catalog) -> Unit, onDelete: (() -> Unit)? = null) {
    var cat by remember { mutableStateOf(initial) }
    val c = Codex.colors
    val pickCover = rememberImagePicker { id -> if (id != null) cat = cat.copy(coverImageId = id) }
    EditorDialog(
        title = if (isNew) "Nova categoria" else "Editar categoria",
        onDismiss = onDismiss,
        onSave = { onSave(cat.copy(name = cat.name.trim())) },
        saveEnabled = cat.name.isNotBlank(),
        onDelete = onDelete,
        deleteWhat = "a categoria “${initial.name}” (as fichas dela serão mantidas, sem categoria)",
    ) {
        CatalogCover(cat, Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(12.dp)), glyphSize = 32.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Imagem de capa", pickCover, Modifier.weight(1f), icon = Icons.Filled.AddPhotoAlternate)
            if (cat.coverImageId != null) GhostButton("Remover", { cat = cat.copy(coverImageId = null) }, icon = Icons.Filled.HideImage, color = c.muted)
        }
        CodexTextField(cat.name, { cat = cat.copy(name = it) }, "Nome da categoria", placeholder = "Ex.: Steampunk sombrio")
        CodexTextField(cat.tagline, { cat = cat.copy(tagline = it) }, "Frase de apresentação", singleLine = false, minLines = 2)
        CodexTextField(cat.description, { cat = cat.copy(description = it) }, "Descrição", singleLine = false, minLines = 3)
        ColorPicker(CatalogAccents, cat.accent, { cat = cat.copy(accent = it) }, "Cor da capa")
        IconPicker(cat.glyph, { cat = cat.copy(glyph = it) })
        Spacer(Modifier.size(4.dp))
    }
}
