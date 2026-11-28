package com.umbral.codex.ui.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umbral.codex.model.Character
import com.umbral.codex.model.Story
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.theme.Codex

private data class StoryField(val title: String, val hint: String, val get: (Story) -> String, val set: (Story, String) -> Story, val lines: Int = 4)

private val StoryFields = listOf(
    StoryField("História de origem", "De onde veio, como tudo começou.", { it.origin }, { s, v -> s.copy(origin = v) }, 6),
    StoryField("Infância", "Lugares, mentores, traumas e alegrias.", { it.childhood }, { s, v -> s.copy(childhood = v) }),
    StoryField("Família", "Pais, irmãos, linhagem, herdeiros.", { it.family }, { s, v -> s.copy(family = v) }),
    StoryField("Personalidade", "Traços, manias, virtudes e vícios.", { it.personality }, { s, v -> s.copy(personality = v) }),
    StoryField("Aparência", "Rosto, cicatrizes, roupas, voz.", { it.appearance }, { s, v -> s.copy(appearance = v) }),
    StoryField("Objetivos", "O que busca conquistar.", { it.goals }, { s, v -> s.copy(goals = v) }),
    StoryField("Motivações", "Por que continua lutando.", { it.motivations }, { s, v -> s.copy(motivations = v) }),
    StoryField("Medos", "O que o paralisa.", { it.fears }, { s, v -> s.copy(fears = v) }),
    StoryField("Segredos", "O que ninguém pode saber.", { it.secrets }, { s, v -> s.copy(secrets = v) }),
    StoryField("Relações", "Aliados, rivais, amores, dívidas.", { it.relations }, { s, v -> s.copy(relations = v) }),
    StoryField("Eventos importantes", "Marcos da trajetória, sessão a sessão.", { it.events }, { s, v -> s.copy(events = v) }, 6),
    StoryField("Descrição livre", "Diário, crônica, anotações narrativas.", { it.freeText }, { s, v -> s.copy(freeText = v) }, 8),
)

@Composable
fun StoryTab(c: Character, ctl: SheetController) {
    val col = Codex.colors
    TabColumn {
        Text("Crônica de ${c.name.ifBlank { "personagem" }}", style = MaterialTheme.typography.headlineSmall, color = col.text)
        Hint("Escreva à vontade: os textos são salvos automaticamente enquanto você digita.")
        StoryFields.forEach { f ->
            OrnatePanel(Modifier.fillMaxWidth(), ornaments = false) {
                SectionTitle(f.title)
                CodexTextField(
                    f.get(c.story), { v -> ctl.update { it.copy(story = f.set(it.story, v)) } },
                    f.hint, singleLine = false, minLines = f.lines,
                )
            }
        }
    }
}
