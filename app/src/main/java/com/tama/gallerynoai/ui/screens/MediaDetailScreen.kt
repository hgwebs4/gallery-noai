package com.tama.gallerynoai.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.MediaStore
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.data.settings.FullscreenRotationMode
import com.tama.gallerynoai.ui.components.AddTagDialog
import com.tama.gallerynoai.ui.components.RenameDialog
import com.tama.gallerynoai.ui.components.bouncyClick
import com.tama.gallerynoai.ui.components.ZoomableBox
import com.tama.gallerynoai.ui.viewmodel.GalleryViewModel
import com.tama.gallerynoai.utils.DetailedMetadata
import com.tama.gallerynoai.utils.MediaFileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.OptIn
import kotlin.time.Duration.Companion.milliseconds

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@AndroidOptIn(UnstableApi::class)
@Composable
fun MediaDetailScreen(
    viewModel: GalleryViewModel,
    items: List<MediaItem>,
    initialId: Long,
    onRefresh: () -> Unit = {},
    onSearchTrigger: (String) -> Unit = {},
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val attributionContext = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.createAttributionContext("mediaAccess")
        } else {
            context
        }
    }

    val useDefaultEditor by viewModel.useDefaultEditor.collectAsState()
    val autoPlayVideo by viewModel.autoPlayVideo.collectAsState()
    val defaultMuteVideo by viewModel.defaultMuteVideo.collectAsState()
    val defaultEditorPackage by viewModel.defaultEditorPackage.collectAsState()
    val useDefaultVideoEditor by viewModel.useDefaultVideoEditor.collectAsState()
    val defaultVideoEditorPackage by viewModel.defaultVideoEditorPackage.collectAsState()
    val fullscreenRotationMode by viewModel.fullscreenRotationMode.collectAsState()
    val enableVideoPreload by viewModel.enableVideoPreload.collectAsState()

    val initialIndex = remember(items, initialId) {
        val index = items.indexOfFirst { it.id == initialId }
        if (index != -1) index else 0
    }

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        LaunchedEffect(Unit) {
            delay(500.milliseconds)
            if (items.isEmpty()) {
                onBackClick()
            }
        }
        return
    }

    val safeInitialPage = remember(initialIndex, items.size) {
        initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    val pagerState = rememberPagerState(initialPage = safeInitialPage) {
        items.size
    }
    var isUiVisible by remember { mutableStateOf(value = true) }

    val uiToggleAlpha by animateFloatAsState(
        targetValue = if (isUiVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "uiToggleAlpha",
    )

    var isManualFullScreen by remember { mutableStateOf(value = false) }
    var isZoomed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val currentItem by remember(items, pagerState.currentPage) {
        derivedStateOf {
            if (pagerState.currentPage < items.size) items[pagerState.currentPage] else null
        }
    }

    val dragOffsetY = remember { Animatable(0f) }
    val dismissThreshold = 300f

    val dragAlpha by remember {
        derivedStateOf {
            (1f - (abs(dragOffsetY.value) / 200f)).coerceIn(0f, 1f)
        }
    }

    val finalUiAlpha = uiToggleAlpha * dragAlpha

    val scope = rememberCoroutineScope()

    val currentItemVal = currentItem

    var showInfoSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var currentVideoSize by remember { mutableStateOf<IntSize?>(null) }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(isUiVisible) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (!isUiVisible) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    fun updateOrientation(activity: Activity, mode: FullscreenRotationMode, item: MediaItem?, videoSize: IntSize?) {
        when (mode) {
            FullscreenRotationMode.SYSTEM_SETTING -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            FullscreenRotationMode.DEVICE_ROTATION -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
            FullscreenRotationMode.ASPECT_RATIO -> {
                if (item != null) {
                    val width = if (item.isVideo) videoSize?.width ?: item.width else item.width
                    val height = if (item.isVideo) videoSize?.height ?: item.height else item.height

                    if (width != null && height != null && (width > height)) {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                } else {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    LaunchedEffect(fullscreenRotationMode, currentItem, currentVideoSize) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        updateOrientation(activity, fullscreenRotationMode, currentItem, currentVideoSize)
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            val window = activity?.window ?: return@onDispose
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onRefresh()
            onBackClick()
        }
    }

    val favoriteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onRefresh()
        }
    }

    val renameLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onRenameConfirmed()
            onRefresh()
        }
    }

    fun shareMedia(item: MediaItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Media"))
    }

    fun editMedia(item: MediaItem) {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (item.isVideo) {
                if (useDefaultVideoEditor && !defaultVideoEditorPackage.isNullOrEmpty()) {
                    setPackage(defaultVideoEditorPackage)
                }
            } else {
                if (useDefaultEditor && !defaultEditorPackage.isNullOrEmpty()) {
                    setPackage(defaultEditorPackage)
                }
            }
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Edit Media"))
        } catch (_: Exception) {}
    }

    fun deleteMedia(item: MediaItem) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, listOf(item.uri), true)
                deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else {
                context.contentResolver.delete(item.uri, null, null)
                onBackClick()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModel.favoriteMedia(listOf(item.uri), !item.isFavorite)?.let { intentSender ->
            favoriteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    fun useAs(item: MediaItem) {
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("mimeType", item.mimeType)
        }
        context.startActivity(Intent.createChooser(intent, "Use as"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (1f - (dragOffsetY.value / 600f)).coerceIn(0f, 1f)))
            .pointerInput(isZoomed) {
                if (!isZoomed) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                dragOffsetY.snapTo(dragOffsetY.value + dragAmount)
                            }
                        },
                        onDragEnd = {
                            if (dragOffsetY.value > dismissThreshold) {
                                onBackClick()
                            } else if (dragOffsetY.value < -150f) {
                                showInfoSheet = true
                                scope.launch {
                                    dragOffsetY.animateTo(0f)
                                }
                            } else {
                                scope.launch {
                                    dragOffsetY.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dragOffsetY.animateTo(0f)
                            }
                        }
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffsetY.value
                    val scale = (1f - (dragOffsetY.value / 3000f)).coerceIn(0.5f, 1f)
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 0,
                key = { if (it < items.size) items[it].id else it }
            ) { page ->
                if (page < items.size) {
                    val isPageVisible = pagerState.currentPage == page
                    MediaPage(
                        item = items[page],
                        isVisible = isPageVisible,
                        isUiVisible = isUiVisible,
                        isManualFullScreen = isManualFullScreen,
                        autoPlay = autoPlayVideo,
                        startMuted = defaultMuteVideo,
                        bottomPadding = if (isUiVisible && !isManualFullScreen) 80.dp else 0.dp,
                        onFullScreenToggle = {
                            isManualFullScreen = !isManualFullScreen
                            if (isManualFullScreen) {
                                isUiVisible = false
                            }
                        },
                        onZoomChange = { isZoomed = it },
                        onTap = { isUiVisible = !isUiVisible },
                        onVideoSizeDetermined = { size ->
                            if (pagerState.currentPage == page) {
                                currentVideoSize = size
                            }
                        },
                        resetTrigger = pagerState.currentPage,
                        uiAlpha = finalUiAlpha,
                        preloadNext = enableVideoPreload && isPageVisible,
                        nextItem = if (enableVideoPreload && isPageVisible && page + 1 < items.size) items[page + 1] else null
                    )
                }
            }

            if (currentItemVal != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = finalUiAlpha }
                        .then(if (finalUiAlpha == 0f) Modifier.zIndex(-1f) else Modifier)
                ) {
                    if (!isManualFullScreen) {
                        TopAppBar(
                            title = {
                                Text(
                                    currentItemVal.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBackClick) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { shareMedia(currentItemVal) }) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = stringResource(R.string.share),
                                        tint = Color.White
                                    )
                                }
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.more),
                                            tint = Color.White
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.rename)) },
                                            onClick = {
                                                showRenameDialog = true
                                                showMoreMenu = false
                                            }
                                        )
                                        if (!currentItemVal.isVideo) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.use_as)) },
                                                onClick = {
                                                    useAs(currentItemVal)
                                                    showMoreMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Black.copy(alpha = 0.3f),
                                scrolledContainerColor = Color.Transparent
                            ),
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                DetailActionItem(
                                    icon = if (currentItemVal.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    label = if (currentItemVal.isFavorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                                    onClick = { toggleFavorite(currentItemVal) },
                                    iconTint = if (currentItemVal.isFavorite) Color.Red else Color.White
                                )
                                DetailActionItem(
                                    icon = Icons.Default.Edit,
                                    label = stringResource(R.string.edit),
                                    onClick = { editMedia(currentItemVal) }
                                )
                                DetailActionItem(
                                    icon = Icons.Default.Info,
                                    label = stringResource(R.string.info),
                                    onClick = { showInfoSheet = true })
                                DetailActionItem(
                                    icon = Icons.Default.Delete,
                                    label = stringResource(R.string.delete),
                                    onClick = { deleteMedia(currentItemVal) })
                            }
                        }
                    }
                }
            }
        }

        if (showInfoSheet && currentItemVal != null) {
            MediaInfoBottomSheet(
                item = currentItemVal,
                context = attributionContext,
                onAddTag = { showAddTagDialog = true },
                onTagClick = { tag ->
                    focusManager.clearFocus()
                    showInfoSheet = false
                    onSearchTrigger(tag)
                },
                onRemoveTag = { tag ->
                    viewModel.updateMediaMetadata(
                        item = currentItemVal,
                        customTags = currentItemVal.customTags - tag
                    )
                }
            ) {
                focusManager.clearFocus()
                showInfoSheet = false
            }
        }

        if (showAddTagDialog && currentItemVal != null) {
            AddTagDialog(
                allTagsFlow = viewModel.allUniqueTags,
                onDismiss = { showAddTagDialog = false },
                onConfirm = { tag ->
                    if (tag.isNotBlank() && !currentItemVal.customTags.contains(tag)) {
                        viewModel.updateMediaMetadata(
                            item = currentItemVal,
                            customTags = currentItemVal.customTags + tag
                        )
                    }
                    showAddTagDialog = false
                }
            )
        }

        if (showRenameDialog && currentItemVal != null) {
            RenameDialog(
                currentName = currentItemVal.name,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    val intentSender = viewModel.renameMedia(currentItemVal, newName)
                    if (intentSender != null) {
                        renameLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    } else {
                        onRefresh()
                    }
                    showRenameDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaInfoBottomSheet(
    item: MediaItem,
    context: Context = LocalContext.current,
    onAddTag: () -> Unit,
    onTagClick: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val configuration = LocalConfiguration.current
        val locale = remember(configuration) {
            try {
                if (configuration.locales.isEmpty) Locale.getDefault() else configuration.locales[0]
            } catch (_: Exception) {
                Locale.getDefault()
            }
        }

        var detailedMetadata by remember { mutableStateOf<DetailedMetadata?>(null) }

        LaunchedEffect(item) {
            detailedMetadata = MediaFileUtils.getDetailedMetadata(context, item.uri, item.isVideo)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            InfoRow(label = "Name", value = item.name)
            InfoRow(label = "Date", value = SimpleDateFormat("dd MMM yyyy, HH:mm", locale).format(Date(item.dateModified * 1000)))

            item.relativePath?.let {
                InfoRow(label = "Path", value = it)
            }

            if (item.width != null && item.height != null) {
                InfoRow(label = "Resolution", value = "${item.width} x ${item.height}")
            }

            if (item.isVideo && item.duration != null) {
                InfoRow(label = "Duration", value = formatTime(item.duration))
            }

            InfoRow(label = "Size", value = String.format(locale, "%.2f MB", item.size / (1024.0 * 1024.0)))

            detailedMetadata?.let { metadata ->
                val cameraInfo = listOfNotNull(
                    metadata.cameraMake,
                    metadata.cameraModel
                ).joinToString(" ")
                if (cameraInfo.isNotBlank()) {
                    InfoRow(label = "Device", value = cameraInfo)
                }

                metadata.software?.let { InfoRow(label = "Software", value = it) }

                if (metadata.aperture != null || metadata.iso != null || metadata.shutterSpeed != null || 
                    metadata.focalLength != null || metadata.flash != null || metadata.whiteBalance != null) {
                    
                    val exposureInfo = listOfNotNull(
                        metadata.aperture,
                        metadata.shutterSpeed,
                        metadata.iso?.let { "ISO $it" },
                        metadata.focalLength,
                        metadata.flash,
                        metadata.whiteBalance?.let { "WB: $it" }
                    ).joinToString(" • ")
                    InfoRow(label = "Exposure Info", value = exposureInfo)
                }

                if (metadata.latitude != null && metadata.longitude != null) {
                    InfoRow(
                        label = "Location",
                        value = String.format(locale, "%.5f, %.5f", metadata.latitude, metadata.longitude)
                    )
                }
            }

            InfoRow(label = "Mime Type", value = item.mimeType)
            InfoRow(label = "URI", value = item.uri.toString())

            Spacer(modifier = Modifier.height(24.dp))

            Text("Tags (Click to search, X to remove)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.customTags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onTagClick(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onRemoveTag(tag) }
                            )
                        }
                    )
                }

                AssistChip(
                    onClick = onAddTag,
                    label = { Text("Add Tag") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
fun MediaPage(
    item: MediaItem,
    isVisible: Boolean,
    isUiVisible: Boolean,
    isManualFullScreen: Boolean,
    autoPlay: Boolean = true,
    startMuted: Boolean = false,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onFullScreenToggle: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onVideoSizeDetermined: (IntSize) -> Unit,
    resetTrigger: Any?,
    uiAlpha: Float = 1f,
    preloadNext: Boolean = false,
    nextItem: MediaItem? = null
) {
    ZoomableBox(
        onZoomChange = onZoomChange,
        onTap = onTap,
        resetTrigger = resetTrigger
    ) {
        if (item.isVideo) {
            VideoPage(
                item = item,
                isVisible = isVisible,
                isUiVisible = isUiVisible,
                isManualFullScreen = isManualFullScreen,
                autoPlay = autoPlay,
                startMuted = startMuted,
                bottomPadding = bottomPadding,
                onFullScreenToggle = onFullScreenToggle,
                onVideoSizeDetermined = onVideoSizeDetermined,
                uiAlpha = uiAlpha,
                preloadNext = preloadNext,
                nextItem = nextItem
            )
        } else {
            PhotoPage(item = item)
        }
    }
}

@Composable
fun PhotoPage(item: MediaItem) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(item.uri)
            .placeholderMemoryCacheKey("${item.uri}_thumb")
            .crossfade(200)
            .build(),
        contentDescription = item.name,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit
    )
}

@AndroidOptIn(UnstableApi::class)
@Composable
fun VideoPage(
    item: MediaItem,
    isVisible: Boolean,
    isUiVisible: Boolean,
    isManualFullScreen: Boolean,
    autoPlay: Boolean = true,
    startMuted: Boolean = false,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onFullScreenToggle: () -> Unit,
    onVideoSizeDetermined: (IntSize) -> Unit,
    uiAlpha: Float = 1f,
    preloadNext: Boolean = false,
    nextItem: MediaItem? = null
) {
    val context = LocalContext.current
    val formatNotSupported = stringResource(R.string.format_not_supported)
    val failedToPlayVideo = stringResource(R.string.failed_to_play_video)
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var nextExoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // 1. Manage ExoPlayer lifecycle
    DisposableEffect(item.uri) {
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        onVideoSizeDetermined(IntSize(videoSize.width, videoSize.height))
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playbackError = when (error.errorCode) {
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> 
                            formatNotSupported
                        else -> failedToPlayVideo
                    }
                }
            })
            prepare()
            playWhenReady = autoPlay
            volume = if (startMuted) 0f else 1f
        }
        exoPlayer = player

        onDispose {
            player.release()
            exoPlayer = null
        }
    }

    // Preload next video
    LaunchedEffect(preloadNext, nextItem) {
        if (preloadNext && nextItem?.isVideo == true) {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(Media3Item.fromUri(nextItem.uri))
                prepare()
                playWhenReady = false
                volume = 0f
            }
            nextExoPlayer = player
        } else {
            nextExoPlayer?.release()
            nextExoPlayer = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            nextExoPlayer?.release()
            nextExoPlayer = null
        }
    }

    // 2. Manage visibility changes (pause/play)
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            exoPlayer?.pause()
        } else if (autoPlay) {
            exoPlayer?.play()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.player = exoPlayer
                }
            )

            if (playbackError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playbackError!!,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            VideoPlayerControls(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = uiAlpha },
                player = exoPlayer!!,
                isVisible = isUiVisible,
                onFullScreenToggle = onFullScreenToggle,
                isFullScreen = isManualFullScreen,
                bottomPadding = bottomPadding
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun VideoPlayerControls(
    modifier: Modifier = Modifier,
    player: Player,
    isVisible: Boolean,
    onFullScreenToggle: () -> Unit,
    isFullScreen: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    var currentPos by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(player.volume == 0f) }
    var playbackSpeed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }

    LaunchedEffect(player, isVisible) {
        while (true) {
            if (!isDragging) {
                currentPos = player.currentPosition
                duration = player.duration.coerceAtLeast(0)
            }
            isPlaying = player.isPlaying
            isMuted = player.volume == 0f
            playbackSpeed = player.playbackParameters.speed
            repeatMode = player.repeatMode
            delay(500.milliseconds)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) }) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.rewind_10s), tint = Color.White, modifier = Modifier.size(48.dp))
                }

                IconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        isPlaying = player.isPlaying
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }

                IconButton(onClick = { player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration)) }) {
                    Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.forward_10s), tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomPadding + 24.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatTime(if (isDragging) dragPos else currentPos)} · ${formatTime(duration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    var showSettingsMenu by remember { mutableStateOf(false) }

                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings), tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            Text(
                                stringResource(R.string.playback_speed),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
                            speeds.forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        player.playbackParameters = PlaybackParameters(speed)
                                        playbackSpeed = speed
                                        showSettingsMenu = false
                                    },
                                    leadingIcon = {
                                        if (playbackSpeed == speed) {
                                            Icon(Icons.Default.Speed, contentDescription = null)
                                        }
                                    }
                                )
                            }

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = {
                                    Text(if (repeatMode == Player.REPEAT_MODE_ONE) stringResource(R.string.repeat_on) else stringResource(R.string.repeat_off))
                                },
                                onClick = {
                                    val newMode = if (repeatMode == Player.REPEAT_MODE_ONE) {
                                        Player.REPEAT_MODE_OFF
                                    } else {
                                        Player.REPEAT_MODE_ONE
                                    }
                                    player.repeatMode = newMode
                                    repeatMode = newMode
                                    showSettingsMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                    IconButton(onClick = {
                        val newMuted = !isMuted
                        player.volume = if (newMuted) 0f else 1f
                        isMuted = newMuted
                    }) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onFullScreenToggle) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullScreen) stringResource(R.string.exit_full_screen) else stringResource(R.string.full_screen),
                            tint = Color.White
                        )
                    }
                }

                Slider(
                    value = if (isDragging) dragPos.toFloat() else currentPos.toFloat(),
                    onValueChange = {
                        isDragging = true
                        dragPos = it.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(dragPos)
                        isDragging = false
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun DetailActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    iconTint: Color = Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.bouncyClick(onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = iconTint)
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
