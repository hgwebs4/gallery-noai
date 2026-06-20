package com.tama.gallerynoai.data.model

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateModified: Long,
    val size: Long,
    val mimeType: String,
    val bucketId: String,
    val bucketName: String? = null,
    val duration: Long? = null,
    val isVideo: Boolean = false,
    val customTags: List<String> = emptyList(), // Manually added tags
    val isFavorite: Boolean = false,
    val relativePath: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

