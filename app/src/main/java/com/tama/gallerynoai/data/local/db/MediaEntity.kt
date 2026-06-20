package com.tama.gallerynoai.data.local.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import com.tama.gallerynoai.data.model.MediaItem
import androidx.core.net.toUri

@Entity(tableName = "media_items")
data class MediaEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val name: String,
    val dateModified: Long,
    val size: Long,
    val mimeType: String,
    val bucketId: String,
    val bucketName: String?,
    val duration: Long?,
    val isVideo: Boolean,
    val customTags: List<String>,
    val isFavorite: Boolean,
    val relativePath: String?,
    val width: Int?,
    val height: Int?
)

@Entity(tableName = "media_items_fts")
@Fts4(contentEntity = MediaEntity::class)
data class MediaFtsEntity(
    val name: String
)

fun MediaEntity.toMediaItem(): MediaItem {
    return MediaItem(
        id = id,
        uri = uri.toUri(),
        name = name,
        dateModified = dateModified,
        size = size,
        mimeType = mimeType,
        bucketId = bucketId,
        bucketName = bucketName,
        duration = duration,
        isVideo = isVideo,
        customTags = customTags,
        isFavorite = isFavorite,
        relativePath = relativePath,
        width = width,
        height = height
    )
}

fun MediaItem.toEntity(): MediaEntity {
    return MediaEntity(
        id = id,
        uri = uri.toString(),
        name = name,
        dateModified = dateModified,
        size = size,
        mimeType = mimeType,
        bucketId = bucketId,
        bucketName = bucketName,
        duration = duration,
        isVideo = isVideo,
        customTags = customTags,
        isFavorite = isFavorite,
        relativePath = relativePath,
        width = width,
        height = height
    )
}

