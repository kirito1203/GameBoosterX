package com.umbral.codex.core

import kotlin.random.Random

/** Modo de rolagem: vantagem/desvantagem rolam a expressão duas vezes e mantêm a maior/menor. */
enum class RollMode(val label: String) { NORMAL("Normal"), ADVANTAGE("Vantagem"), DISADVANTAGE("Desvantagem") }

/** Um grupo de dados da expressão, ex.: "3d6", "4d6kh3", "-1d4". */
data class DiceTerm(
    val count: Int,
    val sides: Int,
    val negative: Boolean = false,
    /** Mantém os N maiores (keepHigh) ou menores (keepLow). Null = mantém todos. */
    val keepHigh: Int? = null,
    val keepLow: Int? = null,
)

data class DiceExpression(val terms: List<DiceTerm>, val constant: Int) {
    val isEmpty get() = terms.isEmpty() && constant == 0

    /** Forma canônica, ex.: "2d6+1d4+3". */
    override fun toString(): String = buildString {
        terms.forEachIndexed { i, t ->
            if (t.negative) append("-") else if (i > 0) append("+")
            append("${t.count}d${if (t.sides == 100) "100" else t.sides}")
            t.keepHigh?.let { append("kh$it") }
            t.keepLow?.let { append("kl$it") }
        }
        if (constant > 0 && terms.isNotEmpty()) append("+$constant")
        else if (constant != 0) append(constant)
        if (isEmpty) append("0")
    }

    fun withModifier(extra: Int) = copy(constant = constant + extra)

    val minTotal: Int get() = terms.sumOf { t -> kept(t) * (if (t.negative) -t.sides else 1) } + constant
    val maxTotal: Int get() = terms.sumOf { t -> kept(t) * (if (t.negative) -1 else t.sides) } + constant

    private fun kept(t: DiceTerm) = t.keepHigh ?: t.keepLow ?: t.count
}

data class TermResult(val term: DiceTerm, val rolls: List<Int>, val keptIndices: Set<Int>) {
    val subtotal: Int get() = rolls.filterIndexed { i, _ -> i in keptIndices }.sum().let { if (term.negative) -it else it }
}

data class RollOutcome(
    val expression: DiceExpression,
    val results: List<TermResult>,
    val total: Int,
    val mode: RollMode = RollMode.NORMAL,
    /** Na vantagem/desvantagem, a rolagem descartada. */
    val discarded: RollOutcome? = null,
) {
    /** 20 natural em um teste de 1d20. */
    val isCritical: Boolean
        get() = singleD20()?.let { it == 20 } ?: false

    /** 1 natural em um teste de 1d20. */
    val isFumble: Boolean
        get() = singleD20()?.let { it == 1 } ?: false

    private fun singleD20(): Int? {
        val d20 = results.filter { it.term.sides == 20 && !it.term.negative }
        if (d20.size != 1) return null
        val kept = d20[0].rolls.filterIndexed { i, _ -> i in d20[0].keptIndices }
        return kept.singleOrNull()
    }

    /** Detalhamento legível: "1d20 [17] + 5 = 22". */
    fun detail(): String = buildString {
        results.forEachIndexed { i, r ->
            if (r.term.negative) append(" − ") else if (i > 0) append(" + ")
            append("${r.term.count}d${r.term.sides} [")
            append(r.rolls.mapIndexed { idx, v -> if (idx in r.keptIndices) "$v" else "~$v~" }.joinToString(", "))
            append("]")
        }
        val c = expression.constant
        if (c > 0) append(if (results.isEmpty()) "$c" else " + $c")
        if (c < 0) append(" − ${-c}")
        discarded?.let { append("  (descartado: ${it.total})") }
    }
}

class DiceParseException(message: String) : IllegalArgumentException(message)

object Dice {
    /** Dados oferecidos na interface. */
    val STANDARD = listOf(4, 6, 8, 10, 12, 20, 100)

    private const val MAX_DICE = 200
    private const val MAX_SIDES = 1000

    private val termRegex = Regex("""^(\d*)d(\d+|%)(?:(kh|kl|k)(\d+))?$""")

    /**
     * Interpreta expressões como "1d20+5", "3d6 + 2", "d100", "4d6kh3", "2d20kl1", "1d8+1d6-1".
     * Lança [DiceParseException] com uma mensagem em português se a expressão for inválida.
     */
    fun parse(input: String): DiceExpression {
        val s = input.lowercase().replace(" ", "").replace("−", "-").replace("–", "-")
        if (s.isEmpty()) throw DiceParseException("Digite uma expressão, ex.: 1d20+5")
        val terms = mutableListOf<DiceTerm>()
        var constant = 0
        var i = 0
        var totalDice = 0
        while (i < s.length) {
            var negative = false
            if (s[i] == '+' || s[i] == '-') {
                negative = s[i] == '-'
                i++
            } else if (i > 0) {
                throw DiceParseException("Símbolo inesperado em \"$input\"")
            }
            val start = i
            while (i < s.length && s[i] != '+' && s[i] != '-') i++
            val token = s.substring(start, i)
            if (token.isEmpty()) throw DiceParseException("Expressão incompleta: \"$input\"")
            val number = token.toIntOrNull()
            if (number != null) {
                constant += if (negative) -number else number
                continue
            }
            val m = termRegex.matchEntire(token)
                ?: throw DiceParseException("Não entendi \"$token\". Use o formato NdX, ex.: 2d6")
            val count = m.groupValues[1].ifEmpty { "1" }.toInt()
            val sides = m.groupValues[2].let { if (it == "%") 100 else it.toInt() }
            if (count < 1) throw DiceParseException("A quantidade de dados deve ser pelo menos 1")
            if (sides < 2 || sides > MAX_SIDES) throw DiceParseException("Um dado precisa ter de 2 a $MAX_SIDES faces")
            totalDice += count
            if (totalDice > MAX_DICE) throw DiceParseException("Máximo de $MAX_DICE dados por rolagem")
            var keepHigh: Int? = null
            var keepLow: Int? = null
            if (m.groupValues[3].isNotEmpty()) {
                val n = m.groupValues[4].toInt()
                if (n < 1 || n > count) throw DiceParseException("Manter $n de $count dados não é possível")
                if (m.groupValues[3] == "kl") keepLow = n else keepHigh = n
            }
            terms += DiceTerm(count, sides, negative, keepHigh, keepLow)
        }
        return DiceExpression(terms, constant)
    }

    fun tryParse(input: String): DiceExpression? = try { parse(input) } catch (_: DiceParseException) { null }

    fun roll(expression: DiceExpression, random: Random = Random.Default, mode: RollMode = RollMode.NORMAL): RollOutcome {
        val first = rollOnce(expression, random)
        if (mode == RollMode.NORMAL) return first
        val second = rollOnce(expression, random)
        val firstWins = if (mode == RollMode.ADVANTAGE) first.total >= second.total else first.total <= second.total
        val (kept, other) = if (firstWins) first to second else second to first
        return kept.copy(mode = mode, discarded = other)
    }

    fun roll(input: String, random: Random = Random.Default, mode: RollMode = RollMode.NORMAL): RollOutcome =
        roll(parse(input), random, mode)

    private fun rollOnce(expression: DiceExpression, random: Random): RollOutcome {
        val results = expression.terms.map { t ->
            val rolls = List(t.count) { random.nextInt(1, t.sides + 1) }
            val order = rolls.indices.sortedBy { rolls[it] }
            val kept = when {
                t.keepHigh != null -> order.takeLast(t.keepHigh).toSet()
                t.keepLow != null -> order.take(t.keepLow).toSet()
                else -> rolls.indices.toSet()
            }
            TermResult(t, rolls, kept)
        }
        return RollOutcome(expression, results, results.sumOf { it.subtotal } + expression.constant)
    }

    /** Monta uma expressão a partir do seletor visual (quantidade, faces, modificador). */
    fun build(count: Int, sides: Int, modifier: Int): DiceExpression =
        DiceExpression(listOf(DiceTerm(count.coerceIn(1, MAX_DICE), sides)), modifier)
}
