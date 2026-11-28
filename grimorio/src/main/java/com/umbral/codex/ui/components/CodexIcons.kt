package com.umbral.codex.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Coronavirus
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Fort
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

/** Ícones temáticos por nome (o nome é o que fica salvo nas fichas e catálogos). */
object CodexIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "auto_fix" to Icons.Filled.AutoFixHigh,
        "auto_awesome" to Icons.Filled.AutoAwesome,
        "auto_stories" to Icons.Filled.AutoStories,
        "menu_book" to Icons.AutoMirrored.Filled.MenuBook,
        "history_edu" to Icons.Filled.HistoryEdu,
        "bolt" to Icons.Filled.Bolt,
        "local_fire_department" to Icons.Filled.LocalFireDepartment,
        "whatshot" to Icons.Filled.Whatshot,
        "water_drop" to Icons.Filled.WaterDrop,
        "air" to Icons.Filled.Air,
        "thunderstorm" to Icons.Filled.Thunderstorm,
        "cyclone" to Icons.Filled.Cyclone,
        "flare" to Icons.Filled.Flare,
        "blur_on" to Icons.Filled.BlurOn,
        "nights_stay" to Icons.Filled.NightsStay,
        "dark_mode" to Icons.Filled.DarkMode,
        "visibility" to Icons.Filled.Visibility,
        "psychology" to Icons.Filled.Psychology,
        "bloodtype" to Icons.Filled.Bloodtype,
        "healing" to Icons.Filled.Healing,
        "favorite" to Icons.Filled.Favorite,
        "shield" to Icons.Filled.Shield,
        "hardware" to Icons.Filled.Hardware,
        "sports_martial_arts" to Icons.Filled.SportsMartialArts,
        "dangerous" to Icons.Filled.Dangerous,
        "mood_bad" to Icons.Filled.MoodBad,
        "coronavirus" to Icons.Filled.Coronavirus,
        "masks" to Icons.Filled.Masks,
        "hexagon" to Icons.Filled.Hexagon,
        "diamond" to Icons.Filled.Diamond,
        "star" to Icons.Filled.Star,
        "key" to Icons.Filled.Key,
        "science" to Icons.Filled.Science,
        "spa" to Icons.Filled.Spa,
        "forest" to Icons.Filled.Forest,
        "pets" to Icons.Filled.Pets,
        "castle" to Icons.Filled.Castle,
        "fort" to Icons.Filled.Fort,
        "church" to Icons.Filled.Church,
        "public" to Icons.Filled.Public,
        "explore" to Icons.Filled.Explore,
        "hourglass" to Icons.Filled.HourglassEmpty,
        "fingerprint" to Icons.Filled.Fingerprint,
        "inventory" to Icons.Filled.Inventory2,
        "backpack" to Icons.Filled.Backpack,
        "toll" to Icons.Filled.Toll,
    )

    fun of(name: String?): ImageVector = all[name] ?: Icons.Filled.AutoAwesome
}
