package com.tama.gallerynoai.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppThemeColor {
    DEFAULT,
    DYNAMIC_COLOR,
    OCEAN_BLUE,
    FOREST_GREEN,
    SUNSET_ORANGE
}

enum class FullscreenRotationMode {
    SYSTEM_SETTING,
    DEVICE_ROTATION,
    ASPECT_RATIO
}

@Singleton
class SettingsManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)

    val themeMode: StateFlow<String> = context.dataStore.data
        .map { it[KEY_THEME_MODE] ?: "System" }
        .stateIn(scope, SharingStarted.Eagerly, "System")

    val dateFormat: StateFlow<String> = context.dataStore.data
        .map { it[KEY_DATE_FORMAT] ?: "dd/MM/yyyy" }
        .stateIn(scope, SharingStarted.Eagerly, "dd/MM/yyyy")

    val useDefaultEditor: StateFlow<Boolean> = context.dataStore.data
        .map { it[KEY_USE_DEFAULT_EDITOR] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val defaultEditorPackage: StateFlow<String?> = context.dataStore.data
        .map { it[KEY_DEFAULT_EDITOR_PACKAGE] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val useDefaultVideoEditor: StateFlow<Boolean> = context.dataStore.data
        .map { it[KEY_USE_DEFAULT_VIDEO_EDITOR] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val autoPlayVideo: StateFlow<Boolean> = context.dataStore.data
        .map { it[KEY_AUTO_PLAY_VIDEO] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val defaultMuteVideo: StateFlow<Boolean> = context.dataStore.data
        .map { it[KEY_DEFAULT_MUTE_VIDEO] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val defaultVideoEditorPackage: StateFlow<String?> = context.dataStore.data
        .map { it[KEY_DEFAULT_VIDEO_EDITOR_PACKAGE] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val enableVideoPreload: StateFlow<Boolean> = context.dataStore.data
        .map { it[KEY_ENABLE_VIDEO_PRELOAD] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val hiddenAlbumIds: StateFlow<Set<String>> = context.dataStore.data
        .map { it[KEY_HIDDEN_ALBUM_IDS] ?: emptySet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val quickAccessItems: StateFlow<Set<String>> = context.dataStore.data
        .map { it[KEY_QUICK_ACCESS_ITEMS] ?: setOf("favorites", "trash", "videos", "screenshots") }
        .stateIn(scope, SharingStarted.Eagerly, setOf("favorites", "trash", "videos", "screenshots"))

    val searchAllFilesByDefault: StateFlow<Boolean> = context.dataStore.data
        .map { it[KEY_SEARCH_ALL_FILES_BY_DEFAULT] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val amoledMode: StateFlow<Boolean> = context.dataStore.data
        .map { it[KEY_AMOLED_MODE] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val gridColumns: StateFlow<Int> = context.dataStore.data
        .map { it[KEY_GRID_COLUMNS] ?: 3 }
        .stateIn(scope, SharingStarted.Eagerly, 3)

    val fullscreenRotationMode: StateFlow<FullscreenRotationMode> = context.dataStore.data
        .map { 
            try {
                FullscreenRotationMode.valueOf(it[KEY_FULLSCREEN_ROTATION_MODE] ?: FullscreenRotationMode.SYSTEM_SETTING.name)
            } catch (e: Exception) {
                FullscreenRotationMode.SYSTEM_SETTING
            }
        }.stateIn(scope, SharingStarted.Eagerly, FullscreenRotationMode.SYSTEM_SETTING)

    val diskCacheMb: StateFlow<Int> = context.dataStore.data
        .map { it[KEY_DISK_CACHE_MB] ?: 512 }
        .stateIn(scope, SharingStarted.Eagerly, 512)

    val showNavLabel: StateFlow<Boolean> = context.dataStore.data
        .map { it[KEY_SHOW_NAV_LABEL] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val defaultSort: StateFlow<String> = context.dataStore.data
        .map { it[KEY_DEFAULT_SORT] ?: "DATE_NEWEST" }
        .stateIn(scope, SharingStarted.Eagerly, "DATE_NEWEST")

    val trashWarningEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { it[KEY_TRASH_WARNING] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val themeColor: StateFlow<AppThemeColor> = context.dataStore.data
        .map { preferences ->
            val colorName = preferences[KEY_THEME_COLOR_NAME] ?: AppThemeColor.DEFAULT.name
            try {
                AppThemeColor.valueOf(colorName)
            } catch (e: Exception) {
                AppThemeColor.DEFAULT
            }
        }.stateIn(scope, SharingStarted.Eagerly, AppThemeColor.DEFAULT)

    fun setThemeColor(color: AppThemeColor) {
        scope.launch {
            context.dataStore.edit { it[KEY_THEME_COLOR_NAME] = color.name }
        }
    }

    fun setThemeMode(mode: String) {
        scope.launch {
            context.dataStore.edit { it[KEY_THEME_MODE] = mode }
        }
    }

    fun setDateFormat(format: String) {
        scope.launch {
            context.dataStore.edit { it[KEY_DATE_FORMAT] = format }
        }
    }

    fun setUseDefaultEditor(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[KEY_USE_DEFAULT_EDITOR] = enabled }
        }
    }

    fun setDefaultEditorPackage(packageName: String?) {
        scope.launch {
            context.dataStore.edit { 
                if (packageName == null) it.remove(KEY_DEFAULT_EDITOR_PACKAGE)
                else it[KEY_DEFAULT_EDITOR_PACKAGE] = packageName
            }
        }
    }

    fun setUseDefaultVideoEditor(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[KEY_USE_DEFAULT_VIDEO_EDITOR] = enabled }
        }
    }

    fun setAutoPlayVideo(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[KEY_AUTO_PLAY_VIDEO] = enabled }
        }
    }

    fun setDefaultMuteVideo(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[KEY_DEFAULT_MUTE_VIDEO] = enabled }
        }
    }

    fun setDefaultVideoEditorPackage(packageName: String?) {
        scope.launch {
            context.dataStore.edit { 
                if (packageName == null) it.remove(KEY_DEFAULT_VIDEO_EDITOR_PACKAGE)
                else it[KEY_DEFAULT_VIDEO_EDITOR_PACKAGE] = packageName
            }
        }
    }

    fun setEnableVideoPreload(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[KEY_ENABLE_VIDEO_PRELOAD] = enabled }
        }
    }

    fun setHiddenAlbumIds(ids: Set<String>) {
        scope.launch {
            context.dataStore.edit { it[KEY_HIDDEN_ALBUM_IDS] = ids }
        }
    }

    fun toggleAlbumHidden(id: String) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val current = prefs[KEY_HIDDEN_ALBUM_IDS] ?: emptySet()
                if (current.contains(id)) {
                    prefs[KEY_HIDDEN_ALBUM_IDS] = current - id
                } else {
                    prefs[KEY_HIDDEN_ALBUM_IDS] = current + id
                }
            }
        }
    }

    fun setQuickAccessItems(items: Set<String>) {
        scope.launch {
            context.dataStore.edit { it[KEY_QUICK_ACCESS_ITEMS] = items }
        }
    }

    fun setSearchAllFilesByDefault(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[KEY_SEARCH_ALL_FILES_BY_DEFAULT] = enabled }
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[KEY_AMOLED_MODE] = enabled }
        }
    }

    fun setGridColumns(columns: Int) {
        scope.launch {
            context.dataStore.edit { it[KEY_GRID_COLUMNS] = columns }
        }
    }

    fun setFullscreenRotationMode(mode: FullscreenRotationMode) {
        scope.launch {
            context.dataStore.edit { it[KEY_FULLSCREEN_ROTATION_MODE] = mode.name }
        }
    }

    fun setDiskCacheMb(mb: Int) {
        scope.launch {
            context.dataStore.edit { it[KEY_DISK_CACHE_MB] = mb }
        }
    }

    fun setShowNavLabel(show: Boolean) {
        scope.launch {
            context.dataStore.edit { it[KEY_SHOW_NAV_LABEL] = show }
        }
    }

    fun setDefaultSort(sort: String) {
        scope.launch {
            context.dataStore.edit { it[KEY_DEFAULT_SORT] = sort }
        }
    }

    fun setTrashWarningEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[KEY_TRASH_WARNING] = enabled }
        }
    }

    companion object {
        val KEY_DISK_CACHE_MB = intPreferencesKey("disk_cache_mb")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DATE_FORMAT = stringPreferencesKey("date_format")
        private val KEY_USE_DEFAULT_EDITOR = booleanPreferencesKey("use_default_editor")
        private val KEY_DEFAULT_EDITOR_PACKAGE = stringPreferencesKey("default_editor_package")
        private val KEY_USE_DEFAULT_VIDEO_EDITOR = booleanPreferencesKey("use_default_video_editor")
        private val KEY_AUTO_PLAY_VIDEO = booleanPreferencesKey("auto_play_video")
        private val KEY_DEFAULT_MUTE_VIDEO = booleanPreferencesKey("default_mute_video")
        private val KEY_DEFAULT_VIDEO_EDITOR_PACKAGE = stringPreferencesKey("default_video_editor_package")
        private val KEY_ENABLE_VIDEO_PRELOAD = booleanPreferencesKey("enable_video_preload")
        private val KEY_HIDDEN_ALBUM_IDS = stringSetPreferencesKey("hidden_album_ids")
        private val KEY_QUICK_ACCESS_ITEMS = stringSetPreferencesKey("quick_access_items")
        private val KEY_SEARCH_ALL_FILES_BY_DEFAULT = booleanPreferencesKey("search_all_files_by_default")
        private val KEY_AMOLED_MODE = booleanPreferencesKey("amoled_mode")
        private val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        private val KEY_FULLSCREEN_ROTATION_MODE = stringPreferencesKey("fullscreen_rotation_mode")
        private val KEY_SHOW_NAV_LABEL = booleanPreferencesKey("show_nav_label")
        private val KEY_DEFAULT_SORT = stringPreferencesKey("default_sort")
        private val KEY_TRASH_WARNING = booleanPreferencesKey("trash_warning_enabled")
        private val KEY_THEME_COLOR_NAME = stringPreferencesKey("theme_color")
    }
}
