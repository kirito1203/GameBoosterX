package com.umbral.codex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umbral.codex.ui.theme.Codex

/**
 * Modal de criação/edição: ocupa quase toda a largura no celular, rola o conteúdo e mantém
 * os botões Salvar/Cancelar sempre visíveis acima do teclado.
 */
@Composable
fun EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    saveLabel: String = "Salvar",
    onDelete: (() -> Unit)? = null,
    deleteWhat: String = "este item",
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Codex.colors
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(c.panel)
                    .border(1.dp, c.accent.copy(alpha = 0.4f), PanelShape)
                    .ornateCorners(c.gold.copy(alpha = 0.5f), Codex.style.ornaments)
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    RoundIconButton(Icons.Filled.Close, "Fechar", onDismiss)
                }
                RuneDivider(Modifier.padding(horizontal = 16.dp))
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
                Row(
                    Modifier.fillMaxWidth().background(c.abyss).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onDelete != null) {
                        RoundIconButton(Icons.Filled.Delete, "Excluir", { confirmDelete = true }, tint = c.danger, filled = true)
                    }
                    Spacer(Modifier.weight(1f))
                    GhostButton("Cancelar", onDismiss, color = c.muted)
                    ArcaneButton(saveLabel, onSave, enabled = saveEnabled)
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Excluir?",
            text = "Tem certeza que deseja excluir $deleteWhat? Esta ação não pode ser desfeita.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val c = Codex.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panelHigh,
        titleContentColor = c.text,
        textContentColor = c.muted,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = if (destructive) c.danger else c.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = c.muted) } },
    )
}

/** Pede um texto curto (nome de categoria, moeda...). */
@Composable
fun PromptDialog(title: String, label: String, initial: String = "", confirmLabel: String = "Salvar", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    EditorDialog(title, onDismiss, { onConfirm(text.trim()) }, saveEnabled = text.isNotBlank(), saveLabel = confirmLabel) {
        CodexTextField(text, { text = it }, label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Codex.colors
    Column(modifier) {
        Text("Ícone", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.size(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CodexIcons.all.forEach { (name, icon) ->
                val sel = name == selected
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) c.accent.copy(alpha = 0.25f) else c.abyss)
                        .border(1.dp, if (sel) c.accent else c.line, RoundedCornerShape(10.dp))
                        .clickable { onSelect(name) },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, name, tint = if (sel) c.text else c.muted, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit, label: String = "Cor") {
    val c = Codex.colors
    var choosing by remember { mutableStateOf(false) }
    val isCustom = selected !in colors
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            colors.forEach { col -> ColorDot(Color(col), col == selected) { onSelect(col) } }
            CustomColorDot(if (isCustom) selected else null, isCustom) { choosing = true }
        }
    }
    if (choosing) ColorChooserDialog("Escolher cor", selected, { choosing = false }, { onSelect(it); choosing = false })
}

/** Duas colunas que viram uma em telas estreitas demais. */
@Composable
fun FieldRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Codex.colors.muted, modifier = Modifier.padding(horizontal = 2.dp))
}
