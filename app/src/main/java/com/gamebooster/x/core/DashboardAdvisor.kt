package com.gamebooster.x.core

enum class AdviceSeverity { INFO, WARNING, CRITICAL }

data class Advice(val severity: AdviceSeverity, val title: String, val detail: String)

/**
 * Recomendações baseadas em regras a partir das leituras atuais.
 * (A análise com histórico e padrões fica para o AI Performance Engine — Fase 6.)
 */
object DashboardAdvisor {

    fun advise(
        thermal: ThermalAssessment,
        memory: MemoryAssessment,
        policy: ModePolicy,
        profile: DeviceProfile,
        display: DisplayReading?,
    ): List<Advice> {
        val out = mutableListOf<Advice>()

        when (thermal.level) {
            ThermalLevel.CRITICAL -> out += Advice(
                AdviceSeverity.CRITICAL,
                "Aparelho muito quente",
                "Pause o jogo alguns minutos, retire capinha e evite jogar carregando. " +
                    "O sistema reduz o desempenho para se proteger.",
            )
            ThermalLevel.HOT -> out += Advice(
                AdviceSeverity.WARNING,
                "Temperatura elevada",
                if (policy.mode == PerformanceMode.COOL) "Reduza gráficos ou FPS máximo no jogo."
                else "Considere o modo Cool para sessões longas.",
            )
            else -> Unit
        }
        if (thermal.throttlingLikely && thermal.level != ThermalLevel.CRITICAL) {
            out += Advice(
                AdviceSeverity.WARNING,
                "Limitação térmica detectada",
                "Quedas de FPS agora provavelmente vêm de temperatura, não de memória.",
            )
        }

        if (memory.aboveModeAlert) {
            out += Advice(
                if (memory.pressure == MemoryPressure.CRITICAL) AdviceSeverity.CRITICAL else AdviceSeverity.WARNING,
                "RAM em ${memory.usedPercent}%",
                "Toque em \"Otimizar agora\" antes de abrir o jogo para encerrar apps em segundo plano.",
            )
        }

        if (profile.tier == DeviceTier.ENTRY) {
            out += Advice(
                AdviceSeverity.INFO,
                "Gráficos recomendados",
                "Para este aparelho, gráficos baixos/médios e limite de 60 FPS tendem a dar FPS mais estável " +
                    "que gráficos altos com FPS variando.",
            )
        }

        display?.let { d ->
            val maxHz = d.supportedRefreshHz.maxOrNull() ?: d.currentRefreshHz
            if (maxHz > d.currentRefreshHz + 1f && policy.mode == PerformanceMode.EXTREME) {
                out += Advice(
                    AdviceSeverity.INFO,
                    "Taxa de atualização",
                    "A tela suporta até ${Formatters.hz(maxHz)} mas está em ${Formatters.hz(d.currentRefreshHz)}. " +
                        "Ative a taxa máxima em Configurações > Tela, se o jogo suportar.",
                )
            }
        }

        if (out.isEmpty()) {
            out += Advice(AdviceSeverity.INFO, "Tudo em ordem", "Temperatura e memória dentro dos limites do modo ${policy.mode.label}.")
        }
        return out
    }
}
