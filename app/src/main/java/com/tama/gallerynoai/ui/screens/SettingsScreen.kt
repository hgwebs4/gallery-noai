package com.tama.gallerynoai.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.settings.AppThemeColor
import com.tama.gallerynoai.data.settings.FullscreenRotationMode
import com.tama.gallerynoai.ui.viewmodel.SettingsViewModel

data class EditorApp(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dateFormat by viewModel.dateFormat.collectAsStateWithLifecycle()
    val useDefaultEditor by viewModel.useDefaultEditor.collectAsStateWithLifecycle()
    val defaultEditorPackage by viewModel.defaultEditorPackage.collectAsStateWithLifecycle()
    val useDefaultVideoEditor by viewModel.useDefaultVideoEditor.collectAsStateWithLifecycle()
    val autoPlayVideo by viewModel.autoPlayVideo.collectAsStateWithLifecycle()
    val defaultMuteVideo by viewModel.defaultMuteVideo.collectAsStateWithLifecycle()
    val defaultVideoEditorPackage by viewModel.defaultVideoEditorPackage.collectAsStateWithLifecycle()
    val searchAllFilesByDefault by viewModel.searchAllFilesByDefault.collectAsStateWithLifecycle()
    val amoledMode by viewModel.amoledMode.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val fullscreenRotationMode by viewModel.fullscreenRotationMode.collectAsStateWithLifecycle()
    val diskCacheMb by viewModel.diskCacheMb.collectAsStateWithLifecycle()
    val showNavLabel by viewModel.showNavLabel.collectAsStateWithLifecycle()
    val defaultSort by viewModel.defaultSort.collectAsStateWithLifecycle()
    val trashWarningEnabled by viewModel.trashWarningEnabled.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val hiddenAlbumIds by viewModel.hiddenAlbumIds.collectAsStateWithLifecycle()
    val quickAccessItems by viewModel.quickAccessItems.collectAsStateWithLifecycle()
    val enableVideoPreload by viewModel.enableVideoPreload.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showDateFormatDialog by remember { mutableStateOf(false) }
    var showGridColumnsDialog by remember { mutableStateOf(false) }
    var showRotationModeDialog by remember { mutableStateOf(false) }
    var showDiskCacheDialog by remember { mutableStateOf(false) }
    var showDefaultSortDialog by remember { mutableStateOf(false) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var showVideoEditorDialog by remember { mutableStateOf(false) }
    var showHiddenAlbumsDialog by remember { mutableStateOf(false) }
    var showQuickAccessDialog by remember { mutableStateOf(false) }
    var availableEditors by remember { mutableStateOf<List<EditorApp>>(emptyList()) }
    var availableVideoEditors by remember { mutableStateOf<List<EditorApp>>(emptyList()) }

    fun loadEditors(isVideos: Boolean = false) {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            type = if (isVideos) "video/*" else "image/*"
        }
        val packageManager = context.packageManager
        val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .ifEmpty { packageManager.queryIntentActivities(intent, 0) }

        val editors = resolveInfos.map {
            EditorApp(
                name = it.loadLabel(packageManager).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(packageManager)
            )
        }

        if (isVideos) {
            availableVideoEditors = editors
        } else {
            availableEditors = editors
        }
    }

    LaunchedEffect(Unit) {
        loadEditors(false)
        loadEditors(true)
        viewModel.scrollToTopTrigger.collect {
            scrollState.animateScrollTo(0)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_settings),
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            SettingsSectionHeader(stringResource(R.string.settings_general))
            ListItem(
                modifier = Modifier.clickable { showHiddenAlbumsDialog = true },
                headlineContent = { Text(stringResource(R.string.manage_hidden_albums)) },
                supportingContent = { Text(stringResource(R.string.manage_hidden_albums_desc)) },
                leadingContent = { Icon(Icons.Default.VisibilityOff, contentDescription = null) }
            )
            ListItem(
                modifier = Modifier.clickable { showQuickAccessDialog = true },
                headlineContent = { Text(stringResource(R.string.configure_quick_access)) },
                supportingContent = { Text(stringResource(R.string.configure_quick_access_desc)) },
                leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) }
            )
            ListItem(
                modifier = Modifier.clickable { showDefaultSortDialog = true },
                headlineContent = { Text(stringResource(R.string.default_sort_order)) },
                supportingContent = {
                    val label = when (defaultSort) {
                        "DATE_NEWEST" -> stringResource(R.string.sort_newest_label)
                        "DATE_OLDEST" -> stringResource(R.string.sort_oldest_label)
                        "SIZE_LARGEST" -> stringResource(R.string.sort_largest_label)
                        "SIZE_SMALLEST" -> stringResource(R.string.sort_smallest_label)
                        else -> defaultSort
                    }
                    Text(label)
                },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.search_all_files)) },
                supportingContent = { Text(stringResource(R.string.search_all_files_desc)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = searchAllFilesByDefault,
                        onCheckedChange = { viewModel.setSearchAllFilesByDefault(it) }
                    )
                }
            )

            ListItem(
                modifier = Modifier.clickable { showDateFormatDialog = true },
                headlineContent = { Text(stringResource(R.string.date_format)) },
                supportingContent = { Text(dateFormat) },
                leadingContent = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
            )
            ListItem(
                modifier = Modifier.clickable { showRotationModeDialog = true },
                headlineContent = { Text(stringResource(R.string.fullscreen_rotation_mode)) },
                supportingContent = {
                    val label = when (fullscreenRotationMode) {
                        FullscreenRotationMode.SYSTEM_SETTING -> stringResource(R.string.rotation_system)
                        FullscreenRotationMode.DEVICE_ROTATION -> stringResource(R.string.rotation_device)
                        FullscreenRotationMode.ASPECT_RATIO -> stringResource(R.string.rotation_aspect)
                    }
                    Text(label)
                },
                leadingContent = { Icon(Icons.Default.ScreenRotation, contentDescription = null) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_appearance))
            ListItem(
                modifier = Modifier.clickable { showGridColumnsDialog = true },
                headlineContent = { Text(stringResource(R.string.grid_columns)) },
                supportingContent = { Text(stringResource(R.string.grid_columns_count, gridColumns)) },
                leadingContent = { Icon(Icons.Default.GridView, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.show_nav_labels)) },
                supportingContent = { Text(stringResource(R.string.show_nav_labels_desc)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = showNavLabel,
                        onCheckedChange = { viewModel.setShowNavLabel(it) }
                    )
                }
            )
            ListItem(
                modifier = Modifier.clickable { showThemeDialog = true },
                headlineContent = { Text(stringResource(R.string.app_theme)) },
                supportingContent = { Text(themeMode) },
                leadingContent = { Icon(Icons.Default.Brightness4, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.amoled_black_mode)) },
                supportingContent = { Text(stringResource(R.string.amoled_black_mode_desc)) },
                leadingContent = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = amoledMode,
                        onCheckedChange = { viewModel.setAmoledMode(it) },
                        enabled = themeMode == "Dark" || themeMode == "System"
                    )
                }
            )
            ListItem(
                modifier = Modifier.clickable { showThemeColorDialog = true },
                headlineContent = { Text(stringResource(R.string.theme_color)) },
                supportingContent = { Text(themeColor.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }) },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_privacy_storage))
            ListItem(
                modifier = Modifier.clickable { showDiskCacheDialog = true },
                headlineContent = { Text(stringResource(R.string.thumbnail_cache_size)) },
                supportingContent = { Text(stringResource(R.string.cache_size_mb, diskCacheMb)) },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.trash_deletion_warning)) },
                supportingContent = { Text(stringResource(R.string.trash_deletion_warning_desc)) },
                leadingContent = { Icon(Icons.Default.RestoreFromTrash, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = trashWarningEnabled,
                        onCheckedChange = { viewModel.setTrashWarningEnabled(it) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_editor))
            ListItem(
                headlineContent = { Text(stringResource(R.string.enable_video_preload)) },
                supportingContent = { Text(stringResource(R.string.enable_video_preload_desc)) },
                leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = enableVideoPreload,
                        onCheckedChange = { viewModel.setEnableVideoPreload(it) }
                    )
                }
            )
            ListItem(
                modifier = Modifier.clickable {
                    loadEditors(false)
                    showEditorDialog = true
                },
                headlineContent = { Text(stringResource(R.string.use_default_photo_editor)) },
                supportingContent = {
                    val currentEditor = availableEditors.find { it.packageName == defaultEditorPackage }?.name ?: stringResource(R.string.none_selected)
                    Text(stringResource(R.string.selected_editor, currentEditor))
                },
                leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = useDefaultEditor,
                        onCheckedChange = { viewModel.toggleUseDefaultEditor(it) }
                    )
                }
            )
            ListItem(
                modifier = Modifier.clickable {
                    loadEditors(true)
                    showVideoEditorDialog = true
                },
                headlineContent = { Text(stringResource(R.string.use_default_video_editor)) },
                supportingContent = {
                    val currentEditor = availableVideoEditors.find { it.packageName == defaultVideoEditorPackage }?.name ?: stringResource(R.string.none_selected)
                    Text(stringResource(R.string.selected_editor, currentEditor))
                },
                leadingContent = { Icon(Icons.Default.Movie, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = useDefaultVideoEditor,
                        onCheckedChange = { viewModel.toggleUseDefaultVideoEditor(it) }
                    )
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.auto_play_video)) },
                supportingContent = { Text(stringResource(R.string.auto_play_video_desc)) },
                leadingContent = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = autoPlayVideo,
                        onCheckedChange = { viewModel.setAutoPlayVideo(it) }
                    )
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.default_mute_video)) },
                supportingContent = { Text(stringResource(R.string.default_mute_video_desc)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = defaultMuteVideo,
                        onCheckedChange = { viewModel.setDefaultMuteVideo(it) }
                    )
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.version_author),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showGridColumnsDialog) {
        AlertDialog(
            onDismissRequest = { showGridColumnsDialog = false },
            title = { Text(stringResource(R.string.grid_columns)) },
            text = {
                Column {
                    listOf(2, 3, 4, 5).forEach { columns ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setGridColumns(columns)
                                    showGridColumnsDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = gridColumns == columns, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.grid_columns_count, columns))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGridColumnsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRotationModeDialog) {
        AlertDialog(
            onDismissRequest = { showRotationModeDialog = false },
            title = { Text(stringResource(R.string.fullscreen_rotation_mode)) },
            text = {
                Column {
                    listOf(
                        FullscreenRotationMode.SYSTEM_SETTING to stringResource(R.string.rotation_system),
                        FullscreenRotationMode.DEVICE_ROTATION to stringResource(R.string.rotation_device),
                        FullscreenRotationMode.ASPECT_RATIO to stringResource(R.string.rotation_aspect)
                    ).forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setFullscreenRotationMode(mode)
                                    showRotationModeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = fullscreenRotationMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRotationModeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDiskCacheDialog) {
        AlertDialog(
            onDismissRequest = { showDiskCacheDialog = false },
            title = { Text(stringResource(R.string.thumbnail_cache_size)) },
            text = {
                Column {
                    listOf(256, 512, 1024).forEach { size ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDiskCacheMb(size)
                                    showDiskCacheDialog = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.restart_app_cache))
                                    }
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = diskCacheMb == size, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.cache_size_mb, size))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiskCacheDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDefaultSortDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultSortDialog = false },
            title = { Text(stringResource(R.string.default_sort_order)) },
            text = {
                Column {
                    listOf(
                        "DATE_NEWEST" to stringResource(R.string.sort_newest_label),
                        "DATE_OLDEST" to stringResource(R.string.sort_oldest_label),
                        "SIZE_LARGEST" to stringResource(R.string.sort_largest_label),
                        "SIZE_SMALLEST" to stringResource(R.string.sort_smallest_label)
                    ).forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDefaultSort(value)
                                    showDefaultSortDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = defaultSort == value, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDefaultSortDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showThemeColorDialog) {
        AlertDialog(
            onDismissRequest = { showThemeColorDialog = false },
            title = { Text(stringResource(R.string.theme_color)) },
            text = {
                Column {
                    AppThemeColor.entries.forEach { color ->
                        val isAvailable = if (color == AppThemeColor.DYNAMIC_COLOR) {
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                        } else true

                        if (isAvailable) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeColor(color)
                                        showThemeColorDialog = false
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                RadioButton(selected = themeColor == color, onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(color.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeColorDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.app_theme)) },
            text = {
                Column {
                    listOf("System", "Light", "Dark").forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDateFormatDialog) {
        AlertDialog(
            onDismissRequest = { showDateFormatDialog = false },
            title = { Text(stringResource(R.string.date_format)) },
            text = {
                Column {
                    listOf("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "dd MMM yyyy").forEach { format ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDateFormat(format)
                                    showDateFormatDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = dateFormat == format, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(format)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDateFormatDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEditorDialog) {
        AlertDialog(
            onDismissRequest = { showEditorDialog = false },
            title = { Text(stringResource(R.string.use_default_photo_editor)) },
            text = {
                LazyColumn {
                    items(availableEditors) { editor ->
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.setDefaultEditorPackage(editor.packageName)
                                showEditorDialog = false
                            },
                            headlineContent = { Text(editor.name) },
                            leadingContent = {
                                Image(
                                    bitmap = editor.icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                            },
                            trailingContent = {
                                RadioButton(
                                    selected = defaultEditorPackage == editor.packageName,
                                    onClick = null
                                )
                            }
                        )
                    }
                    if (availableEditors.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_photo_editors_found),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEditorDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showVideoEditorDialog) {
        AlertDialog(
            onDismissRequest = { showVideoEditorDialog = false },
            title = { Text(stringResource(R.string.use_default_video_editor)) },
            text = {
                LazyColumn {
                    items(availableVideoEditors) { editor ->
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.setDefaultVideoEditorPackage(editor.packageName)
                                showVideoEditorDialog = false
                            },
                            headlineContent = { Text(editor.name) },
                            leadingContent = {
                                Image(
                                    bitmap = editor.icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                            },
                            trailingContent = {
                                RadioButton(
                                    selected = defaultVideoEditorPackage == editor.packageName,
                                    onClick = null
                                )
                            }
                        )
                    }
                    if (availableVideoEditors.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_video_editors_found),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoEditorDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showHiddenAlbumsDialog) {
        val allAlbums by viewModel.getAllAlbumsFlow().collectAsState(initial = emptyList())
        val hiddenAlbums = allAlbums.filter { it.id in hiddenAlbumIds }

        AlertDialog(
            onDismissRequest = { showHiddenAlbumsDialog = false },
            title = { Text(stringResource(R.string.manage_hidden_albums)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(hiddenAlbums) { album ->
                        ListItem(
                            headlineContent = { Text(album.name) },
                            trailingContent = {
                                TextButton(onClick = { viewModel.toggleAlbumHidden(album.id) }) {
                                    Text(stringResource(R.string.unhide))
                                }
                            }
                        )
                    }
                    if (hiddenAlbums.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_hidden_albums),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHiddenAlbumsDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showQuickAccessDialog) {
        val options = listOf(
            "favorites" to stringResource(R.string.favorites),
            "trash" to stringResource(R.string.trash),
            "videos" to stringResource(R.string.videos),
            "screenshots" to stringResource(R.string.screenshots)
        )

        AlertDialog(
            onDismissRequest = { showQuickAccessDialog = false },
            title = { Text(stringResource(R.string.configure_quick_access)) },
            text = {
                Column {
                    options.forEach { (id, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newSet = if (quickAccessItems.contains(id)) {
                                        quickAccessItems - id
                                    } else {
                                        quickAccessItems + id
                                    }
                                    viewModel.setQuickAccessItems(newSet)
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Checkbox(checked = quickAccessItems.contains(id), onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickAccessDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
