package com.gamebooster.x.core

data class KnownGame(val packageName: String, val name: String)

/**
 * Jogos com suporte inicial. Apenas pacotes oficiais verificados.
 * FNF Mobile não tem pacote oficial único (há vários ports); será cadastrado
 * manualmente no Game Manager (Fase 4).
 */
object KnownGames {
    val all: List<KnownGame> = listOf(
        KnownGame("com.mojang.minecraftpe", "Minecraft Bedrock"),
        KnownGame("com.roblox.client", "Roblox"),
        KnownGame("com.dts.freefireth", "Free Fire"),
        KnownGame("com.dts.freefiremax", "Free Fire MAX"),
    )

    private val byPackage = all.associateBy { it.packageName }

    fun find(packageName: String): KnownGame? = byPackage[packageName]
}

/** Jogo detectado no aparelho. */
data class InstalledGame(
    val packageName: String,
    val name: String,
    val isKnown: Boolean,
)
