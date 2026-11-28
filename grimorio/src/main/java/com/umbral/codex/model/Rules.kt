package com.umbral.codex.model

import kotlinx.serialization.Serializable

/**
 * Regras de cálculo da ficha. Cada personagem tem as suas: o app não impõe uma fórmula única.
 */
@Serializable
data class RuleSet(
    val modifierRule: ModifierRule = ModifierRule.D20,
    /** Divisor usado por [ModifierRule.DIVIDED]. */
    val modifierDivisor: Int = 2,
    /** Valor de referência usado por [ModifierRule.D20] (o padrão clássico é 10). */
    val modifierBase: Int = 10,
    val proficiencyRule: ProficiencyRule = ProficiencyRule.MANUAL,
    /** Bônus extra somado a perícias marcadas como "treinadas". */
    val trainedBonus: Int = 0,
    val xpCurve: XpCurve = XpCurve.CLASSIC,
    /** Base para curvas linear e progressiva. */
    val xpBase: Long = 1000,
    val maxLevel: Int = 20,
    /** Ao subir de nível, mantém a experiência acumulada (true) ou zera a barra (false). */
    val carryOverXp: Boolean = true,
    /** Dado padrão de teste (perícias, atributos). Ex.: "1d20", "1d100", "2d6". */
    val checkDie: String = "1d20",
    /** Em sistemas de porcentagem (d100), o teste é "rolar abaixo" do valor. */
    val rollUnder: Boolean = false,
    /** Peso por ponto de Força (capacidade automática). 0 = capacidade manual. */
    val carryPerStrength: Double = 0.0,
)

enum class ModifierRule(val label: String, val description: String) {
    D20("Clássico (valor − 10) ÷ 2", "Modificador = (valor − base) ÷ 2, arredondado para baixo. Ex.: 16 → +3."),
    DIRECT("Direto", "O próprio valor é o modificador. Ex.: atributo 3 → +3."),
    DIVIDED("Dividido", "Modificador = valor ÷ divisor, arredondado para baixo."),
    MANUAL("Manual", "Você digita o modificador de cada atributo."),
}

enum class ProficiencyRule(val label: String) {
    MANUAL("Manual"),
    BY_LEVEL_D20("Automático por nível (+2 a +6)"),
}

enum class XpCurve(val label: String) {
    CLASSIC("Tabela clássica de 20 níveis"),
    LINEAR("Linear (nível × base)"),
    PROGRESSIVE("Progressiva (base × nível²)"),
    MANUAL("Manual"),
}
