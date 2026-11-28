package com.umbral.codex.data

import com.umbral.codex.core.Blueprints
import com.umbral.codex.core.Ids
import com.umbral.codex.core.SheetMath
import com.umbral.codex.model.Catalog
import com.umbral.codex.model.Character
import com.umbral.codex.model.Story

/**
 * Personagens FICTÍCIOS de demonstração. Só são criados se o usuário pedir, ficam marcados
 * como "Exemplo" ([Character.isDemo]) e podem ser removidos de uma vez em Configurações.
 */
object DemoContent {

    private data class Spec(
        val catalogId: String,
        val templateName: String,
        val name: String,
        val level: Int,
        val story: Story,
        val title: String = "",
    )

    private val specs = listOf(
        Spec(
            SeedCatalogs.SUPERNATURAL, "Vampiro aristocrata", "Lysandra Vael", 7,
            Story(
                origin = "Transformada em 1777 durante um baile em Lisboa por um ancião da Corte Carmesim.",
                personality = "Elegante, irônica, cuidadosa com promessas.",
                goals = "Descobrir quem destruiu seu senhor e reconquistar o lugar da Casa Vael.",
                fears = "Perder o último fio de Humanidade.",
                secrets = "Ainda visita, em segredo, os descendentes da irmã mortal.",
            ),
            title = "Condessa da Noite",
        ),
        Spec(
            SeedCatalogs.MEDIEVAL_FANTASY, "Mago da Torre de Âmbar", "Aldric Venn", 5,
            Story(
                origin = "Filho de um moleiro, aceito na Torre por acidente após incendiar o celeiro com magia espontânea.",
                personality = "Curioso, distraído, leal aos amigos.",
                goals = "Tornar-se Arquimago da Escola de Evocação.",
            ),
        ),
        Spec(
            SeedCatalogs.DARK_FANTASY, "Cavaleiro maldito", "Corvin Ashgrave", 6,
            Story(
                origin = "Último sobrevivente da Ordem da Lâmina Cinzenta após o cerco de Morvhal.",
                personality = "Silencioso, implacável, gentil com crianças.",
                secrets = "Fez um juramento à Bruxa de Kesh para sobreviver.",
            ),
            title = "Sor",
        ),
        Spec(
            SeedCatalogs.PARANORMAL, "Investigador paranormal", "Helena Duarte", 1,
            Story(
                origin = "Jornalista que perdeu o irmão no caso do Sinal 77.3.",
                motivations = "Provar que o irmão não enlouqueceu.",
            ),
        ),
        Spec(
            SeedCatalogs.OCCULT, "Demônio encarnado", "Malphas", 9,
            Story(
                origin = "Príncipe menor invocado em 1923 e preso a um contrato com uma família de banqueiros.",
                personality = "Paciente, cortês, absolutamente egoísta.",
                goals = "Encontrar uma brecha no contrato.",
            ),
        ),
    )

    fun build(catalogs: List<Catalog>, now: Long): List<Character> = specs.mapIndexedNotNull { i, s ->
        val cat = catalogs.firstOrNull { it.id == s.catalogId } ?: return@mapIndexedNotNull null
        val tpl = cat.templates.firstOrNull { it.name == s.templateName } ?: cat.templates.firstOrNull() ?: return@mapIndexedNotNull null
        SheetMath.syncDerived(Blueprints.instantiate(tpl.blueprint, now - i * 60_000L, name = s.name, catalogId = cat.id).copy(
            id = "demo-" + Ids.new(),
            level = s.level,
            story = s.story,
            title = s.title.ifBlank { tpl.blueprint.title },
            isDemo = true,
            favorite = i < 2,
        ))
    }
}
