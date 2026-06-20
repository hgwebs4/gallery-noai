package com.tama.gallerynoai.data.local.db

data class AlbumStatEntity(
    val bucketId: String,
    val bucketName: String?, // We might need to add bucketName to MediaEntity or handle it differently
    val coverUri: String,
    val itemCount: Int,
    val totalSize: Long,
    val relativePath: String?
)
