package com.memesoundboardx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memesoundboardx.domain.model.CreditEntry
import com.memesoundboardx.domain.repository.LibrarySyncer
import com.memesoundboardx.ui.components.NeonCard
import com.memesoundboardx.ui.components.SectionTitle
import com.memesoundboardx.ui.theme.MonoLabel
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CreditsViewModel @Inject constructor(syncer: LibrarySyncer) : ViewModel() {
    val credits: StateFlow<List<CreditEntry>> = syncer.credits
}

/**
 * Atribuição exigida pelas licenças CC BY e CC BY-SA, e crédito voluntário para CC0/domínio público.
 * Os dados vêm do manifesto (assets/sounds.json), preenchido automaticamente a partir da fonte.
 */
@Composable
fun CreditsScreen(viewModel: CreditsViewModel = hiltViewModel()) {
    val credits by viewModel.credits.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle("Sons de terceiros (${credits.size})")
                Text(
                    "Os demais sons embutidos são originais deste app (síntese sonora). " +
                        "Licenças: CC0 e domínio público dispensam crédito; CC BY e CC BY-SA exigem — " +
                        "por isso cada som abaixo mostra autor, licença e link da fonte.",
                    style = MaterialTheme.typography.bodySmall, color = NeonColors.TextDim,
                )
            }
        }
        items(credits) { c ->
            NeonCard(
                Modifier.fillMaxWidth(),
                onClick = c.sourceUrl?.let { url -> { runCatching { uri.openUri(url) }; Unit } },
            ) {
                Column {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(c.author, c.license.label).joinToString(" · "),
                        style = MonoLabel, color = NeonColors.TextDim,
                    )
                    c.sourceUrl?.let { Text(it, style = MonoLabel, color = NeonColors.Blue, maxLines = 1) }
                }
            }
        }
    }
}
