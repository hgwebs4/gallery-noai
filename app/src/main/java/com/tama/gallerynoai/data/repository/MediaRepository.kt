package com.tama.gallerynoai.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.tama.gallerynoai.data.local.db.MediaDao
import com.tama.gallerynoai.data.local.db.SearchHistoryDao
import com.tama.gallerynoai.data.local.db.SearchHistoryEntity
import com.tama.gallerynoai.data.local.db.toEntity
import com.tama.gallerynoai.data.local.db.toMediaItem
import com.tama.gallerynoai.data.model.AlbumItem
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.data.model.SortType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map as flowMap

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val searchHistoryDao: SearchHistoryDao
) {

    private companion object {
        const val BUCKET_ID_COL = "bucket_id"
        const val BUCKET_NAME_COL = "bucket_display_name"
    }

    fun getAllMediaFlow(): Flow<List<MediaItem>> {
        return mediaDao.getAllFlow().flowMap { entities ->
            entities.map { it.toMediaItem() }
        }
    }

    suspend fun getMediaItemById(id: Long) = withContext(Dispatchers.IO) {
        mediaDao.getById(id)?.toMediaItem()
    }

    fun getRecentSearches(): Flow<List<String>> = searchHistoryDao.getRecentSearches()

    suspend fun saveSearch(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            searchHistoryDao.insert(SearchHistoryEntity(query, System.currentTimeMillis()))
        }
    }

    suspend fun deleteRecentSearch(query: String) = withContext(Dispatchers.IO) {
        searchHistoryDao.delete(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchHistoryDao.clearAll()
    }

    fun getMediaPaged(sortType: SortType = SortType.DATE_NEWEST, hiddenIds: List<String> = emptyList()): Flow<PagingData<MediaItem>> {
        val nonNullHiddenIds = hiddenIds.ifEmpty { listOf("") }
        return Pager(
            config = PagingConfig(
                pageSize = 100,
                prefetchDistance = 50,
                initialLoadSize = 250,
                enablePlaceholders = true
            ),
            pagingSourceFactory = {
                when (sortType) {
                    SortType.DATE_NEWEST -> mediaDao.getAllPagedDateNewest(nonNullHiddenIds)
                    SortType.DATE_OLDEST -> mediaDao.getAllPagedDateOldest(nonNullHiddenIds)
                    SortType.SIZE_LARGEST -> mediaDao.getAllPagedSizeLargest(nonNullHiddenIds)
                    SortType.SIZE_SMALLEST -> mediaDao.getAllPagedSizeSmallest(nonNullHiddenIds)
                }
            }
        ).flow.flowMap { pagingData ->
            pagingData.map { it.toMediaItem() }
        }
    }

    fun getAlbumStats(): Flow<List<AlbumItem>> {
        return mediaDao.getAlbumStats().flowMap { stats ->
            stats.map { stat ->
                AlbumItem(
                    id = stat.bucketId,
                    name = stat.bucketName ?: "Unknown",
                    coverUri = stat.coverUri.toUri(),
                    itemCount = stat.itemCount,
                    totalSize = stat.totalSize,
                    relativePath = stat.relativePath
                )
            }.sortedBy { it.name }
        }
    }

    fun searchMediaPaged(query: String, hiddenIds: List<String> = emptyList()): Flow<PagingData<MediaItem>> {
        val ftsQuery = if (query.contains("*")) query else "$query*"
        val nonNullHiddenIds = hiddenIds.ifEmpty { listOf("") }
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = true
            ),
            pagingSourceFactory = { mediaDao.searchPaged(ftsQuery, nonNullHiddenIds) }
        ).flow.flowMap { pagingData ->
            pagingData.map { it.toMediaItem() }
        }
    }

    suspend fun syncMediaStoreWithRoom(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val mediaStoreItems = fetchMediaIdsAndDates()
            
            // Safeguard: If MediaStore returns empty but local DB is not empty, 
            // check if we still have access before wiping local data.
            if (mediaStoreItems.isEmpty()) {
                val localCount = mediaDao.getTotalMediaCount().first()
                if (localCount > 0) {
                    val hasAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else {
                        context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    
                    if (!hasAccess) {
                        return@withContext emptyList()
                    }
                }
            }

            val existingItems = mediaDao.getAllIdsAndDates().associateBy { it.id }

            val idsToFetch = mediaStoreItems.filter { item ->
                val existing = existingItems[item.id]
                existing == null || 
                existing.dateModified != item.dateModified || 
                existing.name != item.name || 
                existing.bucketId != item.bucketId ||
                existing.bucketName != item.bucketName
            }.map { it.id }

            if (idsToFetch.isNotEmpty()) {
                val fullMetadata = fetchMediaFullMetadata(idsToFetch)
                val entitiesToInsert = fullMetadata.map { it.toEntity() }
                mediaDao.insertAll(entitiesToInsert)
            }

            val mediaStoreIds = mediaStoreItems.map { it.id }.toSet()
            val idsToDelete = existingItems.keys.filter { it !in mediaStoreIds }

            if (idsToDelete.isNotEmpty()) {
                mediaDao.deleteByIds(idsToDelete)
            }

            return@withContext mediaDao.getAll().map { it.toMediaItem() }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun getAllAlbums(): List<AlbumItem> = fetchAlbums()

    private suspend fun fetchMediaIdsAndDates(): List<com.tama.gallerynoai.data.local.db.MediaIdAndDate> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.tama.gallerynoai.data.local.db.MediaIdAndDate>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            BUCKET_ID_COL,
            BUCKET_NAME_COL
        )
        val queryUri = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val bucketIdx = cursor.getColumnIndexOrThrow(BUCKET_ID_COL)
            val bucketNameIdx = cursor.getColumnIndexOrThrow(BUCKET_NAME_COL)
            while (cursor.moveToNext()) {
                list.add(
                    com.tama.gallerynoai.data.local.db.MediaIdAndDate(
                        cursor.getLong(idIdx),
                        cursor.getString(nameIdx) ?: "",
                        cursor.getLong(dateIdx),
                        cursor.getString(bucketIdx) ?: "",
                        cursor.getString(bucketNameIdx)
                    )
                )
            }
        }
        list
    }

    private suspend fun fetchMediaFullMetadata(ids: List<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val mediaList = mutableListOf<MediaItem>()
        val queryUri = MediaStore.Files.getContentUri("external")

        // Increased chunk size and optimized query for large collections
        ids.chunked(1000).forEach { chunk ->
            val selection = "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})"
            val projection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DURATION,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                BUCKET_ID_COL,
                BUCKET_NAME_COL,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                projection.add(MediaStore.MediaColumns.IS_TRASHED)
                projection.add(MediaStore.MediaColumns.IS_FAVORITE)
            }

            context.contentResolver.query(queryUri, projection.toTypedArray(), selection, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val durIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val typeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val bucketIdx = cursor.getColumnIndex(BUCKET_ID_COL)
                val relativePathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val widthIdx = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightIdx = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val favoriteIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx)
                    val dateModified = cursor.getLong(dateIdx)
                    val size = cursor.getLong(sizeIdx)
                    val mimeType = cursor.getString(mimeIdx)
                    val duration = if (!cursor.isNull(durIdx)) cursor.getLong(durIdx) else null
                    val mediaType = cursor.getInt(typeIdx)
                    val bucketId = if (bucketIdx != -1) cursor.getString(bucketIdx) ?: "" else ""
                    val bucketName = if (bucketIdx != -1) {
                        val bucketNameIdx = cursor.getColumnIndex(BUCKET_NAME_COL)
                        if (bucketNameIdx != -1) cursor.getString(bucketNameIdx) else null
                    } else null
                    val relativePath = if (relativePathIdx != -1) cursor.getString(relativePathIdx) else null
                    val width = if (widthIdx != -1 && !cursor.isNull(widthIdx)) cursor.getInt(widthIdx) else null
                    val height = if (heightIdx != -1 && !cursor.isNull(heightIdx)) cursor.getInt(heightIdx) else null
                    val isFavorite = if (favoriteIdx != -1) cursor.getInt(favoriteIdx) == 1 else false

                    val contentUri = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            name = name,
                            dateModified = dateModified,
                            size = size,
                            mimeType = mimeType,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            duration = duration,
                            isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                            isFavorite = isFavorite,
                            relativePath = relativePath,
                            width = width,
                            height = height
                        )
                    )
                }
            }
        }
        mediaList
    }

    suspend fun updateMediaMetadata(
        id: Long,
        customTags: List<String>? = null
    ) = withContext(Dispatchers.IO) {
        val existing = mediaDao.getById(id) ?: return@withContext
        val updated = existing.copy(
            customTags = customTags ?: existing.customTags
        )
        mediaDao.update(updated)
    }

    suspend fun getAllCustomTags(): List<String> = withContext(Dispatchers.IO) {
        mediaDao.getAllCustomTags()
    }

    suspend fun fetchAlbums(): List<AlbumItem> = withContext(Dispatchers.IO) {
        val albumsMap = mutableMapOf<String, AlbumInfo>()

        val projection = mutableListOf(
            BUCKET_ID_COL,
            BUCKET_NAME_COL,
            MediaStore.MediaColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val queryUri = MediaStore.Files.getContentUri("external")

        val queryArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_DEFAULT)
            }
        } else null

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.contentResolver.query(queryUri, projection.toTypedArray(), queryArgs!!, null)
            } else {
                context.contentResolver.query(queryUri, projection.toTypedArray(), selection, selectionArgs, sortOrder)
            }

            cursor?.use {
                val bucketIdIdx = cursor.getColumnIndex(BUCKET_ID_COL)
                val bucketNameIdx = cursor.getColumnIndex(BUCKET_NAME_COL)
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val typeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val relativePathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

                while (cursor.moveToNext()) {
                    val bucketId = if (bucketIdIdx != -1) cursor.getString(bucketIdIdx) else null
                    if (bucketId == null) continue

                    val bucketName = if (bucketNameIdx != -1) cursor.getString(bucketNameIdx) ?: "Unknown" else "Unknown"
                    val id = cursor.getLong(idIdx)
                    val mediaType = cursor.getInt(typeIdx)
                    val relativePath = if (relativePathIdx != -1) cursor.getString(relativePathIdx) else null
                    val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L

                    val contentUri = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }

                    val current = albumsMap[bucketId]
                    if (current == null) {
                        albumsMap[bucketId] = AlbumInfo(bucketName, contentUri, 1, size, relativePath)
                    } else {
                        albumsMap[bucketId] = current.copy(
                            count = current.count + 1,
                            totalSize = current.totalSize + size
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        albumsMap.map { (id, info) ->
            AlbumItem(id, info.name, info.coverUri, info.count, info.totalSize, info.relativePath)
        }.sortedBy { it.name }
    }

    private data class AlbumInfo(val name: String, val coverUri: Uri, val count: Int, val totalSize: Long, val relativePath: String?)

    fun observeMediaChange(): Flow<Unit> = callbackFlow {
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            observer
        )

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    fun createRestoreRequest(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createTrashRequest(context.contentResolver, uris, false).intentSender
        }
        return null
    }

    fun createDeletePermanentlyRequest(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }
        return null
    }

    fun createTrashRequest(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender
        }
        return null
    }

    suspend fun fetchTrashedMedia(): List<MediaItem> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        fetchMediaInternal()
    } else {
        emptyList()
    }

    fun createFavoriteRequest(uris: List<Uri>, favorite: Boolean): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createFavoriteRequest(context.contentResolver, uris, favorite).intentSender
        }
        return null
    }

    fun moveMedia(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
        }
        return null
    }

    fun renameMedia(uri: Uri, newName: String): IntentSender? {
        val currentName = getDisplayNameFromUri(uri) ?: return null
        val extension = currentName.substringAfterLast('.', "")
        
        val finalName = if (extension.isNotEmpty() && !newName.endsWith(".$extension", ignoreCase = true)) {
            "$newName.$extension"
        } else {
            newName
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
        }

        return try {
            val updated = context.contentResolver.update(uri, contentValues, null, null)
            if (updated == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // If update returned 0 but no exception, it might be due to scoped storage permissions on some devices/scenarios
                // Requesting explicit write permission via createWriteRequest
                MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
            } else {
                null
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException = e as? RecoverableSecurityException
                    ?: throw e
                recoverableSecurityException.userAction.actionIntent.intentSender
            } else {
                throw e
            }
        }
    }

    suspend fun performMove(uris: List<Uri>, targetRelativePath: String) = withContext(Dispatchers.IO) {
        val formattedPath = ensureStandardPath(targetRelativePath)
        
        uris.forEach { uri ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, formattedPath)
                    }
                    context.contentResolver.update(uri, contentValues, null, null)
                } else {
                    // Fallback for API 26-28: Use the DATA column
                    val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val oldPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                            
                            val externalStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
                            val newDir = java.io.File(externalStorage, formattedPath)
                            if (!newDir.exists()) newDir.mkdirs()
                            
                            val newFile = java.io.File(newDir, displayName)
                            val oldFile = java.io.File(oldPath)
                            
                            if (oldFile.renameTo(newFile)) {
                                val values = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DATA, newFile.absolutePath)
                                }
                                context.contentResolver.update(uri, values, null, null)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun copyMedia(uris: List<Uri>, targetRelativePath: String): Boolean = withContext(Dispatchers.IO) {
        val formattedPath = ensureStandardPath(targetRelativePath)
        var success = true
        uris.forEach { uri ->
            try {
                val mimeType = context.contentResolver.getType(uri)
                val displayName = getDisplayNameFromUri(uri) ?: "Media_${System.currentTimeMillis()}"

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, formattedPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = if (mimeType?.startsWith("video") == true) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val destinationUri = context.contentResolver.insert(collection, contentValues)
                if (destinationUri != null) {
                    try {
                        context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(destinationUri, contentValues, null, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        context.contentResolver.delete(destinationUri, null, null)
                        success = false
                    }
                } else {
                    success = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                success = false
            }
        }
        success
    }

    private fun ensureStandardPath(path: String): String {
        val formattedPath = if (path.endsWith("/")) path else "$path/"
        val allowedDirectories = listOf("DCIM/", "Pictures/", "Movies/", "Download/")
        val startsWithAllowed = allowedDirectories.any { formattedPath.startsWith(it, ignoreCase = true) }

        if (startsWithAllowed) {
            if (formattedPath.contains("Android/", ignoreCase = true)) {
                return "Pictures/${formattedPath.replace("/", "_")}"
            }
            return formattedPath
        }

        return "Pictures/$formattedPath"
    }

    private fun getDisplayNameFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchMediaInternal(): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            BUCKET_ID_COL,
            BUCKET_NAME_COL,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.MediaColumns.IS_TRASHED)
            projection.add(MediaStore.MediaColumns.IS_FAVORITE)
        }

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val queryUri = MediaStore.Files.getContentUri("external")

        val queryArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
        } else null

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.contentResolver.query(queryUri, projection.toTypedArray(), queryArgs!!, null)
            } else {
                context.contentResolver.query(queryUri, projection.toTypedArray(), selection, selectionArgs, sortOrder)
            }

            cursor?.use {
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val durIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val typeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val bucketIdx = cursor.getColumnIndex(BUCKET_ID_COL)
                val relativePathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val widthIdx = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightIdx = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val favoriteIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx)
                    val dateModified = cursor.getLong(dateIdx)
                    val size = cursor.getLong(sizeIdx)
                    val mimeType = cursor.getString(mimeIdx)
                    val duration = if (!cursor.isNull(durIdx)) cursor.getLong(durIdx) else null
                    val mediaType = cursor.getInt(typeIdx)
                    val bucketId = if (bucketIdx != -1) cursor.getString(bucketIdx) ?: "" else ""
                    val bucketName = if (bucketIdx != -1) {
                        val bucketNameIdx = cursor.getColumnIndex(BUCKET_NAME_COL)
                        if (bucketNameIdx != -1) cursor.getString(bucketNameIdx) else null
                    } else null
                    val relativePath = if (relativePathIdx != -1) cursor.getString(relativePathIdx) else null
                    val width = if (widthIdx != -1 && !cursor.isNull(widthIdx)) cursor.getInt(widthIdx) else null
                    val height = if (heightIdx != -1 && !cursor.isNull(heightIdx)) cursor.getInt(heightIdx) else null
                    val isFavorite = if (favoriteIdx != -1) cursor.getInt(favoriteIdx) == 1 else false

                    val contentUri = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            name = name,
                            dateModified = dateModified,
                            size = size,
                            mimeType = mimeType,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            duration = duration,
                            isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                            isFavorite = isFavorite,
                            relativePath = relativePath,
                            width = width,
                            height = height
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaList
    }
}
