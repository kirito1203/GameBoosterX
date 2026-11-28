package com.umbral.codex.ui

import com.umbral.codex.core.Blueprints
import com.umbral.codex.core.SheetMath
import com.umbral.codex.core.SystemPreset
import com.umbral.codex.data.DemoContent
import com.umbral.codex.model.Character
import com.umbral.codex.model.CharacterTemplate

/** Ações de alto nível sobre fichas, compartilhadas por várias telas. */
fun AppViewModel.createCharacter(name: String, catalogId: String?, preset: SystemPreset): Character {
    val c = preset.build(name.trim(), catalogId)
    repository.upsert(c, touch = false)
    replace(Route.Sheet(c.id))
    toast("Ficha criada: ${c.name.ifBlank { "Sem nome" }}")
    return c
}

fun AppViewModel.createFromTemplate(name: String, catalogId: String?, template: CharacterTemplate): Character {
    val c = SheetMath.syncDerived(Blueprints.instantiate(template.blueprint, System.currentTimeMillis(), name.trim(), catalogId))
    repository.upsert(c, touch = false)
    replace(Route.Sheet(c.id))
    toast("Ficha criada a partir de “${template.name}”")
    return c
}

fun AppViewModel.duplicateCharacter(id: String) {
    repository.duplicate(id)?.let { toast("Cópia criada: ${it.name}") }
}

fun AppViewModel.deleteCharacter(id: String) {
    val name = repository.character(id)?.name?.ifBlank { "Sem nome" } ?: return
    repository.delete(id)
    toast("“$name” foi excluída")
}

fun AppViewModel.toggleFavorite(id: String) {
    repository.update(id, touch = false) { it.copy(favorite = !it.favorite) }
}

fun AppViewModel.loadDemo() {
    val demo = DemoContent.build(repository.catalogs.value, System.currentTimeMillis())
    demo.forEach { repository.upsert(it, touch = false) }
    toast("${demo.size} personagens de exemplo adicionados (fictícios)")
}

fun AppViewModel.removeDemo() {
    val demo = repository.characters.value.filter { it.isDemo }
    demo.forEach { repository.delete(it.id) }
    toast(if (demo.isEmpty()) "Não há personagens de exemplo" else "${demo.size} personagens de exemplo removidos")
}

/** Salva a ficha como modelo dentro do catálogo dela (ou de outro escolhido). */
fun AppViewModel.saveAsTemplate(characterId: String, catalogId: String, templateName: String, description: String) {
    val c = repository.character(characterId) ?: return
    val blueprint = Blueprints.instantiate(c, 0, name = "").copy(story = com.umbral.codex.model.Story(), portraitId = null, rollHistory = emptyList(), transactions = emptyList(), xp = 0)
    repository.updateCatalog(catalogId) { cat ->
        cat.copy(templates = cat.templates + CharacterTemplate(com.umbral.codex.core.Ids.new(), templateName.trim(), description.trim(), blueprint))
    }
    toast("Modelo “${templateName.trim()}” salvo em ${repository.catalog(catalogId)?.name ?: "catálogo"}")
}
