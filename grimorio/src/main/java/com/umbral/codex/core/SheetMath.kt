package com.umbral.codex.core

import com.umbral.codex.model.Attribute
import com.umbral.codex.model.Character
import com.umbral.codex.model.CoinTransaction
import com.umbral.codex.model.ModifierRule
import com.umbral.codex.model.ProficiencyRule
import com.umbral.codex.model.RuleSet
import com.umbral.codex.model.Skill
import com.umbral.codex.model.TransactionKind
import com.umbral.codex.model.XpCurve
import kotlin.math.floor
import kotlin.math.roundToInt

/** Cálculos da ficha. Lógica pura, sem Android, coberta por testes. */
object SheetMath {

    /** Modificador base do atributo segundo a regra configurada na ficha. */
    fun baseModifier(attr: Attribute, rules: RuleSet): Int = when (rules.modifierRule) {
        ModifierRule.D20 -> floor((attr.value - rules.modifierBase) / 2.0).toInt()
        ModifierRule.DIRECT -> attr.value
        ModifierRule.DIVIDED -> floor(attr.value / rules.modifierDivisor.coerceAtLeast(1).toDouble()).toInt()
        ModifierRule.MANUAL -> attr.manualModifier
    }

    /** Modificador final: base + bônus − penalidade. */
    fun modifier(attr: Attribute, rules: RuleSet): Int = baseModifier(attr, rules) + attr.bonus - attr.penalty

    fun signed(n: Int): String = if (n >= 0) "+$n" else "−${-n}"

    /** Bônus de proficiência automático (+2 nos níveis 1–4, +3 nos 5–8 ... +6 nos 17–20). */
    fun proficiencyForLevel(level: Int): Int = 2 + (level.coerceAtLeast(1) - 1) / 4

    fun proficiency(c: Character): Int = when (c.rules.proficiencyRule) {
        ProficiencyRule.MANUAL -> c.proficiencyBonus
        ProficiencyRule.BY_LEVEL_D20 -> proficiencyForLevel(c.level)
    }

    fun attributeModifier(c: Character, attributeId: String?): Int {
        val attr = c.attributes.firstOrNull { it.id == attributeId } ?: return 0
        return modifier(attr, c.rules)
    }

    /** Total da perícia = base + mod. do atributo + proficiência × multiplicador + treino + bônus − penalidade. */
    fun skillTotal(skill: Skill, c: Character): Int {
        val prof = (proficiency(c) * skill.proficiency.multiplier).let { floor(it).toInt() }
        val trained = if (skill.trained) c.rules.trainedBonus else 0
        return skill.base + attributeModifier(c, skill.attributeId) + prof + trained + skill.bonus - skill.penalty
    }

    // ---------------- Experiência e níveis ----------------

    /** XP total para alcançar cada nível na tabela clássica (índice 0 = nível 1). */
    private val CLASSIC_TABLE = longArrayOf(
        0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000, 64000,
        85000, 100000, 120000, 140000, 165000, 195000, 225000, 265000, 305000, 355000,
    )

    /**
     * Experiência necessária para sair do [level] e chegar ao seguinte (a barra é por nível).
     * Retorna null para a curva manual (o usuário define o valor).
     */
    fun xpRequirement(level: Int, rules: RuleSet): Long? {
        val l = level.coerceAtLeast(1)
        return when (rules.xpCurve) {
            XpCurve.CLASSIC -> {
                if (l >= CLASSIC_TABLE.size) 50000L
                else CLASSIC_TABLE[l] - CLASSIC_TABLE[l - 1]
            }
            XpCurve.LINEAR -> rules.xpBase.coerceAtLeast(1) * l
            XpCurve.PROGRESSIVE -> rules.xpBase.coerceAtLeast(1) * l * l
            XpCurve.MANUAL -> null
        }
    }

    fun xpProgress(c: Character): Float =
        if (c.xpToNext <= 0) 0f else (c.xp.toFloat() / c.xpToNext).coerceIn(0f, 1f)

    fun canLevelUp(c: Character): Boolean = c.level < c.rules.maxLevel && c.xpToNext > 0 && c.xp >= c.xpToNext

    fun addXp(c: Character, amount: Long): Character = c.copy(xp = (c.xp + amount).coerceAtLeast(0))

    /** Sobe um nível (mesmo sem XP suficiente, se o mestre permitir). */
    fun levelUp(c: Character): Character {
        if (c.level >= c.rules.maxLevel) return c
        val newLevel = c.level + 1
        val leftover = if (c.rules.carryOverXp) (c.xp - c.xpToNext).coerceAtLeast(0) else 0
        val next = xpRequirement(newLevel, c.rules) ?: c.xpToNext
        val leveled = c.copy(level = newLevel, xp = leftover, xpToNext = next)
        return if (c.rules.proficiencyRule == ProficiencyRule.BY_LEVEL_D20)
            leveled.copy(proficiencyBonus = proficiencyForLevel(newLevel)) else leveled
    }

    /** Recalcula XP necessária e proficiência quando nível ou regras mudam. */
    fun syncDerived(c: Character): Character {
        var r = c
        xpRequirement(c.level, c.rules)?.let { r = r.copy(xpToNext = it) }
        if (c.rules.proficiencyRule == ProficiencyRule.BY_LEVEL_D20) r = r.copy(proficiencyBonus = proficiencyForLevel(c.level))
        return r
    }

    // ---------------- Carga ----------------

    fun currentLoad(c: Character): Double = c.items.sumOf { it.weight * it.quantity.coerceAtLeast(0) }

    fun carryCapacity(c: Character): Double {
        if (c.rules.carryPerStrength > 0) {
            val str = c.attributes.firstOrNull {
                it.name.startsWith("For", ignoreCase = true) || it.abbr.equals("FOR", ignoreCase = true)
            }
            if (str != null) return str.value * c.rules.carryPerStrength
        }
        return c.carryCapacity
    }

    fun isOverloaded(c: Character): Boolean = currentLoad(c) > carryCapacity(c)

    fun formatWeight(w: Double): String =
        if (w == floor(w)) w.toLong().toString() else ((w * 10).roundToInt() / 10.0).toString().replace('.', ',')

    // ---------------- Moedas ----------------

    sealed interface CoinResult {
        data class Ok(val character: Character) : CoinResult
        data class Error(val message: String) : CoinResult
    }

    fun coinTransaction(
        c: Character,
        currencyId: String,
        kind: TransactionKind,
        amount: Long,
        reason: String,
        now: Long,
        id: String = Ids.new(),
    ): CoinResult {
        val cur = c.currencies.firstOrNull { it.id == currencyId } ?: return CoinResult.Error("Moeda não encontrada")
        if (amount < 0) return CoinResult.Error("Informe um valor positivo")
        val newAmount = when (kind) {
            TransactionKind.GAIN -> cur.amount + amount
            TransactionKind.SPEND -> {
                if (amount > cur.amount) return CoinResult.Error("Saldo insuficiente: ${cur.amount} ${cur.abbr.ifBlank { cur.name }}")
                cur.amount - amount
            }
            TransactionKind.SET -> amount
        }
        if (kind != TransactionKind.SET && amount == 0L) return CoinResult.Error("Informe um valor maior que zero")
        val tx = CoinTransaction(
            id = id, currencyId = cur.id, currencyName = cur.name,
            delta = newAmount - cur.amount, balanceAfter = newAmount,
            reason = reason.trim(), kind = kind, timestamp = now,
        )
        return CoinResult.Ok(
            c.copy(
                currencies = c.currencies.map { if (it.id == cur.id) it.copy(amount = newAmount) else it },
                transactions = (listOf(tx) + c.transactions).take(500),
            )
        )
    }
}
