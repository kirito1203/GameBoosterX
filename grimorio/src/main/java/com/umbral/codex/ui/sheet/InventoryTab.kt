package com.umbral.codex.ui.sheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umbral.codex.core.Dice
import com.umbral.codex.core.Ids
import com.umbral.codex.core.SheetMath
import com.umbral.codex.model.Character
import com.umbral.codex.model.Currency
import com.umbral.codex.model.Item
import com.umbral.codex.model.ItemCategories
import com.umbral.codex.model.Rarity
import com.umbral.codex.model.TransactionKind
import com.umbral.codex.ui.components.ChoiceChips
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.ColorPicker
import com.umbral.codex.ui.components.DecimalField
import com.umbral.codex.ui.components.DetailLine
import com.umbral.codex.ui.components.DropdownField
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.EmptyState
import com.umbral.codex.ui.components.FieldRow
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.GlowBar
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.IconPicker
import com.umbral.codex.ui.components.IntField
import com.umbral.codex.ui.components.LongField
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.SuggestField
import com.umbral.codex.ui.components.SwitchRow
import com.umbral.codex.ui.components.Tag
import com.umbral.codex.ui.components.formatDate
import com.umbral.codex.ui.components.rememberImagePicker
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont

@Composable
fun InventoryTab(c: Character, ctl: SheetController) {
    TabColumn {
        CoinsPanel(c, ctl)
        CarryPanel(c, ctl)
        ItemsSection(c, ctl)
    }
}

// ---------------- Moedas ----------------

@Composable
private fun CoinsPanel(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var tx by remember { mutableStateOf<Pair<Currency, TransactionKind>?>(null) }
    var editingCoin by remember { mutableStateOf<Currency?>(null) }
    var creatingCoin by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    OrnatePanel(Modifier.fillMaxWidth()) {
        SectionTitle("Moedas") {
            RoundIconButton(Icons.Filled.History, "Histórico de transações", { history = true }, tint = col.muted, size = 36.dp)
            RoundIconButton(Icons.Filled.Add, "Nova moeda", { creatingCoin = true }, tint = col.accent, size = 36.dp)
        }
        if (c.currencies.isEmpty()) Hint("Nenhuma moeda. Adicione Ouro, Prata, Cobre ou moedas do seu mundo.")
        c.currencies.forEach { cur ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(cur.color).copy(alpha = 0.25f)).clickable { editingCoin = cur }, contentAlignment = Alignment.Center) {
                    Text(cur.abbr.ifBlank { cur.name.take(2) }, color = Color(cur.color), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { editingCoin = cur }) {
                    Text(cur.name, style = MaterialTheme.typography.bodyMedium, color = col.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${cur.amount}", style = MaterialTheme.typography.titleLarge, color = col.text, fontFamily = TitleFont, maxLines = 1)
                }
                RoundIconButton(Icons.Filled.Remove, "Gastar ${cur.name}", { tx = cur to TransactionKind.SPEND }, tint = col.danger, size = 40.dp, filled = true)
                Spacer(Modifier.width(6.dp))
                RoundIconButton(Icons.Filled.Add, "Ganhar ${cur.name}", { tx = cur to TransactionKind.GAIN }, tint = col.success, size = 40.dp, filled = true)
                Spacer(Modifier.width(6.dp))
                RoundIconButton(Icons.Filled.Edit, "Definir saldo de ${cur.name}", { tx = cur to TransactionKind.SET }, tint = col.muted, size = 40.dp)
            }
        }
        c.transactions.take(3).forEach { t -> TransactionLine(t) }
    }
    tx?.let { (cur, kind) -> TransactionDialog(c, cur, kind, ctl) { tx = null } }
    if (creatingCoin) CurrencyDialog(null, ctl) { creatingCoin = false }
    editingCoin?.let { cur -> CurrencyDialog(cur, ctl) { editingCoin = null } }
    if (history) {
        EditorDialog("Histórico de moedas", { history = false }, { history = false }, saveLabel = "Fechar") {
            if (c.transactions.isEmpty()) Hint("Nenhuma movimentação registrada.")
            c.transactions.forEach { TransactionLine(it, full = true) }
            if (c.transactions.isNotEmpty()) GhostButton("Limpar histórico", { ctl.update { it.copy(transactions = emptyList()) }; history = false }, Modifier.fillMaxWidth(), color = col.danger)
        }
    }
}

@Composable
private fun TransactionLine(t: com.umbral.codex.model.CoinTransaction, full: Boolean = false) {
    val col = Codex.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (t.kind) { TransactionKind.GAIN -> "+${t.delta}"; TransactionKind.SPEND -> "${t.delta}"; TransactionKind.SET -> "= ${t.balanceAfter}" },
            Modifier.width(72.dp), color = when (t.kind) { TransactionKind.GAIN -> col.success; TransactionKind.SPEND -> col.danger; else -> col.muted },
            style = MaterialTheme.typography.labelLarge,
        )
        Column(Modifier.weight(1f)) {
            Text("${t.currencyName}${if (t.reason.isNotBlank()) " — ${t.reason}" else ""}", style = MaterialTheme.typography.bodySmall, color = col.text, maxLines = if (full) 3 else 1, overflow = TextOverflow.Ellipsis)
            if (full) Text("${formatDate(t.timestamp)} · saldo ${t.balanceAfter}", style = MaterialTheme.typography.bodySmall, color = col.faint)
        }
    }
}

@Composable
private fun TransactionDialog(c: Character, cur: Currency, kind: TransactionKind, ctl: SheetController, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(if (kind == TransactionKind.SET) cur.amount else 0L) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val title = when (kind) { TransactionKind.GAIN -> "Ganhar ${cur.name}"; TransactionKind.SPEND -> "Gastar ${cur.name}"; TransactionKind.SET -> "Definir saldo de ${cur.name}" }
    EditorDialog(title, onDismiss, {
        when (val r = SheetMath.coinTransaction(c, cur.id, kind, amount, reason, System.currentTimeMillis())) {
            is SheetMath.CoinResult.Ok -> { ctl.update { r.character.copy(updatedAt = it.updatedAt).let { u -> it.copy(currencies = u.currencies, transactions = u.transactions) } }; onDismiss() }
            is SheetMath.CoinResult.Error -> error = r.message
        }
    }, saveLabel = kind.label.let { if (kind == TransactionKind.SET) "Definir" else it }) {
        Text("Saldo atual: ${cur.amount} ${cur.abbr}", style = MaterialTheme.typography.titleSmall, color = Codex.colors.gold)
        LongField(amount, { amount = it; error = null }, "Quantidade", Modifier.fillMaxWidth())
        if (kind != TransactionKind.SET) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1L, 5L, 10L, 50L, 100L).forEach { q -> GhostButton("+$q", { amount += q; error = null }, Modifier.weight(1f)) }
            }
        }
        CodexTextField(reason, { reason = it }, "Motivo (opcional)", placeholder = if (kind == TransactionKind.SPEND) "Ex.: espada nova" else "Ex.: recompensa da missão")
        error?.let { Text(it, color = Codex.colors.danger, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun CurrencyDialog(initial: Currency?, ctl: SheetController, onDismiss: () -> Unit) {
    var cur by remember { mutableStateOf(initial ?: Currency(Ids.new(), "", "", 0)) }
    EditorDialog(
        if (initial == null) "Nova moeda" else "Editar moeda", onDismiss,
        {
            val fixed = cur.copy(name = cur.name.trim(), abbr = cur.abbr.trim())
            ctl.update { ch -> ch.copy(currencies = if (initial == null) ch.currencies + fixed else ch.currencies.map { if (it.id == fixed.id) fixed.copy(amount = it.amount) else it }) }
            onDismiss()
        },
        saveEnabled = cur.name.isNotBlank(),
        onDelete = initial?.let { { ctl.update { ch -> ch.copy(currencies = ch.currencies.filterNot { it.id == initial.id }) }; onDismiss() } },
        deleteWhat = "a moeda “${initial?.name}”",
    ) {
        SuggestField(cur.name, { cur = cur.copy(name = it) }, "Nome", listOf("Ouro", "Prata", "Cobre", "Platina", "Gemas", "Almas", "Créditos"))
        CodexTextField(cur.abbr, { cur = cur.copy(abbr = it) }, "Abreviação (ex.: PO)")
        if (initial == null) LongField(cur.amount, { cur = cur.copy(amount = it) }, "Saldo inicial", Modifier.fillMaxWidth())
        ColorPicker(listOf(0xFFE0B64AL, 0xFFB8BCC8L, 0xFFB8734AL, 0xFFE5E4E2L, 0xFF5FAF7AL, 0xFF9D6BFFL, 0xFFC2415BL), cur.color, { cur = cur.copy(color = it) })
    }
}

// ---------------- Carga ----------------

@Composable
private fun CarryPanel(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var edit by remember { mutableStateOf(false) }
    val load = SheetMath.currentLoad(c)
    val cap = SheetMath.carryCapacity(c)
    val over = SheetMath.isOverloaded(c)
    OrnatePanel(Modifier.fillMaxWidth(), accent = if (over) col.danger else col.accent, glow = over) {
        SectionTitle("Capacidade de carga") { RoundIconButton(Icons.Filled.Edit, "Configurar carga", { edit = true }, tint = col.muted, size = 36.dp) }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(SheetMath.formatWeight(load), style = MaterialTheme.typography.headlineSmall, color = if (over) col.danger else col.text, fontFamily = TitleFont)
            Text(" / ${SheetMath.formatWeight(cap)} ${c.weightUnit}", style = MaterialTheme.typography.titleSmall, color = col.muted)
        }
        Spacer(Modifier.height(6.dp))
        GlowBar(if (cap <= 0) 1f else (load / cap).toFloat(), if (over) col.danger else if (load / cap.coerceAtLeast(0.01) > 0.75) col.warning else col.success, height = 12.dp)
        if (over) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = col.danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sobrecarregado! Excesso de ${SheetMath.formatWeight(load - cap)} ${c.weightUnit}.", color = col.danger, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (c.rules.carryPerStrength > 0) Hint("Capacidade automática: Força × ${SheetMath.formatWeight(c.rules.carryPerStrength)}.")
    }
    if (edit) {
        var manual by remember { mutableStateOf(c.carryCapacity) }
        var perStr by remember { mutableStateOf(c.rules.carryPerStrength) }
        var unit by remember { mutableStateOf(c.weightUnit) }
        EditorDialog("Capacidade de carga", { edit = false }, {
            ctl.update { it.copy(carryCapacity = manual, weightUnit = unit.trim().ifBlank { "kg" }, rules = it.rules.copy(carryPerStrength = perStr)) }
            edit = false
        }) {
            SwitchRow("Calcular pela Força", perStr > 0, { perStr = if (it) 7.5 else 0.0 })
            if (perStr > 0) DecimalField(perStr, { perStr = it }, "Peso por ponto de Força", Modifier.fillMaxWidth())
            else DecimalField(manual, { manual = it }, "Capacidade máxima", Modifier.fillMaxWidth())
            CodexTextField(unit, { unit = it }, "Unidade de peso")
        }
    }
}

// ---------------- Itens ----------------

@Composable
private fun ItemsSection(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyEquipped by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var creating by remember { mutableStateOf(false) }
    val categories = c.items.map { it.category }.distinct()
    val list = c.items.filter {
        (query.isBlank() || it.name.contains(query.trim(), true)) && (category == null || it.category == category) && (!onlyEquipped || it.equipped)
    }
    OrnatePanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Itens", style = MaterialTheme.typography.titleLarge, color = col.text)
                Text("${c.items.sumOf { it.quantity }} itens · ${c.items.count { it.equipped }} equipados", style = MaterialTheme.typography.bodySmall, color = col.muted)
            }
            RoundIconButton(Icons.Filled.Add, "Novo item", { creating = true }, tint = col.accent, filled = true)
        }
        Spacer(Modifier.height(8.dp))
        CodexTextField(query, { query = it }, "Pesquisar item")
        Spacer(Modifier.height(8.dp))
        if (categories.isNotEmpty()) ChoiceChips(listOf<String?>(null) + categories, category, { it ?: "Todos" }, { category = it })
        SwitchRow("Somente equipados", onlyEquipped, { onlyEquipped = it })
    }
    if (list.isEmpty()) EmptyState(if (c.items.isEmpty()) "Inventário vazio" else "Nenhum item encontrado", "Armas, armaduras, poções, relíquias e artefatos.")
    list.forEach { item -> ItemCard(item, c, ctl) { editing = item } }
    GhostButton("Adicionar item", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
    if (creating) ItemDialog(null, ctl) { creating = false }
    editing?.let { i -> ItemDialog(c.items.firstOrNull { it.id == i.id } ?: i, ctl) { editing = null } }
}

@Composable
private fun ItemCard(item: Item, c: Character, ctl: SheetController, onEdit: () -> Unit) {
    val col = Codex.colors
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val rarity = Color(item.rarity.color)
    fun updateItem(t: (Item) -> Item) = ctl.update { ch -> ch.copy(items = ch.items.map { if (it.id == item.id) t(it) else it }) }
    OrnatePanel(
        Modifier.fillMaxWidth().animateContentSize(), accent = rarity, glow = item.equipped, ornaments = item.equipped,
        padding = PaddingValues(12.dp), onClick = { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityIcon(item.imageId, item.icon, rarity)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, color = col.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.quantity != 1) Text("  ×${item.quantity}", style = MaterialTheme.typography.titleSmall, color = col.gold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Tag(item.rarity.label, rarity)
                    Tag(item.category, col.muted)
                    if (item.weight > 0) Text("${SheetMath.formatWeight(item.weight * item.quantity)} ${c.weightUnit}", style = MaterialTheme.typography.bodySmall, color = col.faint)
                }
            }
            if (item.equippable) {
                Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { updateItem { it.copy(equipped = !it.equipped) } }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (item.equipped) Icons.Filled.CheckCircle else Icons.Filled.Shield, null, tint = if (item.equipped) col.success else col.faint)
                    Text(if (item.equipped) "Equipado" else "Equipar", style = MaterialTheme.typography.labelSmall, color = if (item.equipped) col.success else col.muted)
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium, color = col.text)
            DetailLine("Valor", item.value)
            DetailLine("Peso unitário", if (item.weight > 0) "${SheetMath.formatWeight(item.weight)} ${c.weightUnit}" else "")
            DetailLine("Efeitos", item.effects)
            DetailLine("Bônus", item.bonus)
            DetailLine("Dados", item.dice)
            DetailLine("Observações", item.notes)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoundIconButton(Icons.Filled.Remove, "Diminuir quantidade", { updateItem { it.copy(quantity = (it.quantity - 1).coerceAtLeast(0)) } }, tint = col.accent, size = 40.dp, filled = true)
                Text("${item.quantity}", style = MaterialTheme.typography.titleMedium, color = col.text)
                RoundIconButton(Icons.Filled.Add, "Aumentar quantidade", { updateItem { it.copy(quantity = it.quantity + 1) } }, tint = col.accent, size = 40.dp, filled = true)
                Spacer(Modifier.weight(1f))
                if (item.dice.isNotBlank()) RoundIconButton(Icons.Filled.Casino, "Rolar ${item.dice}", { ctl.roll(item.name, item.dice) }, tint = col.danger, filled = true)
                RoundIconButton(Icons.Filled.Edit, "Editar item", onEdit, tint = col.muted, filled = true)
            }
        }
    }
}

@Composable
private fun ItemDialog(initial: Item?, ctl: SheetController, onDismiss: () -> Unit) {
    var item by remember { mutableStateOf(initial ?: Item(Ids.new(), "")) }
    val col = Codex.colors
    val pick = rememberImagePicker { id -> if (id != null) item = item.copy(imageId = id) }
    val diceError = item.dice.isNotBlank() && Dice.tryParse(item.dice) == null
    EditorDialog(
        if (initial == null) "Novo item" else "Editar item", onDismiss,
        {
            val fixed = item.copy(name = item.name.trim(), category = item.category.trim().ifBlank { "Objeto comum" })
            ctl.update { ch -> ch.copy(items = if (initial == null) ch.items + fixed else ch.items.map { if (it.id == fixed.id) fixed else it }) }
            onDismiss()
        },
        saveEnabled = item.name.isNotBlank() && !diceError,
        onDelete = initial?.let { { ctl.update { ch -> ch.copy(items = ch.items.filterNot { it.id == initial.id }) }; onDismiss() } },
        deleteWhat = "o item “${initial?.name}”",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityIcon(item.imageId, item.icon, Color(item.rarity.color), 56.dp)
            Spacer(Modifier.width(10.dp))
            GhostButton("Imagem", pick, Modifier.weight(1f), icon = Icons.Filled.AddPhotoAlternate)
            if (item.imageId != null) RoundIconButton(Icons.Filled.HideImage, "Remover imagem", { item = item.copy(imageId = null) })
        }
        CodexTextField(item.name, { item = item.copy(name = it) }, "Nome")
        SuggestField(item.category, { item = item.copy(category = it) }, "Categoria", ItemCategories.defaults)
        DropdownField("Raridade", Rarity.entries, item.rarity, { it.label }, { item = item.copy(rarity = it) })
        CodexTextField(item.description, { item = item.copy(description = it) }, "Descrição", singleLine = false, minLines = 2)
        FieldRow {
            IntField(item.quantity, { item = item.copy(quantity = it) }, "Quantidade", Modifier.weight(1f), allowNegative = false, min = 0)
            DecimalField(item.weight, { item = item.copy(weight = it) }, "Peso (un.)", Modifier.weight(1f))
        }
        FieldRow {
            CodexTextField(item.value, { item = item.copy(value = it) }, "Valor", Modifier.weight(1f), placeholder = "15 PO")
            CodexTextField(item.dice, { item = item.copy(dice = it) }, "Dados", Modifier.weight(1f), placeholder = "1d8+3", isError = diceError)
        }
        CodexTextField(item.effects, { item = item.copy(effects = it) }, "Efeitos", singleLine = false, minLines = 2)
        CodexTextField(item.bonus, { item = item.copy(bonus = it) }, "Bônus", placeholder = "+1 CA, +2 em Furtividade")
        CodexTextField(item.notes, { item = item.copy(notes = it) }, "Observações", singleLine = false, minLines = 2)
        SwitchRow("Pode ser equipado", item.equippable, { item = item.copy(equippable = it, equipped = it && item.equipped) })
        if (item.equippable) SwitchRow("Equipado", item.equipped, { item = item.copy(equipped = it) })
        IconPicker(item.icon, { item = item.copy(icon = it) })
        if (diceError) Text("Use o formato de dados, ex.: 1d8+3", color = col.danger, style = MaterialTheme.typography.bodySmall)
    }
}
