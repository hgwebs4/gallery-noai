package com.tama.gallerynoai.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

data class DragSelectionState(
    val startIndex: Int,
    val initialSelectedIds: Set<Long>,
    val shouldSelect: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onBatchTag: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onCopyToFolder: (() -> Unit)? = null,
    onMoveToFolder: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    isAllFavorite: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val hasMenuOptions = onSelectAll != null || onFavorite != null || onBatchTag != null || onHide != null || onCopyToFolder != null || onMoveToFolder != null

    TopAppBar(
        title = {
            Text(
                stringResource(R.string.selected_count, selectedCount),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
            }
        },
        actions = {
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.move_to_trash))
            }
            if (onHide != null) {
                IconButton(onClick = onHide) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = stringResource(R.string.hide))
                }
            }
            if (hasMenuOptions) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    onSelectAll?.let {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.select_all)) },
                            onClick = {
                                showMenu = false
                                it()
                            }
                        )
                    }
                    onFavorite?.let {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (isAllFavorite) R.string.unfavorite else R.string.favorite)) },
                            onClick = {
                                showMenu = false
                                it()
                            }
                        )
                    }
                    onBatchTag?.let {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_tag)) },
                            onClick = {
                                showMenu = false
                                it()
                            }
                        )
                    }
                    onHide?.let {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hide)) },
                            onClick = {
                                showMenu = false
                                it()
                            }
                        )
                    }
                    onCopyToFolder?.let {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_to_folder)) },
                            onClick = {
                                showMenu = false
                                it()
                            }
                        )
                    }
                    onMoveToFolder?.let {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to_folder)) },
                            onClick = {
                                showMenu = false
                                it()
                            }
                        )
                    }
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

@Composable
fun DateHeader(date: String) {
    Text(
        text = date,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 16.dp, 16.dp, 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    item: MediaItem,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelectionMode: Boolean = false
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .then(
                if (isSelectionMode) {
                    Modifier.bouncyClick(onClick = onClick)
                } else {
                    Modifier.bouncyCombinedClick(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                }
            )
    ) {
        val context = LocalContext.current
        val imageRequest = remember(item.uri) {
            ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(100)
                .size(300)
                .precision(coil.size.Precision.INEXACT)
                .memoryCacheKey("${item.uri}_thumb")
                .diskCacheKey("${item.uri}_thumb")
                .build()
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize().then(
                if (isSelected) Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp)) else Modifier
            ),
            contentScale = ContentScale.Crop
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.select),
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }

        if (item.isVideo && !isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.videos),
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }

        if (item.isFavorite && !isSelected) {
            Box(
                modifier = Modifier
                    .align(if (item.isVideo) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.favorites),
                    modifier = Modifier.size(16.dp),
                    tint = Color.Red
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTagDialog(
    allTagsFlow: StateFlow<List<String>>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    val allTags by allTagsFlow.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current

    val suggestions = remember(tagName, allTags) {
        if (tagName.isBlank()) emptyList()
        else allTags.asSequence().filter { it.contains(tagName, ignoreCase = true) && !it.equals(tagName, ignoreCase = true) }.take(5).toList()
    }

    AlertDialog(
        onDismissRequest = {
            keyboardController?.hide()
            onDismiss()
        },
        title = { Text(stringResource(R.string.add_custom_tag)) },
        text = {
            Column {
                TextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text(stringResource(R.string.tag_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.suggestions), style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { tagName = suggestion },
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    keyboardController?.hide()
                    onConfirm(tagName)
                },
                enabled = tagName.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                keyboardController?.hide()
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun MediaGridItemPreview() {
    MaterialTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            MediaGridItem(
                item = MediaItem(
                    id = 1,
                    uri = android.net.Uri.EMPTY,
                    name = "test.jpg",
                    dateModified = 0,
                    size = 0,
                    mimeType = "image/jpeg",
                    bucketId = "0",
                    isVideo = false,
                    isFavorite = false
                ),
                onClick = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SelectionTopAppBarPreview() {
    MaterialTheme {
        SelectionTopAppBar(
            selectedCount = 5,
            onClearSelection = {},
            onDelete = {}
        )
    }
}
