package com.umbral.codex

import com.umbral.codex.core.SheetMath
import com.umbral.codex.core.SheetMath.CoinResult
import com.umbral.codex.core.SystemPreset
import com.umbral.codex.model.Attribute
import com.umbral.codex.model.Item
import com.umbral.codex.model.ModifierRule
import com.umbral.codex.model.Proficiency
import com.umbral.codex.model.RuleSet
import com.umbral.codex.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetMathTest {
    private val attr = Attribute("a", "Força", "FOR", value = 15)

    @Test fun modifierRules() {
        assertEquals(2, SheetMath.baseModifier(attr, RuleSet(modifierRule = ModifierRule.D20)))
        assertEquals(-1, SheetMath.baseModifier(attr.copy(value = 9), RuleSet(modifierRule = ModifierRule.D20)))
        assertEquals(15, SheetMath.baseModifier(attr, RuleSet(modifierRule = ModifierRule.DIRECT)))
        assertEquals(5, SheetMath.baseModifier(attr, RuleSet(modifierRule = ModifierRule.DIVIDED, modifierDivisor = 3)))
        assertEquals(7, SheetMath.baseModifier(attr.copy(manualModifier = 7), RuleSet(modifierRule = ModifierRule.MANUAL)))
        assertEquals(3, SheetMath.modifier(attr.copy(bonus = 2, penalty = 1), RuleSet()))
    }

    @Test fun skillTotalUsesAttributeAndProficiency() {
        val c = SystemPreset.CLASSIC.build("T").let { ch ->
            ch.copy(attributes = ch.attributes.map { if (it.abbr == "DES") it.copy(value = 16) else it }, level = 5)
        }.let(SheetMath::syncDerived)
        assertEquals(3, SheetMath.proficiency(c))
        val acro = c.skills.first { it.name == "Acrobacia" }
        assertEquals(3, SheetMath.skillTotal(acro, c))
        assertEquals(6, SheetMath.skillTotal(acro.copy(proficiency = Proficiency.PROFICIENT), c))
        assertEquals(9, SheetMath.skillTotal(acro.copy(proficiency = Proficiency.EXPERT), c))
        assertEquals(8, SheetMath.skillTotal(acro.copy(proficiency = Proficiency.EXPERT, bonus = 1, penalty = 2), c))
    }

    @Test fun xpAndLevelUp() {
        var c = SystemPreset.CLASSIC.build("T")
        assertEquals(300, c.xpToNext)
        c = SheetMath.addXp(c, 350)
        assertTrue(SheetMath.canLevelUp(c))
        c = SheetMath.levelUp(c)
        assertEquals(2, c.level); assertEquals(50, c.xp); assertEquals(600, c.xpToNext)
        repeat(30) { c = SheetMath.levelUp(c) }
        assertEquals(20, c.level); assertEquals(6, c.proficiencyBonus)
    }

    @Test fun carryAndOverload() {
        val base = SystemPreset.CLASSIC.build("T")
        assertEquals(75.0, SheetMath.carryCapacity(base), 0.001) // FOR 10 × 7,5
        val heavy = base.copy(items = listOf(Item("i", "Bigorna", quantity = 2, weight = 40.0)))
        assertEquals(80.0, SheetMath.currentLoad(heavy), 0.001)
        assertTrue(SheetMath.isOverloaded(heavy))
        assertFalse(SheetMath.isOverloaded(base))
    }

    @Test fun coins() {
        val c = SystemPreset.CLASSIC.build("T")
        val gold = c.currencies.first()
        val gained = (SheetMath.coinTransaction(c, gold.id, TransactionKind.GAIN, 100, "Recompensa", 1) as CoinResult.Ok).character
        assertEquals(100, gained.currencies.first().amount)
        val spent = (SheetMath.coinTransaction(gained, gold.id, TransactionKind.SPEND, 30, "Espada", 2) as CoinResult.Ok).character
        assertEquals(70, spent.currencies.first().amount)
        assertEquals(2, spent.transactions.size); assertEquals("Espada", spent.transactions.first().reason)
        assertTrue(SheetMath.coinTransaction(spent, gold.id, TransactionKind.SPEND, 500, "", 3) is CoinResult.Error)
        val set = (SheetMath.coinTransaction(spent, gold.id, TransactionKind.SET, 5, "Ajuste", 4) as CoinResult.Ok).character
        assertEquals(5, set.currencies.first().amount); assertEquals(-65, set.transactions.first().delta)
    }
}
