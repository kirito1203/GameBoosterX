package com.umbral.codex

import com.umbral.codex.core.Dice
import com.umbral.codex.core.DiceParseException
import com.umbral.codex.core.RollMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceTest {
    @Test fun parsesCommonExpressions() {
        assertEquals("1d20+5", Dice.parse("1d20 + 5").toString())
        assertEquals("3d6+2", Dice.parse("3D6+2").toString())
        assertEquals("1d100", Dice.parse("d100").toString())
        assertEquals("1d100", Dice.parse("1d%").toString())
        assertEquals("4d6kh3", Dice.parse("4d6k3").toString())
        assertEquals("1d8+1d6-1", Dice.parse("1d8+1d6-1").toString())
        assertEquals("-2", Dice.parse("-2").toString())
    }

    @Test fun rejectsInvalidExpressions() {
        listOf("", "abc", "1d1", "0d6", "2d6+", "5d6kh9", "1d20++2", "300d6").forEach {
            try { Dice.parse(it); throw AssertionError("deveria falhar: $it") } catch (_: DiceParseException) {}
        }
    }

    @Test fun totalsStayWithinBounds() {
        val r = Random(42)
        val exp = Dice.parse("3d6+2")
        repeat(500) {
            val o = Dice.roll(exp, r)
            assertTrue(o.total in 5..20)
            assertEquals(o.results.sumOf { it.subtotal } + 2, o.total)
        }
        assertEquals(5, exp.minTotal); assertEquals(20, exp.maxTotal)
    }

    @Test fun keepHighestKeepsCorrectDice() {
        val r = Random(7)
        repeat(200) {
            val o = Dice.roll("4d6kh3", r)
            val rolls = o.results[0].rolls
            assertEquals(rolls.sorted().takeLast(3).sum(), o.total)
        }
    }

    @Test fun advantageKeepsHigherAndDisadvantageLower() {
        val r = Random(3)
        repeat(200) {
            val adv = Dice.roll("1d20", r, RollMode.ADVANTAGE)
            assertNotNull(adv.discarded); assertTrue(adv.total >= adv.discarded!!.total)
            val dis = Dice.roll("1d20", r, RollMode.DISADVANTAGE)
            assertTrue(dis.total <= dis.discarded!!.total)
        }
    }

    @Test fun everyFaceAppearsAndCriticalIsDetected() {
        val r = Random(11)
        val seen = mutableSetOf<Int>()
        var crit = false; var fumble = false
        repeat(2000) {
            val o = Dice.roll("1d20+3", r)
            val face = o.results[0].rolls[0]; seen += face
            if (face == 20) { assertTrue(o.isCritical); crit = true }
            if (face == 1) { assertTrue(o.isFumble); fumble = true }
            if (face in 2..19) { assertFalse(o.isCritical || o.isFumble) }
        }
        assertEquals((1..20).toSet(), seen); assertTrue(crit && fumble)
    }

    @Test fun detailIsReadable() {
        val o = Dice.roll("2d6+3", Random(1))
        assertTrue(o.detail().startsWith("2d6 ["))
        assertTrue(o.detail().endsWith("+ 3"))
    }
}
