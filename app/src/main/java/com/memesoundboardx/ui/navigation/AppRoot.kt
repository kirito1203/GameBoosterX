package com.memesoundboardx.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memesoundboardx.ui.components.LocalPlayerUi
import com.memesoundboardx.ui.components.PlayerBar
import com.memesoundboardx.ui.components.PlayerUi
import com.memesoundboardx.ui.components.PlayerViewModel
import com.memesoundboardx.ui.components.SoundActionsViewModel
import com.memesoundboardx.ui.components.SoundDetailsDialog
import com.memesoundboardx.ui.screens.settings.CreditsScreen
import com.memesoundboardx.ui.screens.categories.CategoriesScreen
import com.memesoundboardx.ui.screens.categories.CategoryDetailScreen
import com.memesoundboardx.ui.screens.favorites.FavoritesScreen
import com.memesoundboardx.ui.screens.home.HomeScreen
import com.memesoundboardx.ui.screens.library.LibraryScreen
import com.memesoundboardx.ui.screens.myaudio.MyAudioScreen
import com.memesoundboardx.ui.screens.search.SearchScreen
import com.memesoundboardx.ui.screens.settings.SettingsScreen
import com.memesoundboardx.ui.theme.NeonColors

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val CATEGORIES = "categories"
    const val FAVORITES = "favorites"
    const val MY_AUDIO = "my_audio"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val CREDITS = "credits"
    const val CATEGORY_DETAIL = "category/{categoryId}"
    const val ARG_CATEGORY_ID = "categoryId"

    fun categoryDetail(id: String) = "category/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Início", Icons.Filled.Home),
    Tab(Routes.LIBRARY, "Biblioteca", Icons.AutoMirrored.Filled.List),
    Tab(Routes.CATEGORIES, "Categorias", Icons.Filled.Menu),
    Tab(Routes.FAVORITES, "Favoritos", Icons.Filled.Favorite),
    Tab(Routes.SEARCH, "Pesquisa", Icons.Filled.Search),
)

private fun titleFor(route: String?): String = when (route) {
    Routes.LIBRARY -> "Biblioteca"
    Routes.CATEGORIES -> "Categorias"
    Routes.FAVORITES -> "Favoritos"
    Routes.SEARCH -> "Pesquisa"
    Routes.MY_AUDIO -> "Meus áudios"
    Routes.SETTINGS -> "Configurações"
    Routes.CATEGORY_DETAIL -> "Categoria"
    Routes.CREDITS -> "Créditos e licenças"
    else -> "MEME SOUNDBOARD X"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTopLevel = tabs.any { it.route == currentRoute }

    val playerVm: PlayerViewModel = hiltViewModel()
    val playerState by playerVm.state.collectAsStateWithLifecycle()
    val actionsVm: SoundActionsViewModel = hiltViewModel()
    val catNames by actionsVm.categoryNames.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<com.memesoundboardx.domain.model.Sound?>(null) }
    val playerUi = PlayerUi(playerState, playerVm::toggle, playerVm::stop, onDetails = { details = it })
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(playerState.lastError) {
        playerState.lastError?.let {
            snackbar.showSnackbar(it)
            playerVm.clearError()
        }
    }

    CompositionLocalProvider(LocalPlayerUi provides playerUi) {
    Scaffold(
        containerColor = NeonColors.BgDeep,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(titleFor(currentRoute), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    if (!isTopLevel && currentRoute != null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (currentRoute != Routes.SETTINGS) {
                        IconButton(onClick = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NeonColors.BgDeep,
                    titleContentColor = NeonColors.Text,
                    actionIconContentColor = NeonColors.TextDim,
                    navigationIconContentColor = NeonColors.Text,
                ),
            )
        },
        bottomBar = {
          Column {
            PlayerBar(
                state = playerState,
                onVolumeCommit = playerVm::setVolume,
                onToggleRepeat = { playerVm.setRepeat(!playerState.repeat) },
                onStopAll = playerVm::stopAll,
            )
            NavigationBar(containerColor = NeonColors.Surface) {
                tabs.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.navigateToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonColors.Blue,
                            selectedTextColor = NeonColors.Blue,
                            indicatorColor = NeonColors.Blue.copy(alpha = 0.15f),
                            unselectedIconColor = NeonColors.TextDim,
                            unselectedTextColor = NeonColors.TextDim,
                        ),
                    )
                }
            }
          }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenMyAudio = { nav.navigate(Routes.MY_AUDIO) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenTab = { nav.navigateToTab(it) },
                )
            }
            composable(Routes.LIBRARY) { LibraryScreen() }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onOpenCategory = { nav.navigate(Routes.categoryDetail(it)) })
            }
            composable(
                Routes.CATEGORY_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_CATEGORY_ID) { type = NavType.StringType }),
            ) { CategoryDetailScreen() }
            composable(Routes.FAVORITES) { FavoritesScreen() }
            composable(Routes.SEARCH) { SearchScreen() }
            composable(Routes.MY_AUDIO) { MyAudioScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onOpenCredits = { nav.navigate(Routes.CREDITS) }) }
            composable(Routes.CREDITS) { CreditsScreen() }
        }
    }
    details?.let { s ->
        SoundDetailsDialog(
            sound = s,
            categoryName = s.categoryId?.let { catNames[it] },
            onToggleFavorite = { actionsVm.toggleFavorite(s); details = null },
            onRemove = { actionsVm.remove(s) },
            onDismiss = { details = null },
        )
    }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
