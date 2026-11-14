package com.gamebooster.x.core

/** Identidade do aparelho, lida em tempo real (nunca presumida). */
data class DeviceIdentity(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    /** Nome comercial (ex.: ro.product.marketname), quando o sistema expõe. */
    val marketName: String?,
    val socManufacturer: String?,
    val socModel: String?,
    val androidRelease: String,
    val sdkInt: Int,
    val abis: List<String>,
    val cpuCores: Int,
    val totalRamBytes: Long,
)

enum class DeviceTier(val label: String) {
    ENTRY("Entrada"),
    MID("Intermediário"),
    HIGH("Avançado"),
}

data class DeviceProfile(
    val key: String,
    val displayName: String,
    val isRealmeC71: Boolean,
    val tier: DeviceTier,
    val notes: List<String>,
)

/**
 * Identifica o aparelho e escolhe o perfil de recomendações.
 *
 * O Realme C71 recebe perfil próprio, mas as decisões usam apenas os valores
 * medidos (RAM, núcleos, Android) — nenhuma especificação é presumida.
 */
object DeviceProfileResolver {

    const val REALME_C71_KEY = "realme_c71"
    private const val GIB = 1024L * 1024L * 1024L

    fun isRealmeC71(id: DeviceIdentity): Boolean {
        val isRealme = listOf(id.manufacturer, id.brand).any { it.equals("realme", ignoreCase = true) }
        if (!isRealme) return false
        val c71 = Regex("""(^|[^a-z0-9])c71([^a-z0-9]|$)""", RegexOption.IGNORE_CASE)
        return listOfNotNull(id.model, id.marketName, id.device, id.product).any { c71.containsMatchIn(it) }
    }

    fun tierFor(totalRamBytes: Long, cpuCores: Int): DeviceTier = when {
        totalRamBytes <= 4L * GIB || cpuCores <= 4 -> DeviceTier.ENTRY
        totalRamBytes <= 8L * GIB -> DeviceTier.MID
        else -> DeviceTier.HIGH
    }

    fun resolve(id: DeviceIdentity): DeviceProfile {
        val tier = tierFor(id.totalRamBytes, id.cpuCores)
        val name = id.marketName?.takeIf { it.isNotBlank() }
            ?: "${id.manufacturer.replaceFirstChar { it.uppercase() }} ${id.model}"
        val notes = mutableListOf<String>()
        notes += "Android ${id.androidRelease} (API ${id.sdkInt}), ${id.cpuCores} núcleos, " +
            "${Formatters.bytes(id.totalRamBytes)} de RAM."
        id.socModel?.let { notes += "Chip informado pelo sistema: ${listOfNotNull(id.socManufacturer, it).joinToString(" ")}." }

        return if (isRealmeC71(id)) {
            notes += "Perfil Realme C71 ativo: recomendações priorizam estabilidade de FPS e controle térmico."
            if (tier == DeviceTier.ENTRY) {
                notes += "RAM/núcleos medidos indicam faixa de entrada: prefira gráficos baixos e 60 FPS estáveis."
            }
            DeviceProfile(REALME_C71_KEY, name, isRealmeC71 = true, tier = tier, notes = notes)
        } else {
            DeviceProfile(
                key = "generic_${id.manufacturer}_${id.model}".lowercase().replace(Regex("[^a-z0-9_]"), "_"),
                displayName = name,
                isRealmeC71 = false,
                tier = tier,
                notes = notes,
            )
        }
    }
}
