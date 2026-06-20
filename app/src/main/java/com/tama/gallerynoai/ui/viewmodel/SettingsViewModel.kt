package com.tama.gallerynoai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tama.gallerynoai.data.settings.AppThemeColor
import com.tama.gallerynoai.data.settings.FullscreenRotationMode
import com.tama.gallerynoai.data.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.tama.gallerynoai.data.model.AlbumItem
import com.tama.gallerynoai.data.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settingsManager: SettingsManager,
    private val repository: MediaRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = settingsManager.themeMode
    val themeColor: StateFlow<AppThemeColor> = settingsManager.themeColor
    val dateFormat: StateFlow<String> = settingsManager.dateFormat
    val useDefaultEditor: StateFlow<Boolean> = settingsManager.useDefaultEditor
    val defaultEditorPackage: StateFlow<String?> = settingsManager.defaultEditorPackage
    val useDefaultVideoEditor: StateFlow<Boolean> = settingsManager.useDefaultVideoEditor
    val autoPlayVideo: StateFlow<Boolean> = settingsManager.autoPlayVideo
    val defaultMuteVideo: StateFlow<Boolean> = settingsManager.defaultMuteVideo
    val defaultVideoEditorPackage: StateFlow<String?> = settingsManager.defaultVideoEditorPackage
    val enableVideoPreload: StateFlow<Boolean> = settingsManager.enableVideoPreload
    val hiddenAlbumIds: StateFlow<Set<String>> = settingsManager.hiddenAlbumIds
    val quickAccessItems: StateFlow<Set<String>> = settingsManager.quickAccessItems
    val searchAllFilesByDefault: StateFlow<Boolean> = settingsManager.searchAllFilesByDefault
    val amoledMode: StateFlow<Boolean> = settingsManager.amoledMode
    val gridColumns: StateFlow<Int> = settingsManager.gridColumns
    val fullscreenRotationMode: StateFlow<FullscreenRotationMode> = settingsManager.fullscreenRotationMode
    val diskCacheMb: StateFlow<Int> = settingsManager.diskCacheMb
    val showNavLabel: StateFlow<Boolean> = settingsManager.showNavLabel
    val defaultSort: StateFlow<String> = settingsManager.defaultSort
    val trashWarningEnabled: StateFlow<Boolean> = settingsManager.trashWarningEnabled

    private val _scrollToTopTrigger = MutableSharedFlow<Unit>(replay = 0)
    val scrollToTopTrigger: SharedFlow<Unit> = _scrollToTopTrigger.asSharedFlow()

    fun triggerScrollToTop() {
        viewModelScope.launch {
            _scrollToTopTrigger.emit(Unit)
        }
    }

    fun setThemeMode(mode: String) = settingsManager.setThemeMode(mode)
    fun setThemeColor(color: AppThemeColor) = settingsManager.setThemeColor(color)
    fun setDateFormat(format: String) = settingsManager.setDateFormat(format)
    fun toggleUseDefaultEditor(enabled: Boolean) = settingsManager.setUseDefaultEditor(enabled)
    fun setDefaultEditorPackage(packageName: String?) = settingsManager.setDefaultEditorPackage(packageName)
    fun toggleUseDefaultVideoEditor(enabled: Boolean) = settingsManager.setUseDefaultVideoEditor(enabled)
    fun setAutoPlayVideo(enabled: Boolean) = settingsManager.setAutoPlayVideo(enabled)
    fun setDefaultMuteVideo(enabled: Boolean) = settingsManager.setDefaultMuteVideo(enabled)
    fun setDefaultVideoEditorPackage(packageName: String?) = settingsManager.setDefaultVideoEditorPackage(packageName)
    fun setEnableVideoPreload(enabled: Boolean) = settingsManager.setEnableVideoPreload(enabled)
    fun setHiddenAlbumIds(ids: Set<String>) = settingsManager.setHiddenAlbumIds(ids)
    fun toggleAlbumHidden(id: String) = settingsManager.toggleAlbumHidden(id)
    fun setQuickAccessItems(items: Set<String>) = settingsManager.setQuickAccessItems(items)
    fun setSearchAllFilesByDefault(enabled: Boolean) = settingsManager.setSearchAllFilesByDefault(enabled)
    fun setAmoledMode(enabled: Boolean) = settingsManager.setAmoledMode(enabled)
    fun setGridColumns(columns: Int) = settingsManager.setGridColumns(columns)
    fun setFullscreenRotationMode(mode: FullscreenRotationMode) = settingsManager.setFullscreenRotationMode(mode)
    fun setDiskCacheMb(mb: Int) = settingsManager.setDiskCacheMb(mb)
    fun setShowNavLabel(show: Boolean) = settingsManager.setShowNavLabel(show)
    fun setDefaultSort(sort: String) = settingsManager.setDefaultSort(sort)
    fun setTrashWarningEnabled(enabled: Boolean) = settingsManager.setTrashWarningEnabled(enabled)

    fun getAllAlbumsFlow(): Flow<List<AlbumItem>> = flow {
        emit(repository.getAllAlbums())
    }
}
