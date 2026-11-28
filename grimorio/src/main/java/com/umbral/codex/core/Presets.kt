package com.umbral.codex.core

import com.umbral.codex.model.Attribute
import com.umbral.codex.model.Character
import com.umbral.codex.model.Currency
import com.umbral.codex.model.ModifierRule
import com.umbral.codex.model.ProficiencyRule
import com.umbral.codex.model.Resource
import com.umbral.codex.model.ResourceDisplay
import com.umbral.codex.model.RuleSet
import com.umbral.codex.model.SheetSection
import com.umbral.codex.model.Skill
import com.umbral.codex.model.XpCurve
import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

/** Cores padrão dos recursos (ARGB). */
object ResourceColors {
    const val LIFE = 0xFFC2415BL
    const val MANA = 0xFF5B7CF0L
    const val STAMINA = 0xFF5FAF7AL
    const val ENERGY = 0xFFE0A84AL
    const val SPIRIT = 0xFF9D6BFFL
    const val SANITY = 0xFF4FB3C7L
    const val CORRUPTION = 0xFF7A2E8CL
    const val BLOOD = 0xFF8E1B2EL
    val palette = listOf(LIFE, MANA, STAMINA, ENERGY, SPIRIT, SANITY, CORRUPTION, BLOOD, 0xFFB8B2C8L, 0xFFC9A55CL)
}

/**
 * Sistemas de regras iniciais. Servem só de ponto de partida: atributos, recursos e perícias
 * continuam totalmente editáveis em cada ficha.
 */
enum class SystemPreset(val label: String, val description: String) {
    CLASSIC(
        "Clássico d20",
        "Seis atributos, modificador (valor − 10) ÷ 2, proficiência por nível e 19 perícias.",
    ),
    SUPERNATURAL(
        "Sobrenatural d20",
        "Base clássica com Humanidade, Essência, Energia espiritual e Sanidade.",
    ),
    INVESTIGATION(
        "Horror investigativo d100",
        "Atributos e perícias em porcentagem, testes rolando abaixo do valor e Sanidade.",
    ),
    FREEFORM(
        "Sistema livre",
        "Sem cálculos automáticos: você define modificadores, recursos e regras.",
    ),
    ;

    fun build(name: String = "", catalogId: String? = null, now: Long = System.currentTimeMillis()): Character {
        val base = when (this) {
            CLASSIC -> classic()
            SUPERNATURAL -> supernatural()
            INVESTIGATION -> investigation()
            FREEFORM -> freeform()
        }
        return SheetMath.syncDerived(
            base.copy(name = name, catalogId = catalogId, createdAt = now, updatedAt = now, lastOpenedAt = now)
        )
    }

    companion object {
        fun attr(name: String, abbr: String, value: Int = 10, desc: String = "") =
            Attribute(Ids.new(), name, abbr, value, description = desc)

        fun res(name: String, cur: Int, max: Int, color: Long, display: ResourceDisplay = ResourceDisplay.BOTH) =
            Resource(Ids.new(), name, cur, max, 0, color, display)

        fun classicAttributes() = listOf(
            attr("Força", "FOR", desc = "Potência física, ataques corpo a corpo e carga."),
            attr("Destreza", "DES", desc = "Agilidade, reflexos, pontaria e equilíbrio."),
            attr("Constituição", "CON", desc = "Vitalidade, resistência e fôlego."),
            attr("Inteligência", "INT", desc = "Raciocínio, memória e estudo arcano."),
            attr("Sabedoria", "SAB", desc = "Percepção, intuição e força de vontade."),
            attr("Carisma", "CAR", desc = "Presença, persuasão e liderança."),
        )

        /** Perícias padrão ligadas aos atributos pela abreviação. */
        fun classicSkills(attributes: List<Attribute>): List<Skill> {
            fun a(abbr: String) = attributes.firstOrNull { it.abbr == abbr }?.id
            return listOf(
                "Acrobacia" to "DES", "Atletismo" to "FOR", "Arcanismo" to "INT", "Atuação" to "CAR",
                "Enganação" to "CAR", "Furtividade" to "DES", "História" to "INT", "Intimidação" to "CAR",
                "Intuição" to "SAB", "Investigação" to "INT", "Lidar com animais" to "SAB", "Medicina" to "SAB",
                "Natureza" to "INT", "Ocultismo" to "INT", "Percepção" to "SAB", "Persuasão" to "CAR",
                "Prestidigitação" to "DES", "Religião" to "INT", "Sobrevivência" to "SAB",
            ).map { (n, ab) -> Skill(Ids.new(), n, a(ab)) }
        }

        fun classicCoins() = listOf(
            Currency(Ids.new(), "Ouro", "PO", 0, 0xFFE0B64AL),
            Currency(Ids.new(), "Prata", "PP", 0, 0xFFB8BCC8L),
            Currency(Ids.new(), "Cobre", "PC", 0, 0xFFB8734AL),
        )

        private fun classic(): Character {
            val attrs = classicAttributes()
            return Character(
                id = Ids.new(),
                attributes = attrs,
                skills = classicSkills(attrs),
                resources = listOf(
                    res("Vida", 12, 12, ResourceColors.LIFE),
                    res("Mana", 6, 6, ResourceColors.MANA),
                    res("Vigor", 10, 10, ResourceColors.STAMINA),
                ),
                currencies = classicCoins(),
                rules = RuleSet(proficiencyRule = ProficiencyRule.BY_LEVEL_D20, xpCurve = XpCurve.CLASSIC, carryPerStrength = 7.5),
            )
        }

        private fun supernatural(): Character {
            val attrs = classicAttributes() + listOf(
                attr("Humanidade", "HUM", 7, "O quanto resta de humano. Cai com atos monstruosos."),
                attr("Essência", "ESS", 10, "Força da natureza sobrenatural do personagem."),
            )
            return Character(
                id = Ids.new(),
                attributes = attrs,
                skills = classicSkills(attrs),
                resources = listOf(
                    res("Vida", 14, 14, ResourceColors.LIFE),
                    res("Energia espiritual", 10, 10, ResourceColors.SPIRIT),
                    res("Sanidade", 50, 50, ResourceColors.SANITY),
                    res("Vigor", 10, 10, ResourceColors.STAMINA),
                ),
                currencies = classicCoins(),
                rules = RuleSet(proficiencyRule = ProficiencyRule.BY_LEVEL_D20, xpCurve = XpCurve.CLASSIC, carryPerStrength = 7.5),
            )
        }

        private fun investigation(): Character {
            val attrs = listOf(
                attr("Força", "FOR", 50), attr("Destreza", "DES", 50), attr("Constituição", "CON", 50),
                attr("Inteligência", "INT", 60), attr("Poder", "POD", 50, "Força de vontade e afinidade com o oculto."),
                attr("Aparência", "APA", 50), attr("Educação", "EDU", 60),
            )
            fun a(abbr: String) = attrs.firstOrNull { it.abbr == abbr }?.id
            val skills = listOf(
                Triple("Investigação", "INT", 25), Triple("Ocultismo", "INT", 5), Triple("Percepção", "POD", 25),
                Triple("Furtividade", "DES", 20), Triple("Persuasão", "APA", 15), Triple("Intimidação", "FOR", 15),
                Triple("Medicina", "EDU", 5), Triple("História", "EDU", 10), Triple("Biblioteconomia", "EDU", 20),
                Triple("Psicologia", "INT", 10), Triple("Primeiros socorros", "EDU", 30), Triple("Armas de fogo", "DES", 20),
                Triple("Luta", "FOR", 25), Triple("Esquiva", "DES", 25), Triple("Chaveiro", "DES", 1),
                Triple("Sobrevivência", "CON", 10), Triple("Religião", "EDU", 10),
            ).map { (n, ab, base) -> Skill(Ids.new(), n, a(ab), base = base) }
            return Character(
                id = Ids.new(),
                attributes = attrs,
                skills = skills,
                resources = listOf(
                    res("Vida", 11, 11, ResourceColors.LIFE),
                    res("Sanidade", 50, 99, ResourceColors.SANITY),
                    res("Sorte", 50, 99, ResourceColors.ENERGY, ResourceDisplay.NUMBER),
                    res("Pontos de magia", 10, 10, ResourceColors.SPIRIT),
                ),
                currencies = listOf(Currency(Ids.new(), "Dinheiro", "$", 0, 0xFF5FAF7AL)),
                speed = "8",
                armorClass = 0,
                hiddenSections = setOf(SheetSection.MAGIC.key),
                fieldLabels = mapOf("armorClass" to "Armadura", "charClass" to "Ocupação", "race" to "Origem"),
                rules = RuleSet(
                    modifierRule = ModifierRule.MANUAL, proficiencyRule = ProficiencyRule.MANUAL,
                    xpCurve = XpCurve.MANUAL, checkDie = "1d100", rollUnder = true,
                ),
                proficiencyBonus = 0,
                xpToNext = 10,
            )
        }

        private fun freeform(): Character {
            val attrs = classicAttributes()
            return Character(
                id = Ids.new(),
                attributes = attrs,
                skills = classicSkills(attrs),
                resources = listOf(
                    res("Vida", 100, 100, ResourceColors.LIFE),
                    res("Energia", 100, 100, ResourceColors.ENERGY),
                ),
                currencies = classicCoins(),
                rules = RuleSet(modifierRule = ModifierRule.MANUAL, proficiencyRule = ProficiencyRule.MANUAL, xpCurve = XpCurve.MANUAL),
                xpToNext = 100,
            )
        }
    }
}

/** Cópia profunda de fichas e modelos: novos ids para tudo, preservando ligações perícia → atributo. */
object Blueprints {
    fun instantiate(source: Character, now: Long, name: String? = null, catalogId: String? = source.catalogId): Character {
        val attrMap = source.attributes.associate { it.id to Ids.new() }
        val coinMap = source.currencies.associate { it.id to Ids.new() }
        return source.copy(
            id = Ids.new(),
            name = name ?: source.name,
            catalogId = catalogId,
            attributes = source.attributes.map { it.copy(id = attrMap.getValue(it.id)) },
            skills = source.skills.map { it.copy(id = Ids.new(), attributeId = it.attributeId?.let(attrMap::get)) },
            resources = source.resources.map { it.copy(id = Ids.new()) },
            spells = source.spells.map { it.copy(id = Ids.new()) },
            abilities = source.abilities.map { it.copy(id = Ids.new()) },
            traits = source.traits.map { it.copy(id = Ids.new()) },
            items = source.items.map { it.copy(id = Ids.new()) },
            currencies = source.currencies.map { it.copy(id = coinMap.getValue(it.id)) },
            transactions = source.transactions.map {
                it.copy(id = Ids.new(), currencyId = coinMap[it.currencyId] ?: it.currencyId)
            },
            customFields = source.customFields.map { it.copy(id = Ids.new()) },
            rollHistory = emptyList(),
            favorite = false,
            isDemo = false,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = now,
        )
    }

    fun duplicate(source: Character, now: Long): Character =
        instantiate(source, now, name = "${source.name.ifBlank { "Sem nome" }} (cópia)")
}
