package com.tama.gallerynoai.ui.screens

import android.content.IntentSender
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.model.AlbumItem
import com.tama.gallerynoai.ui.components.SelectionTopAppBar
import com.tama.gallerynoai.ui.components.bouncyClick
import com.tama.gallerynoai.ui.components.bouncyCombinedClick
import com.tama.gallerynoai.ui.viewmodel.GalleryViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: GalleryViewModel,
    onAlbumClick: (AlbumItem) -> Unit,
    onFavoritesClick: () -> Unit,
    onVideosClick: () -> Unit,
    onScreenshotsClick: () -> Unit,
    onTrashClick: () -> Unit,
    onDeleteRequest: (IntentSender) -> Unit = {}
) {
    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasLoadedOnce by viewModel.hasLoadedOnce.collectAsState()
    val albumSelectionMode by viewModel.albumSelectionMode.collectAsState()
    val selectedAlbumIds by viewModel.selectedAlbumIds.collectAsState()
    val hiddenAlbumIds by viewModel.hiddenAlbumIds.collectAsState()
    val quickAccessItems by viewModel.quickAccessItems.collectAsState()
    
    val filteredAlbums = remember(albums, hiddenAlbumIds) {
        albums.filter { it.id !in hiddenAlbumIds }
    }
    
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val selectionScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    BackHandler(enabled = albumSelectionMode) {
        viewModel.setAlbumSelectionMode(false)
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToTopTrigger.collect { route ->
            if (route == "albums") {
                gridState.animateScrollToItem(0)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(if (albumSelectionMode) selectionScrollBehavior.nestedScrollConnection else scrollBehavior.nestedScrollConnection),
        topBar = {
            if (albumSelectionMode) {
                SelectionTopAppBar(
                    selectedCount = selectedAlbumIds.size,
                    onClearSelection = { viewModel.setAlbumSelectionMode(false) },
                    onDelete = {
                        viewModel.deleteAlbums(selectedAlbumIds)?.let { onDeleteRequest(it) }
                    },
                    onHide = {
                        selectedAlbumIds.forEach { viewModel.toggleAlbumHidden(it) }
                        viewModel.setAlbumSelectionMode(false)
                    },
                    scrollBehavior = selectionScrollBehavior
                )
            } else {
                TopAppBar(
                    title = { 
                        Text(
                            stringResource(R.string.nav_albums), 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        ) 
                    },
                    scrollBehavior = scrollBehavior,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredAlbums.isEmpty() && hasLoadedOnce) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_albums_found))
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!albumSelectionMode && quickAccessItems.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        QuickAccessSection(
                            onFavoritesClick = onFavoritesClick,
                            onVideosClick = onVideosClick,
                            onScreenshotsClick = onScreenshotsClick,
                            onTrashClick = onTrashClick,
                            visibleItems = quickAccessItems
                        )
                    }
                }

                items(filteredAlbums, key = { it.id }) { album ->
                    AlbumGridItem(
                        album = album,
                        isSelected = selectedAlbumIds.contains(album.id),
                        isSelectionMode = albumSelectionMode,
                        onClick = {
                            if (albumSelectionMode) {
                                viewModel.toggleAlbumSelection(album.id)
                            } else {
                                onAlbumClick(album)
                            }
                        },
                        onLongClick = {
                            viewModel.toggleAlbumSelection(album.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickAccessSection(
    onFavoritesClick: () -> Unit,
    onVideosClick: () -> Unit,
    onScreenshotsClick: () -> Unit,
    onTrashClick: () -> Unit,
    visibleItems: Set<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        val rows = mutableListOf<List<@Composable () -> Unit>>()
        val currentItems = mutableListOf<@Composable () -> Unit>()

        if (visibleItems.contains("favorites")) {
            currentItems.add {
                QuickAccessButton(
                    icon = Icons.Default.FavoriteBorder,
                    label = stringResource(R.string.favorites),
                    onClick = onFavoritesClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (visibleItems.contains("trash")) {
            currentItems.add {
                QuickAccessButton(
                    icon = Icons.Default.DeleteOutline,
                    label = stringResource(R.string.trash),
                    onClick = onTrashClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (visibleItems.contains("videos")) {
            currentItems.add {
                QuickAccessButton(
                    icon = Icons.Default.Videocam,
                    label = stringResource(R.string.videos),
                    onClick = onVideosClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (visibleItems.contains("screenshots")) {
            currentItems.add {
                QuickAccessButton(
                    icon = Icons.Default.Screenshot,
                    label = stringResource(R.string.screenshots),
                    onClick = onScreenshotsClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        currentItems.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item -> item() }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}


@Composable
fun QuickAccessButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .height(56.dp)
            .bouncyClick(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumGridItem(
    album: AlbumItem,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyCombinedClick(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEEEEEE)) // Light gray background
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = album.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) Color.Black.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .background(if (!isSelected) Color.Black.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        val formattedSize = android.text.format.Formatter.formatFileSize(LocalContext.current, album.totalSize)
        Text(
            text = stringResource(R.string.items_with_size, album.itemCount, formattedSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AlbumGridItemPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp).width(160.dp)) {
            AlbumGridItem(
                album = AlbumItem(
                    id = "1",
                    name = "Camera",
                    coverUri = android.net.Uri.EMPTY,
                    itemCount = 124,
                    totalSize = 1024 * 1024 * 500,
                    relativePath = "DCIM/Camera"
                ),
                onClick = {}
            )
        }
    }
}
