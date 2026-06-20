package com.tama.gallerynoai.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.ui.components.DragSelectionState
import com.tama.gallerynoai.ui.components.MediaGridItem
import com.tama.gallerynoai.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: GalleryViewModel,
    onRestoreClick: (List<Uri>) -> Unit,
    onDeletePermanentlyClick: (List<Uri>) -> Unit,
    onEmptyTrashClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val trashedItems by viewModel.trashedMedia.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.selectionMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasLoadedOnce by viewModel.hasLoadedOnce.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val trashWarningEnabled by viewModel.trashWarningEnabled.collectAsState()
    
    var selectedItemForDialog by remember { mutableStateOf<MediaItem?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEmptyTrashConfirmation by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = isSelectionMode) {
        viewModel.setSelectionMode(false)
    }

    LaunchedEffect(Unit) {
        viewModel.loadTrashedMedia()
    }
    
    // Reset selection mode when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setSelectionMode(false)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { 
                        Text(
                            stringResource(R.string.selected_count, selectedIds.size),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.setSelectionMode(false) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val selectedUris = trashedItems.filter { selectedIds.contains(it.id) }.map { it.uri }
                            onRestoreClick(selectedUris)
                            viewModel.setSelectionMode(false)
                        }) {
                            Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.restore_selected))
                        }
                        IconButton(onClick = { showBulkDeleteConfirmation = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.delete_permanently), tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = { 
                        Text(
                            stringResource(R.string.trash), 
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
                        if (trashedItems.isNotEmpty()) {
                            IconButton(onClick = { showEmptyTrashConfirmation = true }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = stringResource(R.string.empty_trash),
                                    tint = MaterialTheme.colorScheme.error
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (trashWarningEnabled && trashedItems.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.trash_warning_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (trashedItems.isEmpty() && hasLoadedOnce) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.trash_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.trash_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (trashedItems.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    var dragSelectionState by remember { mutableStateOf<DragSelectionState?>(null) }
                    val currentTrashedItems by rememberUpdatedState(trashedItems)

                    // Handle Drag Selection Logic
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
                                currentTrashedItems.getOrNull(i)?.let { item ->
                                    if (startState.shouldSelect) {
                                        newSelectedIds.add(item.id)
                                    } else {
                                        newSelectedIds.remove(item.id)
                                    }
                                }
                            }
                            
                            if (newSelectedIds != selectedIds) {
                                viewModel.setSelectedIds(newSelectedIds)
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
                                        val itemUnderPointer = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                            offset.y in item.offset.y.toFloat()..(item.offset.y + item.size.height).toFloat() &&
                                            offset.x in item.offset.x.toFloat()..(item.offset.x + item.size.width).toFloat()
                                        }
                                        
                                        if (itemUnderPointer != null) {
                                            currentTrashedItems.getOrNull(itemUnderPointer.index)?.let { item ->
                                                val isCurrentlySelected = selectedIds.contains(item.id)
                                                dragSelectionState = DragSelectionState(
                                                    startIndex = itemUnderPointer.index,
                                                    initialSelectedIds = selectedIds,
                                                    shouldSelect = !isCurrentlySelected
                                                )
                                                viewModel.toggleSelection(item.id)
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
                                            coroutineScope.launch { gridState.scrollBy(-30f) }
                                        } else if (change.position.y > viewHeight - threshold) {
                                            coroutineScope.launch { gridState.scrollBy(30f) }
                                        }
                                    },
                                    onDragEnd = { dragSelectionState = null },
                                    onDragCancel = { dragSelectionState = null }
                                )
                            },
                        contentPadding = PaddingValues(1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(trashedItems) { index, item ->
                            val isSelected = selectedIds.contains(item.id)
                            MediaGridItem(
                                item = item, 
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = { 
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        selectedItemForDialog = item 
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.toggleSelection(item.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog for single item options
    selectedItemForDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItemForDialog = null },
            title = { Text(stringResource(R.string.options)) },
            text = { Text(stringResource(R.string.trash_options_desc, item.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreClick(listOf(item.uri))
                        selectedItemForDialog = null
                    }
                ) {
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = true
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_permanently))
                }
            }
        )
    }

    // Confirmation dialog for single permanent delete
    if (showDeleteConfirmation && selectedItemForDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_permanently)) },
            text = { Text(stringResource(R.string.permanently_delete_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        selectedItemForDialog?.let { onDeletePermanentlyClick(listOf(it.uri)) }
                        showDeleteConfirmation = false
                        selectedItemForDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Confirmation dialog for bulk permanent delete
    if (showBulkDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_permanently)) },
            text = { Text(stringResource(R.string.permanently_delete_confirm_bulk, selectedIds.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedUris = trashedItems.filter { selectedIds.contains(it.id) }.map { it.uri }
                        onDeletePermanentlyClick(selectedUris)
                        showBulkDeleteConfirmation = false
                        viewModel.setSelectionMode(false)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Confirmation dialog for EMPTY TRASH
    if (showEmptyTrashConfirmation) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmation = false },
            title = { Text(stringResource(R.string.empty_trash)) },
            text = { Text(stringResource(R.string.empty_trash_confirm, trashedItems.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrashClick()
                        showEmptyTrashConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.empty_trash))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

