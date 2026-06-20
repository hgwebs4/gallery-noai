package com.tama.gallerynoai.ui.screens

import android.content.IntentSender
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.data.model.SortType
import com.tama.gallerynoai.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class QuickAccessType {
    FAVORITES, VIDEOS, SCREENSHOTS
}

@Composable
fun QuickAccessDetailScreen(
    type: QuickAccessType,
    viewModel: GalleryViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onBackClick: () -> Unit,
    onDeleteRequest: (IntentSender) -> Unit = {}
) {
    val mediaItems by viewModel.mediaItems.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val gridPadding = 1
    
    var filteredMedia by remember { mutableStateOf(emptyList<MediaItem>()) }

    LaunchedEffect(mediaItems, type, sortType) {
        withContext(Dispatchers.Default) {
            val filtered = when (type) {
                QuickAccessType.FAVORITES -> mediaItems.filter { it.isFavorite }
                QuickAccessType.VIDEOS -> mediaItems.filter { it.isVideo }
                QuickAccessType.SCREENSHOTS -> mediaItems.filter { 
                    it.relativePath?.contains("Screenshots", ignoreCase = true) == true || 
                    it.name.contains("Screenshot", ignoreCase = true)
                }
            }
            val sorted = when (sortType) {
                SortType.DATE_NEWEST -> filtered.sortedByDescending { it.dateModified }
                SortType.DATE_OLDEST -> filtered.sortedBy { it.dateModified }
                SortType.SIZE_LARGEST -> filtered.sortedByDescending { it.size }
                SortType.SIZE_SMALLEST -> filtered.sortedBy { it.size }
            }
            filteredMedia = sorted
        }
    }

    val title = when (type) {
        QuickAccessType.FAVORITES -> stringResource(R.string.favorites)
        QuickAccessType.VIDEOS -> stringResource(R.string.videos)
        QuickAccessType.SCREENSHOTS -> stringResource(R.string.screenshots)
    }
    
    MediaGridScreen(
        title = title,
        filteredMedia = filteredMedia,
        viewModel = viewModel,
        onMediaClick = onMediaClick,
        onBackClick = onBackClick,
        onDeleteRequest = onDeleteRequest,
        gridColumns = gridColumns,
        gridPadding = gridPadding
    )
}
