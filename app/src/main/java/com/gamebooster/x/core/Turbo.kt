package com.gamebooster.x.core

import java.util.Locale

/** Estado do Shizuku no aparelho. */
enum class ShizukuStatus(val label: String) {
    NOT_INSTALLED("Shizuku não instalado"),
    NOT_RUNNING("Shizuku instalado, mas parado"),
    OUTDATED("Versão do Shizuku muito antiga"),
    NO_PERMISSION("Falta permitir o acesso"),
    READY("Pronto"),
}

/**
 * Configuração do Modo Turbo para um jogo.
 *
 * @param resolutionScale fração da resolução nativa que o jogo vai renderizar (1.0 = sem redução).
 * @param fpsCap limite de FPS aplicado pelo sistema; 0 = sem limite.
 */
data class TurboSettings(
    val resolutionScale: Float = 0.7f,
    val fpsCap: Int = 60,
) {
    companion object {
        val SCALE_OPTIONS = listOf(1.0f, 0.85f, 0.7f, 0.5f)
        val FPS_OPTIONS = listOf(0, 30, 60, 90)
        val DEFAULT = TurboSettings()
    }
}

/** Resultado real de um comando executado pelo Shizuku. */
data class ShellResult(val command: String, val exitCode: Int, val output: String) {
    val ok: Boolean
        get() = exitCode == 0 && ERROR_MARKERS.none { output.contains(it, ignoreCase = true) }

    private companion object {
        val ERROR_MARKERS = listOf("Exception", "Error:", "Unknown command", "not a game", "Invalid")
    }
}

/**
 * Comandos do Game Mode oficial do Android (cmd game), executados com permissão de shell via Shizuku.
 * Não altera arquivos de jogos nem toca em antitrapaça: só pede ao sistema para renderizar o jogo
 * em resolução menor e/ou limitar o FPS.
 */
object TurboCommands {

    private val PACKAGE = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$""")

    fun isValidPackage(pkg: String): Boolean = PACKAGE.matches(pkg)

    fun formatScale(scale: Float): String =
        String.format(Locale.US, "%.2f", scale).trimEnd('0').trimEnd('.')

    /** Comandos aplicados antes de abrir o jogo. O último lista a configuração resultante (verificação). */
    fun apply(pkg: String, settings: TurboSettings): List<String> {
        require(isValidPackage(pkg)) { "Pacote inválido: $pkg" }
        val commands = mutableListOf(
            "am kill-all",
            "cmd game mode performance $pkg",
        )
        val interventions = buildString {
            if (settings.resolutionScale < 1f) append(" --downscale ${formatScale(settings.resolutionScale)}")
            if (settings.fpsCap > 0) append(" --fps ${settings.fpsCap}")
        }
        if (interventions.isNotEmpty()) {
            commands += "cmd game set --mode 2$interventions $pkg"
        }
        commands += "cmd game list-configs $pkg"
        return commands
    }

    /** Desfaz tudo o que [apply] alterou no jogo. */
    fun restore(pkg: String): List<String> {
        require(isValidPackage(pkg)) { "Pacote inválido: $pkg" }
        return listOf("cmd game reset $pkg", "cmd game mode standard $pkg")
    }
}
