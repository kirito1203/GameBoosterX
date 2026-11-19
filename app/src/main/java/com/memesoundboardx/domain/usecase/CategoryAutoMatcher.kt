package com.memesoundboardx.domain.usecase

import com.memesoundboardx.core.util.TextNormalizer

/**
 * Organização automática: escolhe a categoria pelo nome do arquivo importado.
 * Regras em ordem de prioridade; a primeira palavra-chave encontrada vence.
 * Sem correspondência → "sons_aleatorios". O usuário pode mover depois.
 */
object CategoryAutoMatcher {

    const val FALLBACK = "sons_aleatorios"

    private val rules: List<Pair<String, List<String>>> = listOf(
        "memes_brasileiros" to listOf(
            "faustao", "silvio", "ratinho", "eita", "receba", "que isso", "tome", "irra", "ui ui",
            "ai que", "cavalo", "meme br", "br meme", "galvao", "e o pix", "rapaz", "vish", "oxe",
            "nao pode", "taca", "jesus", "senhor", "ta pegando fogo", "olha a faca", "pao de batata",
        ),
        "memes_classicos" to listOf(
            "meme", "bruh", "vine", "boom", "oof", "airhorn", "mlg", "nope", "yeet", "sus", "among us",
            "amogus", "rickroll", "sad violin", "wow", "emotional damage", "windows", "xp", "error",
            "nani", "omg", "fbi", "wasted", "dun dun", "taco bell", "metal pipe",
        ),
        "risadas_reacoes" to listOf(
            "risada", "rindo", "riso", "laugh", "haha", "kkk", "kkkk", "rsrs", "lol", "aplauso", "applause",
            "palmas", "grito", "scream", "aww", "uau", "reacao", "reaction", "vaia", "boo",
        ),
        "suspense_terror" to listOf(
            "terror", "horror", "susto", "jumpscare", "scary", "creepy", "suspense", "medo", "fantasma",
            "ghost", "zumbi", "zombie", "monstro", "assombr", "sinistro",
        ),
        "anime_poderes" to listOf(
            "anime", "kamehameha", "rasengan", "chidori", "jutsu", "poder", "power", "magia", "magic",
            "espada", "sword", "katana", "teleport", "super saiyajin", "goku", "naruto", "skill",
        ),
        "games_roblox" to listOf(
            "roblox", "minecraft", "game", "jogo", "level", "coin", "moeda", "8bit", "8 bit", "pixel",
            "win", "victory", "vitoria", "lose", "derrota", "respawn", "checkpoint", "fortnite", "free fire",
        ),
        "vinhetas_musicas" to listOf(
            "musica", "music", "song", "cancao", "jingle", "vinheta", "intro", "outro", "tema", "theme",
            "beat", "batida", "funk", "trap", "remix", "loop", "abertura",
        ),
        "impactos_comicos" to listOf(
            "soco", "punch", "tapa", "slap", "bonk", "hit", "impacto", "impact", "explos", "bomba",
            "whoosh", "boing", "queda", "fall", "crash", "pancada", "porrada", "chute", "kick",
        ),
    )

    fun match(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        // "kkkkk_risada-alta" -> " kkkkk risada alta "
        val text = " " + TextNormalizer.normalize(base.replace(Regex("[_\\-.()\\[\\]]+"), " ")) + " "
        val words = text.trim().split(' ').filter { it.isNotEmpty() }
        for ((category, keywords) in rules) {
            if (keywords.any { matches(text, words, it) }) return category
        }
        return FALLBACK
    }

    /**
     * Palavra-chave com espaço ("among us") → procura a expressão inteira.
     * Palavra curta (até 3 letras: "sus", "lol", "win") → só palavra exata, para "sus" não pegar "susto".
     * Palavra com 4+ letras → também vale como início de palavra ("explos" pega "explosao").
     */
    private fun matches(text: String, words: List<String>, keyword: String): Boolean {
        val kw = keyword.trim()
        if (kw.contains(' ')) return text.contains(" $kw ")
        return words.any { w -> w == kw || (kw.length >= 4 && w.startsWith(kw)) }
    }
}
