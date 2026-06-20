package com.tama.gallerynoai.ui.screens

import android.content.Intent
import android.content.IntentSender
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.data.model.SortType
import com.tama.gallerynoai.ui.components.CreateFolderDialog
import com.tama.gallerynoai.ui.components.FastDateScroller
import com.tama.gallerynoai.ui.components.FolderSelectionDialog
import com.tama.gallerynoai.ui.viewmodel.GalleryItem
import com.tama.gallerynoai.ui.components.DateHeader
import com.tama.gallerynoai.ui.components.DragSelectionState
import com.tama.gallerynoai.ui.components.MediaGridItem
import com.tama.gallerynoai.ui.components.SelectionTopAppBar
import com.tama.gallerynoai.ui.components.AddTagDialog
import com.tama.gallerynoai.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGridScreen(
    title: String,
    filteredMedia: List<MediaItem>,
    viewModel: GalleryViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onBackClick: () -> Unit,
    onSearchClick: (() -> Unit)? = null,
    onDeleteRequest: (IntentSender) -> Unit = {},
    gridColumns: Int = 3,
    gridPadding: Int = 1
) {
    val sortType by viewModel.sortType.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.selectionMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasLoadedOnce by viewModel.hasLoadedOnce.collectAsState()
    val dateFormat by viewModel.dateFormat.collectAsState()

    val context = LocalContext.current

    // Move heavy computation to Background Thread to avoid UI freeze
    var groupedItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }

    LaunchedEffect(filteredMedia, sortType, dateFormat) {
        withContext(Dispatchers.Default) {
            val result = if (sortType == SortType.DATE_NEWEST || sortType == SortType.DATE_OLDEST) {
                viewModel.groupMediaByDate(filteredMedia, dateFormat)
            } else {
                filteredMedia.map { GalleryItem.Media(it) }
            }
            groupedItems = result
        }
    }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = isSelectionMode) {
        viewModel.setSelectionMode(false)
    }

    var showMenu by remember { mutableStateOf(false) }

    var showFolderSelection by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var isMoveOperation by remember { mutableStateOf(false) }
    var showBatchTagDialog by remember { mutableStateOf(false) }
    val albums by viewModel.albums.collectAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSelectionMode) {
                SelectionTopAppBar(
                    selectedCount = selectedIds.size,
                    onClearSelection = { viewModel.setSelectionMode(false) },
                    onDelete = {
                        val selectedUris = filteredMedia.filter { selectedIds.contains(it.id) }.map { it.uri }
                        viewModel.clearSelection()
                        viewModel.moveToTrash(selectedUris)?.let { onDeleteRequest(it) }
                    },
                    onShare = {
                        val selectedUris = filteredMedia.filter { selectedIds.contains(it.id) }.map { it.uri }
                        if (selectedUris.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/* video/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selectedUris))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_media)))
                        }
                    },
                    onSelectAll = {
                        viewModel.selectAll(filteredMedia)
                    },
                    onFavorite = {
                        val selectedMedia = filteredMedia.filter { selectedIds.contains(it.id) }
                        val selectedUris = selectedMedia.map { it.uri }
                        val isAllFavorite = selectedMedia.all { it.isFavorite }
                        viewModel.favoriteMedia(selectedUris, !isAllFavorite)?.let { onDeleteRequest(it) }
                    },
                    onBatchTag = {
                        showBatchTagDialog = true
                    },
                    onMoveToFolder = {
                        isMoveOperation = true
                        showFolderSelection = true
                    },
                    onCopyToFolder = {
                        isMoveOperation = false
                        showFolderSelection = true
                    },
                    scrollBehavior = scrollBehavior,
                    isAllFavorite = remember(selectedIds, filteredMedia) {
                        val selectedMedia = filteredMedia.filter { selectedIds.contains(it.id) }
                        selectedMedia.isNotEmpty() && selectedMedia.all { it.isFavorite }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.setSelectionMode(true) }) {
                            Text(stringResource(R.string.select))
                        }
                        if (onSearchClick != null) {
                            IconButton(onClick = onSearchClick) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_date_newest)) },
                                    onClick = {
                                        viewModel.setSortType(SortType.DATE_NEWEST)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_date_oldest)) },
                                    onClick = {
                                        viewModel.setSortType(SortType.DATE_OLDEST)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_size_largest)) },
                                    onClick = {
                                        viewModel.setSortType(SortType.SIZE_LARGEST)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_size_smallest)) },
                                    onClick = {
                                        viewModel.setSortType(SortType.SIZE_SMALLEST)
                                        showMenu = false
                                    }
                                )
                            }
                        }
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
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && !hasLoadedOnce) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                var dragSelectionState by remember { mutableStateOf<DragSelectionState?>(null) }

                fun updateDragSelection(offset: Offset) {
                    val state = dragSelectionState ?: return
                    val item = gridState.layoutInfo.visibleItemsInfo.find { item ->
                        offset.y >= item.offset.y && offset.y <= (item.offset.y + item.size.height) &&
                                offset.x >= item.offset.x && offset.x <= (item.offset.x + item.size.width)
                    }
                    if (item != null && item.index < groupedItems.size) {
                        val currentItem = groupedItems[item.index]
                        if (currentItem is GalleryItem.Media) {
                            val currentIndex = item.index
                            val start = minOf(state.startIndex, currentIndex)
                            val end = maxOf(state.startIndex, currentIndex)

                            val rangeIds = (start..end)
                                .mapNotNull { (groupedItems.getOrNull(it) as? GalleryItem.Media)?.item?.id }

                            val newSelection = if (state.shouldSelect) {
                                state.initialSelectedIds + rangeIds
                            } else {
                                state.initialSelectedIds - rangeIds.toSet()
                            }
                            viewModel.setSelectedIds(newSelection)
                        }
                    }
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isSelectionMode) {
                            if (!isSelectionMode) return@pointerInput

                            detectDragGestures(
                                onDragStart = { offset ->
                                    val item = gridState.layoutInfo.visibleItemsInfo.find { item ->
                                        offset.y >= item.offset.y && offset.y <= (item.offset.y + item.size.height) &&
                                                offset.x >= item.offset.x && offset.x <= (item.offset.x + item.size.width)
                                    }
                                    if (item != null && item.index < groupedItems.size) {
                                        val currentItem = groupedItems[item.index]
                                        if (currentItem is GalleryItem.Media) {
                                            val isSelected = selectedIds.contains(currentItem.item.id)
                                            dragSelectionState = DragSelectionState(
                                                startIndex = item.index,
                                                initialSelectedIds = selectedIds,
                                                shouldSelect = !isSelected
                                            )
                                            viewModel.toggleSelection(currentItem.item.id)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    updateDragSelection(change.position)

                                    // Auto-scroll
                                    val viewHeight = gridState.layoutInfo.viewportSize.height
                                    val threshold = 100f
                                    if (change.position.y < threshold) {
                                        coroutineScope.launch {
                                            gridState.scrollBy(-30f)
                                        }
                                    } else if (change.position.y > viewHeight - threshold) {
                                        coroutineScope.launch {
                                            gridState.scrollBy(30f)
                                        }
                                    }
                                },
                                onDragEnd = { dragSelectionState = null },
                                onDragCancel = { dragSelectionState = null }
                            )
                        },
                    contentPadding = PaddingValues(gridPadding.dp),
                    horizontalArrangement = Arrangement.spacedBy(gridPadding.dp),
                    verticalArrangement = Arrangement.spacedBy(gridPadding.dp)
                ) {
                    items(
                        count = groupedItems.size,
                        key = { index ->
                            when (val item = groupedItems[index]) {
                                is GalleryItem.Header -> "header_${item.date}"
                                is GalleryItem.Media -> "media_${item.item.id}"
                            }
                        },
                        span = { index ->
                            val item = groupedItems[index]
                            when (item) {
                                is GalleryItem.Header -> GridItemSpan(maxLineSpan)
                                is GalleryItem.Media -> GridItemSpan(1)
                            }
                        },
                        contentType = { index ->
                            val item = groupedItems[index]
                            when (item) {
                                is GalleryItem.Header -> "header"
                                is GalleryItem.Media -> "media"
                            }
                        }
                    ) { index ->
                        when (val item = groupedItems[index]) {
                            is GalleryItem.Header -> DateHeader(date = item.date)
                            is GalleryItem.Media -> {
                                val isSelected = remember(selectedIds) { selectedIds.contains(item.item.id) }
                                MediaGridItem(
                                    item = item.item,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleSelection(item.item.id)
                                        } else {
                                            onMediaClick(item.item)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            viewModel.toggleSelection(item.item.id)
                                        }
                                    },
                                    isSelectionMode = isSelectionMode
                                )
                            }
                        }
                    }
                }

                // --- Fast Scroll Slider ---
                if (sortType == SortType.DATE_NEWEST || sortType == SortType.DATE_OLDEST) {
                    FastDateScroller(
                        gridState = gridState,
                        items = groupedItems,
                        dateFormat = dateFormat
                    )
                }
            }
        }
    }

    if (showFolderSelection) {
        FolderSelectionDialog(
            albums = albums,
            onFolderSelected = { albumId ->
                showFolderSelection = false
                val selectedMedia = filteredMedia.filter { selectedIds.contains(it.id) }
                val selectedUris = selectedMedia.map { it.uri }
                val targetAlbum = albums.find { it.id == albumId }
                val targetPath = targetAlbum?.relativePath ?: ""

                if (isMoveOperation) {
                    viewModel.moveMedia(selectedUris, targetPath)?.let { onDeleteRequest(it) }
                } else {
                    viewModel.copyMedia(selectedUris, targetPath)
                }
                viewModel.clearSelection()
            },
            onDismiss = { showFolderSelection = false },
            onCreateNewFolder = {
                showCreateFolder = true
            }
        )
    }

    if (showCreateFolder) {
        CreateFolderDialog(
            onDismiss = { showCreateFolder = false },
            onConfirm = { folderName ->
                showCreateFolder = false
                showFolderSelection = false
                val selectedUris = filteredMedia.filter { selectedIds.contains(it.id) }.map { it.uri }
                if (isMoveOperation) {
                    viewModel.moveMedia(selectedUris, "Pictures/$folderName")?.let { onDeleteRequest(it) }
                } else {
                    viewModel.copyMedia(selectedUris, "Pictures/$folderName")
                }
            }
        )
    }

    if (showBatchTagDialog) {
        AddTagDialog(
            allTagsFlow = viewModel.allUniqueTags,
            onDismiss = { showBatchTagDialog = false },
            onConfirm = { tag ->
                showBatchTagDialog = false
                viewModel.batchUpdateMediaMetadata(selectedIds.toList(), tag)
            }
        )
    }
}
