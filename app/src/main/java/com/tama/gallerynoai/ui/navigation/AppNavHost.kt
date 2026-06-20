package com.tama.gallerynoai.ui.navigation

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.ui.screens.*
import com.tama.gallerynoai.ui.viewmodel.GalleryViewModel
import com.tama.gallerynoai.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds

@Composable
fun AppNavHost(
    navController: NavHostController,
    galleryViewModel: GalleryViewModel,
    settingsViewModel: SettingsViewModel,
    commonLauncher: ActivityResultLauncher<IntentSenderRequest>,
    restoreLauncher: ActivityResultLauncher<IntentSenderRequest>,
    modifier: Modifier = Modifier,
    activity: Activity
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.PHOTOS,
        modifier = modifier
    ) {
        composable(NavRoutes.PHOTOS) {
            GalleryScreen(
                viewModel = galleryViewModel,
                onMediaClick = { item ->
                    navController.navigate(NavRoutes.detail(item.id))
                },
                onSearchClick = {
                    navController.navigate(NavRoutes.SEARCH)
                },
                onDeleteRequest = { intentSender ->
                    commonLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            )
        }
        composable(NavRoutes.SEARCH) {
            SearchScreen(
                viewModel = galleryViewModel,
                onMediaClick = { item ->
                    navController.navigate(NavRoutes.detail(item.id))
                },
                onDeleteRequest = { intentSender ->
                    commonLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            )
        }
        composable(NavRoutes.ALBUMS) {
            AlbumsScreen(
                viewModel = galleryViewModel,
                onAlbumClick = { album ->
                    val encodedName = URLEncoder.encode(album.name, StandardCharsets.UTF_8.toString())
                    navController.navigate(NavRoutes.albumDetail(album.id, encodedName))
                },
                onFavoritesClick = {
                    navController.navigate(NavRoutes.quickAccess("favorites"))
                },
                onVideosClick = {
                    navController.navigate(NavRoutes.quickAccess("videos"))
                },
                onScreenshotsClick = {
                    navController.navigate(NavRoutes.quickAccess("screenshots"))
                },
                onTrashClick = {
                    navController.navigate(NavRoutes.TRASH)
                },
                onDeleteRequest = { intentSender ->
                    commonLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            )
        }
        composable(NavRoutes.QUICK_ACCESS) { backStackEntry ->
            val typeStr = backStackEntry.arguments?.getString("type") ?: ""
            val type = when (typeStr.lowercase()) {
                "favorites" -> QuickAccessType.FAVORITES
                "videos" -> QuickAccessType.VIDEOS
                "screenshots" -> QuickAccessType.SCREENSHOTS
                else -> QuickAccessType.FAVORITES
            }
            QuickAccessDetailScreen(
                type = type,
                viewModel = galleryViewModel,
                onMediaClick = { item ->
                    navController.navigate(NavRoutes.quickAccessDetail(typeStr, item.id))
                },
                onBackClick = { navController.popBackStack() },
                onDeleteRequest = { intentSender ->
                    commonLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            )
        }
        composable(NavRoutes.QUICK_ACCESS_DETAIL) { backStackEntry ->
            val typeStr = backStackEntry.arguments?.getString("type") ?: ""
            val mediaId = backStackEntry.arguments?.getString("mediaId")?.toLongOrNull() ?: -1L

            val filteredItems by when (typeStr.lowercase()) {
                "favorites" -> galleryViewModel.favoriteItems.collectAsStateWithLifecycle()
                "videos" -> galleryViewModel.videoItems.collectAsStateWithLifecycle()
                "screenshots" -> galleryViewModel.screenshotItems.collectAsStateWithLifecycle()
                else -> remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
            }

            MediaDetailScreen(
                viewModel = galleryViewModel,
                items = filteredItems,
                initialId = mediaId,
                onRefresh = { galleryViewModel.loadMedia() },
                onSearchTrigger = { tag ->
                    galleryViewModel.onSearchQueryChange(tag)
                    navController.navigate(NavRoutes.SEARCH) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.TRASH) {
            TrashScreen(
                viewModel = galleryViewModel,
                onRestoreClick = { uris ->
                    val intentSender = galleryViewModel.restoreMedia(uris)
                    intentSender?.let {
                        restoreLauncher.launch(IntentSenderRequest.Builder(it).build())
                    }
                },
                onDeletePermanentlyClick = { uris ->
                    val intentSender = galleryViewModel.deletePermanently(uris)
                    intentSender?.let {
                        restoreLauncher.launch(IntentSenderRequest.Builder(it).build())
                    }
                },
                onEmptyTrashClick = {
                    val trashedUris = galleryViewModel.trashedMedia.value.map { it.uri }
                    if (trashedUris.isNotEmpty()) {
                        val intentSender = galleryViewModel.deletePermanently(trashedUris)
                        intentSender?.let {
                            restoreLauncher.launch(IntentSenderRequest.Builder(it).build())
                        }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.ALBUM_DETAIL) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            val encodedName = backStackEntry.arguments?.getString("albumName") ?: ""
            val albumName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.toString())
            AlbumDetailScreen(
                albumId = albumId,
                albumName = albumName,
                viewModel = galleryViewModel,
                onMediaClick = { item ->
                    navController.navigate(NavRoutes.albumItemDetail(albumId, item.id))
                },
                onBackClick = { navController.popBackStack() },
                onSearchClick = {
                    navController.navigate(NavRoutes.SEARCH)
                },
                onDeleteRequest = { intentSender ->
                    commonLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            )
        }
        composable(NavRoutes.ALBUM_ITEM_DETAIL) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            val mediaId = backStackEntry.arguments?.getString("mediaId")?.toLongOrNull() ?: -1L
            val items by galleryViewModel.mediaItems.collectAsStateWithLifecycle()
            val albumItems = items.filter { it.bucketId == albumId }

            MediaDetailScreen(
                viewModel = galleryViewModel,
                items = albumItems,
                initialId = mediaId,
                onRefresh = { galleryViewModel.loadMedia() },
                onSearchTrigger = { tag ->
                    galleryViewModel.onSearchQueryChange(tag)
                    navController.navigate(NavRoutes.SEARCH) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(viewModel = settingsViewModel)
        }
        composable(NavRoutes.DETAIL) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString("mediaId")?.toLongOrNull() ?: -1L
            val items by galleryViewModel.mediaItems.collectAsStateWithLifecycle()

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                LaunchedEffect(Unit) {
                    galleryViewModel.loadMedia()
                    delay(2.seconds)
                    if (items.isEmpty()) {
                        navController.popBackStack()
                    }
                }
            } else {
                MediaDetailScreen(
                    viewModel = galleryViewModel,
                    items = items,
                    initialId = mediaId,
                    onRefresh = { galleryViewModel.loadMedia() },
                    onSearchTrigger = { tag ->
                        galleryViewModel.onSearchQueryChange(tag)
                        navController.navigate(NavRoutes.SEARCH) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        composable(
            NavRoutes.EXTERNAL_DETAIL,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("mimeType") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val uriStr = backStackEntry.arguments?.getString("uri") ?: ""
            val mimeType = backStackEntry.arguments?.getString("mimeType") ?: "image/*"
            val uri = URLDecoder.decode(uriStr, StandardCharsets.UTF_8.toString()).let { android.net.Uri.parse(it) }

            val externalItem = MediaItem(
                id = -1L,
                uri = uri,
                name = uri.lastPathSegment ?: "Media",
                dateModified = System.currentTimeMillis() / 1000,
                size = 0,
                mimeType = mimeType,
                bucketId = "",
                isVideo = mimeType.startsWith("video")
            )

            MediaDetailScreen(
                viewModel = galleryViewModel,
                items = listOf(externalItem),
                initialId = -1L,
                onRefresh = { },
                onSearchTrigger = { tag ->
                    galleryViewModel.onSearchQueryChange(tag)
                    navController.navigate(NavRoutes.SEARCH) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onBackClick = {
                    if (navController.previousBackStackEntry == null) {
                        activity.finish()
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
