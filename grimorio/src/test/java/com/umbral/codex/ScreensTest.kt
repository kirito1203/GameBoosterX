package com.umbral.codex

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Teste de fumaça das telas reais (Robolectric + gráficos nativos): percorre o app como um
 * usuário — início, exemplos, catálogos, biblioteca, todas as abas da ficha, rolagem de dados,
 * criação e ajustes — e salva capturas em build/screenshots para conferência visual.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w400dp-h860dp-xhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val outDir = File(System.getProperty("codex.screenshots") ?: "build/screenshots").apply { mkdirs() }

    private fun settle(ms: Long = 1200) {
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    /** Desenha todas as janelas visíveis (tela + diálogos) num único bitmap. */
    private fun shot(name: String) {
        settle(400)
        val views = rootViews().filter { it.isShown && it.width > 0 }
        val base = views.first()
        val bmp = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        views.forEach { v ->
            val loc = IntArray(2); v.getLocationOnScreen(loc)
            val baseLoc = IntArray(2); base.getLocationOnScreen(baseLoc)
            canvas.save()
            if (v !== base) canvas.drawColor(0x99000000.toInt())
            canvas.translate((loc[0] - baseLoc[0]).toFloat(), (loc[1] - baseLoc[1]).toFloat())
            v.draw(canvas)
            canvas.restore()
        }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rootViews(): List<android.view.View> {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val global = cls.getMethod("getInstance").invoke(null)
        val field = cls.getDeclaredField("mViews").apply { isAccessible = true }
        return (field.get(global) as List<android.view.View>).toList()
    }

    private fun tab(label: String) {
        rule.onAllNodes(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).onFirst().performScrollToSafe().performClick()
        settle()
    }

    private fun click(text: String, substring: Boolean = false) {
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).onFirst().performScrollToSafe().performClick()
        settle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToSafe() =
        try { performScrollTo() } catch (_: Throwable) { this }

    @Test
    fun walkThroughTheApp() {
        val repo = (rule.activity.application as CodexApplication).repository
        val start = System.currentTimeMillis()
        while (!repo.loaded.value && System.currentTimeMillis() - start < 20_000) Thread.sleep(50)
        assertTrue("o repositório deve carregar", repo.loaded.value)
        settle()
        shot("01_inicio_vazio")

        click("Carregar exemplos fictícios")
        assertEquals(5, repo.characters.value.size)
        shot("02_inicio_com_exemplos")

        tab("Catálogos"); shot("03_catalogos")
        click("Dark Fantasy"); shot("04_catalogo_dark_fantasy")
        click("Raças", substring = true); shot("05_catalogo_racas")
        click("Modelos", substring = true); shot("06_catalogo_modelos")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle()

        tab("Biblioteca"); shot("07_biblioteca")
        click("Aldric Venn")
        shot("10_ficha")
        listOf("Atributos", "Perícias", "Magia", "Poderes", "Inventário", "Dados", "História", "Outras informações").forEachIndexed { i, t ->
            tab(t); shot("1${i + 1}_aba_${t.lowercase().replace(' ', '_')}")
        }
        // Rolagem real na aba Dados
        tab("Dados")
        click("Rolar 1d20", substring = true)
        settle(1500)
        val rolls = repo.characters.value.first { it.name == "Aldric Venn" }.rollHistory
        assertTrue("a rolagem deve ir para o histórico", rolls.isNotEmpty())
        assertTrue(rolls.first().total in 1..20)
        shot("19_dados_rolados")

        // Magia: expandir e conjurar desconta mana
        tab("Magia")
        val before = repo.characters.value.first { it.name == "Aldric Venn" }.resources.first { it.name == "Mana" }.current
        click("Mísseis arcanos"); click("Conjurar")
        val after = repo.characters.value.first { it.name == "Aldric Venn" }.resources.first { it.name == "Mana" }.current
        assertEquals(before - 2, after)
        shot("20_magia_conjurada")

        tab("Inventário"); click("Ouro", substring = false)
        shot("21_moeda_dialogo")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle()

        tab("Criar"); shot("30_criar")
        click("Usar modelo"); shot("31_criar_modelo")
        tab("Ajustes"); shot("40_ajustes")
    }
}
