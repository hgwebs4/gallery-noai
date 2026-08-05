package com.tama.gallerynoai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tama.gallerynoai.ui.navigation.NavRoutes
import com.tama.gallerynoai.ui.theme.GalleryTheme
import com.tama.gallerynoai.ui.viewmodel.GalleryViewModel
import com.tama.gallerynoai.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            galleryViewModel.loadMedia()
        }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            galleryViewModel.loadMedia()
            galleryViewModel.loadTrashedMedia()
        }
    }

    private val galleryViewModel: GalleryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val commonLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            galleryViewModel.onMoveConfirmed()
            galleryViewModel.loadMedia()
            galleryViewModel.clearSelection()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val amoledMode by settingsViewModel.amoledMode.collectAsStateWithLifecycle()
            val themeColor by settingsViewModel.themeColor.collectAsStateWithLifecycle()
            val showNavLabel by settingsViewModel.showNavLabel.collectAsStateWithLifecycle()
            
            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            GalleryTheme(
                darkTheme = darkTheme,
                themeColor = themeColor,
                amoledMode = amoledMode
            ) {
                LaunchedEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { !darkTheme },
                        navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { !darkTheme }
                    )
                }

                val navController = rememberNavController()
                
                // Handle incoming intent
                LaunchedEffect(intent) {
                    handleIntent(intent, navController)
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                val showBottomBar = currentDestination?.route in listOf(
                    NavRoutes.PHOTOS, 
                    NavRoutes.SEARCH, 
                    NavRoutes.ALBUMS, 
                    NavRoutes.SETTINGS,
                    NavRoutes.ALBUM_DETAIL,
                    NavRoutes.TRASH,
                    NavRoutes.QUICK_ACCESS
                )

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars,
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavigationBar(
                                navController = navController, 
                                currentDestination = currentDestination,
                                galleryViewModel = galleryViewModel,
                                showLabel = showNavLabel,
                                onTabReselected = { screen ->
                                    if (navController.currentDestination?.route != screen.route) {
                                        // Clear selection and search when popping back to root
                                        galleryViewModel.setSelectionMode(false)
                                        galleryViewModel.setAlbumSelectionMode(false)
                                        galleryViewModel.onSearchQueryChange("")
                                        
                                        // If we are in a sub-route (like search or album_detail), pop back to the tab's root
                                        navController.popBackStack(screen.route, inclusive = false)
                                    } else {
                                        // If we are already at the root, trigger scroll to top
                                        when (screen) {
                                            Screen.Photos -> galleryViewModel.triggerScrollToTop(screen.route)
                                            Screen.Search -> galleryViewModel.triggerScrollToTop(screen.route)
                                            Screen.Albums -> galleryViewModel.triggerScrollToTop(screen.route)
                                            Screen.Settings -> settingsViewModel.triggerScrollToTop()
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    com.tama.gallerynoai.ui.navigation.AppNavHost(
                        navController = navController,
                        galleryViewModel = galleryViewModel,
                        settingsViewModel = settingsViewModel,
                        commonLauncher = commonLauncher,
                        restoreLauncher = restoreLauncher,
                        modifier = Modifier.padding(innerPadding),
                        activity = this
                    )
                }
            }
        }

        checkAndRequestPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?, navController: androidx.navigation.NavHostController) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            val mimeType = intent.type ?: contentResolver.getType(uri) ?: "image/*"
            val encodedUri = URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8.toString())
            navController.navigate(NavRoutes.externalDetail(encodedUri, mimeType)) {
                popUpTo(NavRoutes.PHOTOS) { inclusive = false }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            galleryViewModel.loadMedia(silent = true)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = getRequiredPermissions()

        if (hasAllPermissions()) {
            galleryViewModel.loadMedia()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        return permissions.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean {
        return getRequiredPermissions().all { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

sealed class Screen(val route: String, val labelId: Int, val icon: ImageVector) {
    object Photos : Screen(NavRoutes.PHOTOS, R.string.nav_photos, Icons.Default.Photo)
    object Search : Screen(NavRoutes.SEARCH, R.string.nav_search, Icons.Default.Search)
    object Albums : Screen(NavRoutes.ALBUMS, R.string.nav_albums, Icons.Default.Collections)
    object Settings : Screen(NavRoutes.SETTINGS, R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun BottomNavigationBar(
    navController: androidx.navigation.NavHostController, 
    currentDestination: androidx.navigation.NavDestination?,
    galleryViewModel: GalleryViewModel,
    showLabel: Boolean = true,
    onTabReselected: (Screen) -> Unit
) {
    val items = listOf(Screen.Photos, Screen.Albums, Screen.Settings)
    val previousRoute = remember(currentDestination) {
        navController.previousBackStackEntry?.destination?.route
    }

    val isSearchOwnedByAlbums = currentDestination?.route == NavRoutes.SEARCH && 
        previousRoute in listOf(
            NavRoutes.ALBUMS, 
            NavRoutes.ALBUM_DETAIL, 
            NavRoutes.QUICK_ACCESS
        )

    NavigationBar(
        windowInsets = NavigationBarDefaults.windowInsets,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        items.forEach { screen ->
            val isSelected = when (screen) {
                Screen.Photos -> (currentDestination?.route in listOf(NavRoutes.PHOTOS, NavRoutes.DETAIL)) || 
                                (currentDestination?.route == NavRoutes.SEARCH && !isSearchOwnedByAlbums)
                Screen.Search -> currentDestination?.route == NavRoutes.SEARCH
                Screen.Albums -> (currentDestination?.route in listOf(
                    NavRoutes.ALBUMS,
                    NavRoutes.ALBUM_DETAIL,
                    NavRoutes.TRASH,
                    NavRoutes.ALBUM_ITEM_DETAIL,
                    NavRoutes.QUICK_ACCESS,
                    NavRoutes.QUICK_ACCESS_DETAIL
                )) || (currentDestination?.route == NavRoutes.SEARCH && isSearchOwnedByAlbums)
                Screen.Settings -> currentDestination?.route == NavRoutes.SETTINGS
            }
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = null) },
                label = if (showLabel) { { Text(stringResource(screen.labelId)) } } else null,
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        onTabReselected(screen)
                    } else {
                        // Clear selection when moving to a new tab
                        galleryViewModel.setSelectionMode(false)
                        galleryViewModel.setAlbumSelectionMode(false)
                        galleryViewModel.onSearchQueryChange("")

                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
