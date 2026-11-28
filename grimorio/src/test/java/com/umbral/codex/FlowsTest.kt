package com.umbral.codex

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.umbral.codex.data.CodexRepository
import com.umbral.codex.data.SeedCatalogs
import com.umbral.codex.model.Character
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Fluxo completo pela interface real, como um usuário faria: cria uma ficha, edita, usa todas as
 * abas, confere o salvamento em disco (relendo os arquivos) e exclui.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w400dp-h860dp-xhdpi")
class FlowsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val repo get() = (rule.activity.application as CodexApplication).repository
    private fun kael(): Character = repo.characters.value.first { it.name == "Kael Sombra" }

    private fun settle() { rule.mainClock.advanceTimeBy(800); rule.waitForIdle() }

    private fun SemanticsNodeInteraction.go(): SemanticsNodeInteraction {
        try { performScrollTo() } catch (_: Throwable) {}
        performClick(); settle(); return this
    }

    private fun text(t: String) = rule.onAllNodesWithText(t, useUnmergedTree = true).onFirst().go()
    private fun lastText(t: String) = rule.onAllNodesWithText(t, useUnmergedTree = true).onLast().go()
    private fun desc(d: String) = rule.onAllNodes(hasContentDescription(d)).onFirst().go()
    private fun tab(t: String) = rule.onAllNodes(hasText(t) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).onFirst().go()
    private fun type(label: String, value: String, last: Boolean = false) {
        val nodes = rule.onAllNodes(hasText(label) and hasSetTextAction())
        val n = if (last) nodes.onLast() else nodes.onFirst()
        try { n.performScrollTo() } catch (_: Throwable) {}
        n.performTextInput(value); settle()
    }

    @Test
    fun createEditUseSaveAndDeleteACharacter() {
        val start = System.currentTimeMillis()
        while (!repo.loaded.value && System.currentTimeMillis() - start < 20_000) Thread.sleep(50)
        settle()

        // Catálogos organizados por gênero
        val names = repo.catalogs.value.map { it.name }
        listOf("Sobrenatural", "Paranormal", "Fantasia", "Dark Fantasy", "Medieval", "Horror", "Ocultismo", "Fantasia medieval")
            .forEach { assertTrue("catálogo $it", it in names) }

        // ---- Criar (do zero, sistema clássico, universo Dark Fantasy)
        tab("Criar")
        type("Nome do personagem", "Kael Sombra")
        text("Nenhum (ficha avulsa)"); lastText("Dark Fantasy")
        lastText("Criar ficha")
        val created = kael()
        assertEquals(SeedCatalogs.DARK_FANTASY, created.catalogId)
        assertEquals(6, created.attributes.size); assertEquals(19, created.skills.size)

        // ---- Editar informações básicas
        type("Raça", "Meio-cinéreo")
        type("Classe", "Hemomante")
        type("Idade", "31")
        assertEquals("Meio-cinéreo", kael().race); assertEquals("Hemomante", kael().charClass); assertEquals("31", kael().age)

        // ---- Status / recursos
        val vida = kael().resources.first { it.name == "Vida" }
        desc("Diminuir Vida"); desc("Diminuir Vida")
        assertEquals(vida.current - 2, kael().resources.first { it.name == "Vida" }.current)
        desc("Aumentar Vida")
        assertEquals(vida.current - 1, kael().resources.first { it.name == "Vida" }.current)

        // ---- Progressão: XP e nível
        lastText("Experiência"); text("+300"); lastText("Adicionar")
        assertEquals(300L, kael().xp)
        text("Subir de nível")
        assertEquals(2, kael().level); assertEquals(0L, kael().xp); assertEquals(600L, kael().xpToNext)

        // ---- Atributos: criar atributo personalizado
        tab("Atributos")
        desc("Novo atributo")
        type("Nome", "Corrupção", last = true)
        lastText("Salvar")
        assertTrue(kael().attributes.any { it.name == "Corrupção" })
        val rollsBefore = kael().rollHistory.size
        desc("Rolar Força")
        assertEquals(rollsBefore + 1, kael().rollHistory.size)

        // ---- Perícias: rolar e alternar proficiência
        tab("Perícias")
        text("Acrobacia")
        assertEquals(rollsBefore + 2, kael().rollHistory.size)

        // ---- Magia: criar e conjurar
        tab("Magia")
        desc("Nova magia")
        type("Nome", "Lança de sangue", last = true)
        type("Dano (dados)", "2d6+1", last = true)
        type("Custo de mana", "2", last = true)
        lastText("Salvar")
        val spell = kael().spells.single()
        assertEquals("Lança de sangue", spell.name); assertEquals("2d6+1", spell.damage)
        val mana = kael().resources.first { it.name == "Mana" }.current
        text("Lança de sangue"); text("Conjurar")
        assertEquals(mana - spell.manaCost, kael().resources.first { it.name == "Mana" }.current)

        // ---- Poderes e características
        tab("Poderes")
        desc("Nova habilidade")
        type("Nome", "Sede rubra", last = true)
        lastText("Salvar")
        assertEquals("Sede rubra", kael().abilities.single().name)
        text("Características (0)")
        desc("Nova característica")
        type("Nome", "Visão noturna", last = true)
        lastText("Salvar")
        assertEquals("Visão noturna", kael().traits.single().name)

        // ---- Inventário: moedas e itens
        tab("Inventário")
        desc("Ganhar Ouro")
        type("Quantidade", "50", last = true)
        type("Motivo (opcional)", "Recompensa", last = true)
        lastText("Ganho")
        assertEquals(50L, kael().currencies.first { it.name == "Ouro" }.amount)
        desc("Gastar Ouro")
        type("Quantidade", "80", last = true)
        lastText("Gasto")
        assertEquals("gasto acima do saldo é recusado", 50L, kael().currencies.first { it.name == "Ouro" }.amount)
        text("Cancelar")
        assertEquals(1, kael().transactions.size)
        desc("Novo item")
        type("Nome", "Adaga ritual", last = true)
        lastText("Salvar")
        text("Equipar")
        assertTrue(kael().items.single { it.name == "Adaga ritual" }.equipped)

        // ---- História
        tab("História")
        type("De onde veio, como tudo começou.", "Nasceu durante o eclipse.")
        assertEquals("Nasceu durante o eclipse.", kael().story.origin)

        // ---- Salvamento: relê os arquivos com um repositório novo
        runBlocking { repo.flush() }
        val reread = CodexRepository(File(rule.activity.filesDir, "codex"), CoroutineScope(Dispatchers.IO))
        runBlocking { reread.load() }
        val saved = reread.characters.value.first { it.name == "Kael Sombra" }
        assertEquals("Meio-cinéreo", saved.race); assertEquals(2, saved.level)
        assertEquals(1, saved.spells.size); assertEquals(1, saved.abilities.size); assertEquals(1, saved.traits.size)
        assertEquals(50L, saved.currencies.first { it.name == "Ouro" }.amount)
        assertEquals("Nasceu durante o eclipse.", saved.story.origin)

        // ---- Excluir com confirmação
        desc("Mais ações"); text("Excluir ficha"); lastText("Excluir")
        assertNull(repo.characters.value.firstOrNull { it.name == "Kael Sombra" })
        runBlocking { repo.flush() }
        val afterDelete = CodexRepository(File(rule.activity.filesDir, "codex"), CoroutineScope(Dispatchers.IO))
        runBlocking { afterDelete.load() }
        assertNull(afterDelete.characters.value.firstOrNull { it.name == "Kael Sombra" })
        assertNotNull(afterDelete.catalogs.value.firstOrNull { it.id == SeedCatalogs.DARK_FANTASY })
    }
}
