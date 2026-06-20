package com.tama.gallerynoai.ui.screens

import android.content.Intent
import android.content.IntentSender
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.tama.gallerynoai.ui.components.DragSelectionState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.compose.itemContentType
import androidx.paging.LoadState
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.ui.components.AddTagDialog
import com.tama.gallerynoai.ui.components.CreateFolderDialog
import com.tama.gallerynoai.ui.components.FastDateScroller
import com.tama.gallerynoai.ui.components.FolderSelectionDialog
import com.tama.gallerynoai.ui.components.MediaGridItem
import com.tama.gallerynoai.ui.components.SelectionTopAppBar
import com.tama.gallerynoai.ui.viewmodel.GalleryItem
import com.tama.gallerynoai.ui.viewmodel.GalleryViewModel
import androidx.compose.ui.res.stringResource
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.model.SortType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: GalleryViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onDeleteRequest: (IntentSender) -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val pagedResults = viewModel.pagedSearchResults.collectAsLazyPagingItems()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val allTags by viewModel.allUniqueTags.collectAsState()
    val dateFormat by viewModel.dateFormat.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val gridPadding = 1

    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    var isSearchBarActive by remember { mutableStateOf(value = false) }
    val gridState = rememberLazyGridState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current

    var showFolderSelection by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var isMoveOperation by remember { mutableStateOf(false) }
    var showBatchTagDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode || isSearchBarActive || searchQuery.isNotEmpty()) {
        if (isSelectionMode) {
            viewModel.setSelectionMode(false)
        } else if (isSearchBarActive) {
            isSearchBarActive = false
        } else if (searchQuery.isNotEmpty()) {
            viewModel.onSearchQueryChange("")
        }
    }

    val searchAnimationSpec = tween<Float>(durationMillis = 400, easing = FastOutSlowInEasing)
    val intSizeAnimationSpec = tween<IntSize>(durationMillis = 400, easing = FastOutSlowInEasing)
    val searchDpAnimationSpec = tween<Dp>(durationMillis = 400, easing = FastOutSlowInEasing)
    val searchColorAnimationSpec = tween<Color>(durationMillis = 400, easing = FastOutSlowInEasing)

    val searchBarPaddingHorizontal by animateDpAsState(
        targetValue = if (isSearchBarActive) 0.dp else 16.dp,
        animationSpec = searchDpAnimationSpec,
        label = "SearchBarHorizontalPadding"
    )
    val searchBarPaddingBottom by animateDpAsState(
        targetValue = if (isSearchBarActive) 0.dp else 8.dp,
        animationSpec = searchDpAnimationSpec,
        label = "SearchBarBottomPadding"
    )
    val searchBarContainerColor by animateColorAsState(
        targetValue = if (isSearchBarActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = searchColorAnimationSpec,
        label = "SearchBarContainerColor"
    )

    LaunchedEffect(Unit) {
        viewModel.scrollToTopTrigger.collect { route ->
            if (route == "search") {
                if (searchQuery.isNotEmpty()) {
                    if (pagedResults.itemCount > 0 && gridState.firstVisibleItemIndex > 0) {
                        gridState.animateScrollToItem(0)
                    } else {
                        viewModel.onSearchQueryChange("")
                    }
                } else {
                    gridState.animateScrollToItem(0)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSelectionMode) {
                SelectionTopAppBar(
                    selectedCount = selectedIds.size,
                    onClearSelection = { viewModel.setSelectionMode(false) },
                    onDelete = {
                        val selectedUris = mutableListOf<android.net.Uri>()
                        for (i in 0 until pagedResults.itemCount) {
                            pagedResults.peek(i)?.let { item ->
                                if (selectedIds.contains(item.id)) {
                                    selectedUris.add(item.uri)
                                }
                            }
                        }
                        viewModel.clearSelection()
                        viewModel.moveToTrash(selectedUris)?.let { onDeleteRequest(it) }
                    },
                    onShare = {
                        val selectedUris = ArrayList<android.net.Uri>()
                        for (i in 0 until pagedResults.itemCount) {
                            pagedResults.peek(i)?.let { item ->
                                if (selectedIds.contains(item.id)) {
                                    selectedUris.add(item.uri)
                                }
                            }
                        }
                        if (selectedUris.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/* video/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, selectedUris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_media)))
                        }
                    },
                    onSelectAll = {
                        val allItems = mutableListOf<MediaItem>()
                        for (i in 0 until pagedResults.itemCount) {
                            pagedResults.peek(i)?.let { allItems.add(it) }
                        }
                        viewModel.selectAll(allItems)
                    },
                    onFavorite = {
                        val selectedMedia = mutableListOf<MediaItem>()
                        for (i in 0 until pagedResults.itemCount) {
                            pagedResults.peek(i)?.let { item ->
                                if (selectedIds.contains(item.id)) {
                                    selectedMedia.add(item)
                                }
                            }
                        }
                        val selectedUris = selectedMedia.map { it.uri }
                        val isAllFavorite = selectedMedia.all { it.isFavorite }
                        viewModel.favoriteMedia(selectedUris, !isAllFavorite)?.let { onDeleteRequest(it) }
                    },
                    onBatchTag = {
                        showBatchTagDialog = true
                    },
                    onCopyToFolder = {
                        isMoveOperation = false
                        showFolderSelection = true
                    },
                    onMoveToFolder = {
                        isMoveOperation = true
                        showFolderSelection = true
                    },
                    scrollBehavior = scrollBehavior,
                    isAllFavorite = remember(selectedIds, pagedResults.itemCount) {
                        var allFav = true
                        var hasSelected = false
                        for (i in 0 until pagedResults.itemCount) {
                            pagedResults.peek(i)?.let { item ->
                                if (selectedIds.contains(item.id)) {
                                    hasSelected = true
                                    if (!item.isFavorite) allFav = false
                                }
                            }
                        }
                        hasSelected && allFav
                    }
                )
            } else {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    AnimatedVisibility(
                        visible = !isSearchBarActive,
                        enter = fadeIn(searchAnimationSpec) + expandVertically(intSizeAnimationSpec),
                        exit = fadeOut(searchAnimationSpec) + shrinkVertically(intSizeAnimationSpec)
                    ) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.nav_search),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            },
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            )
                        )
                    }

                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { viewModel.onSearchQueryChange(it) },
                                onSearch = {
                                    viewModel.saveSearch(it)
                                    isSearchBarActive = false
                                    keyboardController?.hide()
                                },
                                expanded = isSearchBarActive,
                                onExpandedChange = { isSearchBarActive = it },
                                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                                        }
                                    }
                                }
                            )
                        },
                        expanded = isSearchBarActive,
                        onExpandedChange = { isSearchBarActive = it },
                        colors = SearchBarDefaults.colors(
                            containerColor = searchBarContainerColor,
                        ),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = searchBarPaddingHorizontal)
                            .padding(bottom = searchBarPaddingBottom)
                    ) {
                        if (searchQuery.isEmpty()) {
                            RecentSearchesList(
                                recentSearches = recentSearches,
                                onSearchClick = {
                                    viewModel.onSearchQueryChange(it)
                                    isSearchBarActive = false
                                },
                                onDeleteClick = { viewModel.deleteRecentSearch(it) },
                                onClearAll = { viewModel.clearSearchHistory() }
                            )
                        } else {
                            SuggestionSection(
                                suggestions = searchSuggestions,
                                onSuggestionClick = {
                                    viewModel.onSearchQueryChange(it)
                                    viewModel.saveSearch(it)
                                    isSearchBarActive = false
                                    keyboardController?.hide()
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = searchQuery.isNotEmpty(),
                    label = "SearchContentAnimation",
                    transitionSpec = {
                        fadeIn(searchAnimationSpec) togetherWith fadeOut(searchAnimationSpec)
                    }
                ) { isShowingResults ->
                    if (isShowingResults) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            SearchResultsGrid(
                                pagedResults = pagedResults,
                                query = searchQuery,
                                gridState = gridState,
                                onMediaClick = onMediaClick,
                                selectedIds = selectedIds,
                                isSelectionMode = isSelectionMode,
                                gridColumns = gridColumns,
                                gridPadding = gridPadding,
                                onToggleSelection = { viewModel.toggleSelection(it) },
                                onSetSelectedIds = { viewModel.setSelectedIds(it) }
                            )

                            if (searchQuery.isNotEmpty() && (sortType == SortType.DATE_NEWEST || sortType == SortType.DATE_OLDEST)) {
                                val galleryItems = remember(pagedResults.itemCount) {
                                    val list = mutableListOf<GalleryItem>()
                                    for (i in 0 until pagedResults.itemCount) {
                                        pagedResults.peek(i)?.let { list.add(GalleryItem.Media(it)) }
                                    }
                                    list
                                }
                                FastDateScroller(
                                    gridState = gridState,
                                    items = galleryItems,
                                    dateFormat = dateFormat
                                )
                            }
                        }
                    } else {
                        SearchHome(
                            recentSearches = recentSearches,
                            tags = allTags,
                            onSearchClick = { viewModel.onSearchQueryChange(it) },
                            onDeleteRecent = { viewModel.deleteRecentSearch(it) },
                            onClearRecent = { viewModel.clearSearchHistory() }
                        )
                    }
                }

                if (isSearchBarActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.01f))
                            .clickable(enabled = true, onClick = { isSearchBarActive = false })
                    )
                }
            }
        }
    }

    if (showFolderSelection) {
        FolderSelectionDialog(
            albums = albums,
            onDismiss = { showFolderSelection = false },
            onFolderSelected = { albumId ->
                showFolderSelection = false
                val selectedUris = mutableListOf<android.net.Uri>()
                for (i in 0 until pagedResults.itemCount) {
                    pagedResults.peek(i)?.let { item ->
                        if (selectedIds.contains(item.id)) {
                            selectedUris.add(item.uri)
                        }
                    }
                }
                val targetAlbum = albums.find { it.id == albumId }
                val targetPath = targetAlbum?.relativePath ?: "Pictures/${targetAlbum?.name ?: ""}"

                if (isMoveOperation) {
                    viewModel.moveMedia(selectedUris, targetPath)?.let { onDeleteRequest(it) }
                } else {
                    viewModel.copyMedia(selectedUris, targetPath)
                }
            },
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
                val selectedUris = mutableListOf<android.net.Uri>()
                for (i in 0 until pagedResults.itemCount) {
                    pagedResults.peek(i)?.let { item ->
                        if (selectedIds.contains(item.id)) {
                            selectedUris.add(item.uri)
                        }
                    }
                }
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

@Composable
fun SearchHome(
    recentSearches: List<String>,
    tags: List<String>,
    onSearchClick: (String) -> Unit,
    onDeleteRecent: (String) -> Unit,
    onClearRecent: () -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Recent Searches (Horizontal Chips)
        if (recentSearches.isNotEmpty()) {
            item {
                SearchSection(
                    title = stringResource(R.string.recent),
                    trailingText = stringResource(R.string.clear_all),
                    onTrailingClick = onClearRecent
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentSearches.take(10), key = { it }) { query ->
                            InputChip(
                                modifier = Modifier.animateItem(),
                                selected = false,
                                onClick = { onSearchClick(query) },
                                label = { Text(query) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp).clickable { onDeleteRecent(query) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Custom Tags
        if (tags.isNotEmpty()) {
            item {
                SearchSection(title = stringResource(R.string.your_tags)) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.take(15).forEach { tag ->
                            SuggestionChip(
                                onClick = { onSearchClick(tag) },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SearchResultsGrid(
    pagedResults: androidx.paging.compose.LazyPagingItems<MediaItem>,
    query: String,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onMediaClick: (MediaItem) -> Unit,
    selectedIds: Set<Long> = emptySet(),
    isSelectionMode: Boolean = false,
    gridColumns: Int = 3,
    gridPadding: Int = 1,
    onToggleSelection: (Long) -> Unit = {},
    onSetSelectedIds: (Set<Long>) -> Unit = {}
) {
    val isSearching = pagedResults.loadState.refresh is LoadState.Loading

    if (isSearching && pagedResults.itemCount == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (!isSearching && pagedResults.itemCount == 0) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.no_results_found, query),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        var dragSelectionState by remember { mutableStateOf<DragSelectionState?>(null) }
        val haptic = LocalHapticFeedback.current
        val coroutineScope = rememberCoroutineScope()

        fun updateDragSelection(currentOffset: Offset) {
            val startState = dragSelectionState ?: return

            val itemUnderPointer = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                val itemTop = item.offset.y.toFloat()
                val itemBottom = itemTop + item.size.height
                val itemLeft = item.offset.x.toFloat()
                val itemRight = itemLeft + item.size.width

                currentOffset.y in itemTop..itemBottom &&
                        currentOffset.x in itemLeft..itemRight
            }

            if (itemUnderPointer != null) {
                val currentIndex = itemUnderPointer.index
                val startIndex = startState.startIndex

                val minIndex = minOf(startIndex, currentIndex)
                val maxIndex = maxOf(startIndex, currentIndex)

                val newSelectedIds = startState.initialSelectedIds.toMutableSet()

                for (i in minIndex..maxIndex) {
                    pagedResults.peek(i)?.let { itemInRange ->
                        if (startState.shouldSelect) {
                            newSelectedIds.add(itemInRange.id)
                        } else {
                            newSelectedIds.remove(itemInRange.id)
                        }
                    }
                }

                if (newSelectedIds != selectedIds) {
                    onSetSelectedIds(newSelectedIds)
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
                        onDragStart = { offset: Offset ->
                            val itemUnderPointer = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                offset.y in item.offset.y.toFloat()..(item.offset.y + item.size.height).toFloat() &&
                                        offset.x in item.offset.x.toFloat()..(item.offset.x + item.size.width).toFloat()
                            }

                            if (itemUnderPointer != null) {
                                pagedResults.peek(itemUnderPointer.index)?.let { mediaItem ->
                                    val isCurrentlySelected = selectedIds.contains(mediaItem.id)
                                    dragSelectionState = DragSelectionState(
                                        startIndex = itemUnderPointer.index,
                                        initialSelectedIds = selectedIds,
                                        shouldSelect = !isCurrentlySelected
                                    )
                                    onToggleSelection(mediaItem.id)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateDragSelection(change.position)

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
                count = pagedResults.itemCount,
                key = pagedResults.itemKey { it.id },
                contentType = pagedResults.itemContentType { "media" }
            ) { index ->
                val item = pagedResults[index]
                if (item != null) {
                    val isSelected = remember(selectedIds) { selectedIds.contains(item.id) }
                    MediaGridItem(
                        item = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelection(item.id)
                            } else {
                                onMediaClick(item)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                onToggleSelection(item.id)
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchSection(
    title: String,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            if (trailingText != null) {
                TextButton(onClick = { onTrailingClick?.invoke() }) {
                    Text(trailingText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        content()
    }
}

@Composable
fun RecentSearchesList(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onClearAll) {
                Text(stringResource(R.string.clear_all))
            }
        }

        recentSearches.take(8).forEach { query ->
            ListItem(
                headlineContent = { Text(query) },
                leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onDeleteClick(query) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(18.dp))
                    }
                },
                modifier = Modifier.clickable { onSearchClick(query) }
            )
        }
    }
}

@Composable
fun SuggestionSection(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        suggestions.forEach { suggestion ->
            ListItem(
                headlineContent = { Text(suggestion) },
                leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.NorthWest, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.clickable { onSuggestionClick(suggestion) }
            )
        }
    }
}
