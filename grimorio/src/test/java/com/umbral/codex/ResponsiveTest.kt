package com.umbral.codex

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Responsividade: abre as telas principais num celular pequeno e num tablet, verifica que
 * nenhum elemento ultrapassa a largura da janela e salva capturas para conferência.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResponsiveTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val outDir = File(System.getProperty("codex.screenshots") ?: "build/screenshots").apply { mkdirs() }

    private fun settle() { rule.mainClock.advanceTimeBy(900); rule.waitForIdle() }

    private fun shot(name: String) {
        settle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Nenhum nó visível pode começar fora da tela ou terminar além da borda direita. */
    private fun assertNoHorizontalOverflow(screen: String, pagerLevels: Int = 0) {
        val width = rule.activity.window.decorView.width
        val all = rule.onAllNodes(SemanticsMatcher("todos") { true }, useUnmergedTree = true).fetchSemanticsNodes()
        val offenders = all.filter { n: SemanticsNode ->
            val b = n.boundsInWindow
            // Ignora conteúdo de listas/rolagens horizontais (chips, carrosséis), que é rolável por design.
            // Só conta quem está parcialmente visível e cruza a borda (páginas vizinhas do pager ficam inteiras fora).
            horizontalScrollDepth(n) <= pagerLevels && b.width > 0 &&
                ((b.left < width - 2 && b.right > width + 2) || (b.left < -2 && b.right > 2))
        }
        assertTrue("$screen: ${offenders.size} elementos ultrapassam a largura: " +
            offenders.take(5).joinToString { it.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty() + " " + it.boundsInWindow },
            offenders.isEmpty())
    }

    private fun horizontalScrollDepth(n: SemanticsNode): Int {
        var p: SemanticsNode? = n.parent
        var depth = 0
        while (p != null) {
            if (p.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null) depth++
            p = p.parent
        }
        return depth
    }

    private fun tab(t: String) {
        rule.onAllNodes(hasText(t) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).onFirst().performClick(); settle()
    }

    private fun walk(prefix: String) {
        val repo = (rule.activity.application as CodexApplication).repository
        val start = System.currentTimeMillis()
        while (!repo.loaded.value && System.currentTimeMillis() - start < 20_000) Thread.sleep(50)
        settle()
        rule.onAllNodesWithText("Carregar exemplos fictícios", useUnmergedTree = true).onFirst().apply { performScrollTo(); performClick() }; settle()
        rule.onAllNodesWithText("Fichas, universos", substring = true, useUnmergedTree = true).onFirst().performScrollTo(); settle()
        shot("${prefix}_1_inicio"); assertNoHorizontalOverflow("Início")
        tab("Catálogos"); shot("${prefix}_2_catalogos"); assertNoHorizontalOverflow("Catálogos")
        tab("Biblioteca"); assertNoHorizontalOverflow("Biblioteca")
        rule.onAllNodesWithText("Lysandra Vael", useUnmergedTree = true).onFirst().performClick(); settle()
        shot("${prefix}_3_ficha"); assertNoHorizontalOverflow("Ficha", 1)
        listOf("Atributos", "Perícias", "Magia", "Poderes", "Inventário", "Dados", "História", "Outras informações").forEach { t ->
            tab(t); assertNoHorizontalOverflow(t, 1)
        }
        tab("Dados"); shot("${prefix}_4_dados")
        tab("Inventário"); shot("${prefix}_5_inventario")
    }

    @Test @Config(sdk = [33], qualifiers = "w320dp-h640dp-mdpi")
    fun smallPhone() = walk("p320")

    @Test @Config(sdk = [33], qualifiers = "w840dp-h1280dp-mdpi")
    fun tablet() = walk("t840")
}
