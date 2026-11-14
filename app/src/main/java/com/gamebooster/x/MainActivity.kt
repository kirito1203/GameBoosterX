package com.gamebooster.x

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamebooster.x.ui.dashboard.DashboardRoute
import com.gamebooster.x.ui.dashboard.DashboardViewModel
import com.gamebooster.x.ui.dashboard.LaunchRequestsEffect
import com.gamebooster.x.ui.gamespace.GameSpaceRoute
import com.gamebooster.x.ui.gamespace.GameSpaceViewModel
import com.gamebooster.x.ui.library.LibraryScreen
import com.gamebooster.x.ui.theme.GameBoosterTheme
import com.gamebooster.x.ui.theme.GbxColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameBoosterTheme {
                AppRoot()
            }
        }
    }
}

private enum class Tab(val label: String, val icon: String) {
    LIBRARY("Jogos", "🎮"),
    DASHBOARD("Painel", "⚡"),
    GAME_SPACE("Game Space", "🛡"),
}

@Composable
private fun AppRoot() {
    val dashboardVm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
    val gameSpaceVm: GameSpaceViewModel = viewModel(factory = GameSpaceViewModel.Factory)
    val dash by dashboardVm.state.collectAsStateWithLifecycle()
    val gs by gameSpaceVm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(Tab.LIBRARY.ordinal) }

    LaunchRequestsEffect(dashboardVm)

    Scaffold(
        containerColor = GbxColors.Black,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = GbxColors.Graphite) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Text(t.icon) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = GbxColors.Neon,
                            unselectedTextColor = GbxColors.TextSecondary,
                            indicatorColor = GbxColors.Panel,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (Tab.entries[tab]) {
                Tab.LIBRARY -> LibraryScreen(
                    games = dash.games,
                    gamesLoaded = dash.gamesLoaded,
                    stats = gs.stats,
                    shizukuStatus = dash.turbo.status,
                    turboRunning = dash.turbo.running,
                    turboMessage = dash.turbo.message,
                    onPlay = dashboardVm::launchGame,
                    onTurbo = { pkg -> dashboardVm.applyTurboAndPlay(pkg) },
                    onRefresh = dashboardVm::refreshGames,
                )
                Tab.DASHBOARD -> DashboardRoute(dashboardVm)
                Tab.GAME_SPACE -> GameSpaceRoute(games = dash.games, viewModel = gameSpaceVm)
            }
        }
    }
}
