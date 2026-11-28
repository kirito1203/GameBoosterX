package com.umbral.codex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grade responsiva: calcula quantas colunas cabem (mínimo [minItemWidth] cada) e distribui
 * os itens em linhas. Nunca ultrapassa a largura da tela.
 */
@Composable
fun <T> AutoGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    minItemWidth: Dp = 150.dp,
    maxColumns: Int = 6,
    spacing: Dp = 10.dp,
    itemContent: @Composable (T) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = ((maxWidth + spacing) / (minItemWidth + spacing)).toInt().coerceIn(1, maxColumns)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { item -> Column(Modifier.weight(1f)) { itemContent(item) } }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
