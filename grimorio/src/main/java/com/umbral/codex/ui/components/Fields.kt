package com.umbral.codex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umbral.codex.ui.theme.Codex

@Composable
fun codexFieldColors(): TextFieldColors {
    val c = Codex.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent,
        unfocusedBorderColor = c.line,
        focusedLabelColor = c.accent,
        unfocusedLabelColor = c.muted,
        cursorColor = c.accent,
        focusedContainerColor = c.abyss,
        unfocusedContainerColor = c.abyss.copy(alpha = 0.6f),
        focusedTextColor = c.text,
        unfocusedTextColor = c.text,
        focusedPlaceholderColor = c.faint,
        unfocusedPlaceholderColor = c.faint,
    )
}

/**
 * Campo de texto que mantém o próprio buffer (cursor estável) e só sincroniza com o valor
 * externo quando ele muda por outro motivo. Cada digitação é repassada imediatamente
 * (salvamento automático).
 */
@Composable
fun CodexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    isError: Boolean = false,
    supportingText: String? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange.Zero)) }
    var focused by remember { mutableStateOf(false) }
    // Enquanto o campo está em foco, o texto digitado manda; valores externos atrasados são ignorados.
    LaunchedEffect(value, focused) { if (!focused && value != tfv.text) tfv = TextFieldValue(value, TextRange.Zero) }
    OutlinedTextField(
        value = tfv,
        onValueChange = { nv -> val changed = nv.text != tfv.text; tfv = nv; if (changed) onValueChange(nv.text) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization, keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        colors = codexFieldColors(),
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = textAlign),
    )
}

/** Campo numérico inteiro. Aceita texto parcial ("-", "") sem perder o valor salvo. */
@Composable
fun IntField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = true,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused && text.toIntOrNull() != value) text = value.toString() }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filterIndexed { i, ch -> ch.isDigit() || (allowNegative && ch == '-' && i == 0) }.take(9)
            text = filtered
            filtered.toIntOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.onFocusChanged { focused = it.isFocused; if (it.isFocused && text.trimStart('0', ',', '.').isEmpty()) text = "" },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        colors = codexFieldColors(),
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
    )
}

@Composable
fun LongField(value: Long, onValueChange: (Long) -> Unit, label: String, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused && text.toLongOrNull() != value) text = value.toString() }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val f = raw.filter { it.isDigit() }.take(15)
            text = f
            f.toLongOrNull()?.let(onValueChange)
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.onFocusChanged { focused = it.isFocused; if (it.isFocused && text.trimStart('0', ',', '.').isEmpty()) text = "" },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        colors = codexFieldColors(),
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
    )
}

/** Campo decimal (peso, capacidade). Aceita vírgula ou ponto. */
@Composable
fun DecimalField(value: Double, onValueChange: (Double) -> Unit, label: String, modifier: Modifier = Modifier) {
    fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString().replace('.', ',')
    var text by remember { mutableStateOf(fmt(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused && text.replace(',', '.').toDoubleOrNull() != value) text = fmt(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val f = raw.filter { it.isDigit() || it == ',' || it == '.' }.take(10)
            text = f
            f.replace(',', '.').toDoubleOrNull()?.let(onValueChange)
            if (f.isEmpty()) onValueChange(0.0)
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.onFocusChanged { focused = it.isFocused; if (it.isFocused && text.trimStart('0', ',', '.').isEmpty()) text = "" },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors = codexFieldColors(),
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
    )
}

/** Botões − / + com o valor no meio. */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    step: Int = 1,
    label: String? = null,
    format: (Int) -> String = { it.toString() },
) {
    val c = Codex.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label != null) Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Filled.Remove, "Diminuir", { onValueChange((value - step).coerceIn(min, max)) }, tint = c.accent, size = 40.dp, filled = true)
            Text(
                format(value), modifier = Modifier.widthIn(min = 44.dp).padding(horizontal = 6.dp),
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = c.text, maxLines = 1,
            )
            RoundIconButton(Icons.Filled.Add, "Aumentar", { onValueChange((value + step).coerceIn(min, max)) }, tint = c.accent, size = 40.dp, filled = true)
        }
    }
}

/** Seleção em lista suspensa. */
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Codex.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.abyss.copy(alpha = 0.6f))
                .border(1.dp, c.line, RoundedCornerShape(10.dp))
                .clickable { open = true }
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(optionLabel(selected), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, null, tint = c.muted)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = c.panelHigh) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(optionLabel(o), color = if (o == selected) c.accent else c.text) },
                    onClick = { onSelect(o); open = false },
                )
            }
        }
    }
}

/** Texto livre com sugestões (categorias, tipos, escolas). */
@Composable
fun SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    val c = Codex.colors
    Column(modifier) {
        CodexTextField(value, onValueChange, label)
        val visible = suggestions.distinct().filter { it.isNotBlank() && it != value }
        if (visible.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                visible.forEach { s ->
                    Text(
                        s,
                        Modifier.clip(RoundedCornerShape(8.dp)).background(c.panelHighest).clickable { onValueChange(s) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium, color = c.muted,
                    )
                }
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    val c = Codex.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked, onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.void, checkedTrackColor = c.accent,
                uncheckedThumbColor = c.muted, uncheckedTrackColor = c.abyss, uncheckedBorderColor = c.line,
            ),
        )
    }
}

/** Fileira rolável de chips de seleção única. */
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterPill(label(o), o == selected, { onSelect(o) }) }
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Codex.colors.accent) {
    val c = Codex.colors
    val shape = RoundedCornerShape(20.dp)
    Text(
        text,
        modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.22f) else c.panel)
            .border(1.dp, if (selected) color else c.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) c.text else c.muted,
        maxLines = 1,
    )
}

/** Linha "rótulo: valor" usada nos detalhes de magias, itens e habilidades. */
@Composable
fun DetailLine(label: String, value: String, modifier: Modifier = Modifier) {
    if (value.isBlank()) return
    val c = Codex.colors
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = c.gold)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = c.text)
    }
}

@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val c = Codex.colors
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .border(2.dp, if (selected) c.text else Color.Transparent, RoundedCornerShape(20.dp)).padding(5.dp)
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(color))
    }
}
