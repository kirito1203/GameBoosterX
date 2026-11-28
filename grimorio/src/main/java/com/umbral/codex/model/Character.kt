package com.umbral.codex.model

import kotlinx.serialization.Serializable

/**
 * Ficha completa de um personagem. Tudo é serializável em JSON: o mesmo formato é usado
 * no armazenamento interno, na exportação de fichas e no backup.
 *
 * Os campos têm valores padrão para que fichas antigas continuem abrindo quando novos campos
 * forem adicionados (o Json é configurado com ignoreUnknownKeys e coerceInputValues).
 */
@Serializable
data class Character(
    val id: String,
    val name: String = "",
    val portraitId: String? = null,
    val age: String = "",
    val gender: String = "",
    val race: String = "",
    val charClass: String = "",
    val subclass: String = "",
    val level: Int = 1,
    val background: String = "",
    val alignment: String = "",
    val title: String = "",
    val faction: String = "",
    /** Id do catálogo (universo/gênero) ao qual a ficha pertence. */
    val catalogId: String? = null,

    // Progressão
    val xp: Long = 0,
    val xpToNext: Long = 300,
    val proficiencyBonus: Int = 2,
    val initiative: Int = 0,
    val speed: String = "9 m",
    val armorClass: Int = 10,

    val attributes: List<Attribute> = emptyList(),
    val resources: List<Resource> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val spells: List<Spell> = emptyList(),
    val abilities: List<Ability> = emptyList(),
    val traits: List<Trait> = emptyList(),
    val items: List<Item> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val transactions: List<CoinTransaction> = emptyList(),
    val carryCapacity: Double = 75.0,
    val weightUnit: String = "kg",
    val story: Story = Story(),
    val notes: String = "",
    val languages: String = "",
    val customFields: List<CustomField> = emptyList(),
    val rollHistory: List<RollRecord> = emptyList(),

    val rules: RuleSet = RuleSet(),
    /** Abas ocultas pelo usuário (chaves de [SheetSection]). */
    val hiddenSections: Set<String> = emptySet(),
    /** Rótulos personalizados de campos (chave do campo → nome exibido). */
    val fieldLabels: Map<String, String> = emptyMap(),
    /** Nome das categorias personalizadas de habilidades criadas pelo usuário. */
    val abilityCategories: List<String> = emptyList(),

    val favorite: Boolean = false,
    /** Conteúdo fictício de demonstração (pode ser removido em Configurações). */
    val isDemo: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastOpenedAt: Long = 0,
)

/** Abas da ficha. A ordem aqui é a ordem de exibição. */
enum class SheetSection(val key: String, val label: String, val canHide: Boolean = true) {
    SHEET("ficha", "Ficha", canHide = false),
    ATTRIBUTES("atributos", "Atributos"),
    SKILLS("pericias", "Perícias"),
    MAGIC("magia", "Magia"),
    POWERS("poderes", "Poderes"),
    INVENTORY("inventario", "Inventário"),
    DICE("dados", "Dados"),
    STORY("historia", "História"),
    OTHER("outros", "Outras informações", canHide = false),
}

@Serializable
data class Attribute(
    val id: String,
    val name: String,
    val abbr: String = "",
    val value: Int = 10,
    val bonus: Int = 0,
    val penalty: Int = 0,
    /** Usado quando a regra de modificador é manual. */
    val manualModifier: Int = 0,
    val description: String = "",
)

enum class ResourceDisplay(val label: String) { BAR("Barra"), NUMBER("Número"), BOTH("Barra e número") }

@Serializable
data class Resource(
    val id: String,
    val name: String,
    val current: Int = 10,
    val max: Int = 10,
    val min: Int = 0,
    /** Cor ARGB. */
    val color: Long = 0xFF8B5CF6,
    val display: ResourceDisplay = ResourceDisplay.BOTH,
    val step: Int = 1,
)

enum class Proficiency(val label: String, val multiplier: Double) {
    NONE("Nenhuma", 0.0),
    HALF("Metade", 0.5),
    PROFICIENT("Proficiente", 1.0),
    EXPERT("Especialista", 2.0),
}

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val attributeId: String? = null,
    val base: Int = 0,
    val trained: Boolean = false,
    val proficiency: Proficiency = Proficiency.NONE,
    val bonus: Int = 0,
    val penalty: Int = 0,
    val description: String = "",
)

@Serializable
data class Spell(
    val id: String,
    val name: String,
    val icon: String = "auto_fix",
    val imageId: String? = null,
    val description: String = "",
    val type: String = "",
    val level: Int = 0,
    val school: String = "",
    val manaCost: Int = 0,
    val energyCost: Int = 0,
    val castTime: String = "",
    val range: String = "",
    val duration: String = "",
    /** Expressão de dados rolável, ex.: "2d8+3". */
    val damage: String = "",
    val effect: String = "",
    val requirements: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
    val available: Boolean = true,
)

@Serializable
data class Ability(
    val id: String,
    val name: String,
    val category: String = AbilityCategories.ACTIVE,
    val icon: String = "bolt",
    val imageId: String? = null,
    val description: String = "",
    val effect: String = "",
    val cost: String = "",
    val cooldown: String = "",
    val requirements: String = "",
    val levelRequired: Int = 0,
    val bonus: String = "",
    val penalty: String = "",
    /** Expressão de dados opcional (dano/cura), ex.: "1d10+2". */
    val dice: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
)

object AbilityCategories {
    const val ACTIVE = "Habilidade ativa"
    const val PASSIVE = "Habilidade passiva"
    const val TECHNIQUE = "Técnica especial"
    const val RACIAL = "Poder racial"
    const val TRANSFORMATION = "Transformação"
    const val SUPERNATURAL = "Poder sobrenatural"
    const val DEMONIC = "Poder demoníaco"
    const val DIVINE = "Poder divino"
    const val COMBAT = "Habilidade de combate"
    const val TALENT = "Talento"

    val defaults = listOf(ACTIVE, PASSIVE, TECHNIQUE, RACIAL, TRANSFORMATION, SUPERNATURAL, DEMONIC, DIVINE, COMBAT, TALENT)
}

@Serializable
data class Trait(
    val id: String,
    val name: String,
    val type: String = "Natural",
    val description: String = "",
    val effect: String = "",
    val requirements: String = "",
    val limitations: String = "",
    val notes: String = "",
)

object TraitTypes {
    val defaults = listOf("Natural", "Sobrenatural", "Racial", "Adquirida", "Maldição", "Bênção")
}

enum class Rarity(val label: String, val color: Long) {
    COMMON("Comum", 0xFF9A93A8),
    UNCOMMON("Incomum", 0xFF5FAF7A),
    RARE("Raro", 0xFF5B8BEA),
    EPIC("Épico", 0xFFA66BFF),
    LEGENDARY("Lendário", 0xFFE0A84A),
    ARTIFACT("Artefato", 0xFFD9534F),
}

object ItemCategories {
    val defaults = listOf(
        "Arma", "Armadura", "Escudo", "Poção", "Relíquia", "Artefato mágico",
        "Equipamento", "Objeto comum", "Item especial",
    )
}

@Serializable
data class Item(
    val id: String,
    val name: String,
    val icon: String = "inventory",
    val imageId: String? = null,
    val description: String = "",
    val quantity: Int = 1,
    /** Peso unitário. */
    val weight: Double = 0.0,
    val value: String = "",
    val rarity: Rarity = Rarity.COMMON,
    val category: String = "Objeto comum",
    val effects: String = "",
    val bonus: String = "",
    /** Expressão de dados opcional (dano da arma, cura da poção). */
    val dice: String = "",
    val notes: String = "",
    val equipped: Boolean = false,
    val equippable: Boolean = true,
)

@Serializable
data class Currency(
    val id: String,
    val name: String,
    val abbr: String = "",
    val amount: Long = 0,
    val color: Long = 0xFFC9A55C,
)

@Serializable
data class CoinTransaction(
    val id: String,
    val currencyId: String,
    val currencyName: String,
    val delta: Long,
    val balanceAfter: Long,
    val reason: String = "",
    val kind: TransactionKind = TransactionKind.GAIN,
    val timestamp: Long = 0,
)

enum class TransactionKind(val label: String) { GAIN("Ganho"), SPEND("Gasto"), SET("Saldo definido") }

@Serializable
data class Story(
    val origin: String = "",
    val childhood: String = "",
    val family: String = "",
    val personality: String = "",
    val appearance: String = "",
    val goals: String = "",
    val motivations: String = "",
    val fears: String = "",
    val secrets: String = "",
    val relations: String = "",
    val events: String = "",
    val freeText: String = "",
)

@Serializable
data class CustomField(val id: String, val name: String, val value: String = "")

@Serializable
data class RollRecord(
    val id: String,
    val label: String,
    val expression: String,
    val detail: String,
    val total: Int,
    val critical: Boolean = false,
    val fumble: Boolean = false,
    val timestamp: Long = 0,
)
