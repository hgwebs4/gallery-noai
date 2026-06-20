package com.tama.gallerynoai.data.local.db

data class MediaIdAndDate(
    val id: Long,
    val name: String,
    val dateModified: Long,
    val bucketId: String,
    val bucketName: String?
)
