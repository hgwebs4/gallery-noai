package com.tama.gallerynoai.data.repository

import com.tama.gallerynoai.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.text.SimpleDateFormat

class MediaSearchProvider {

    suspend fun processSearch(
        items: List<MediaItem>,
        query: String,
        options: SearchOptions
    ): List<MediaItem> = withContext(Dispatchers.Default) {
        var filtered = items

        // 1. Universal Search Scope
        if (!options.searchAllFiles && options.currentDirectory != null) {
            filtered = filtered.filter { it.bucketId == options.currentDirectory }
        }

        // 2. Media Type Filtering
        if (options.mediaTypes.isNotEmpty()) {
            filtered = filtered.filter { item ->
                options.mediaTypes.any { type -> matchesType(item, type) }
            }
        }

        // 3. Text Search & Scoring
        if (query.isNotBlank()) {
            val normalizedQuery = query.trim().lowercase()
            val terms = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
            
            filtered = filtered.mapNotNull { item ->
                val score = calculateScore(item, terms)
                if (score > 0) item to score else null
            }.sortedByDescending { it.second }.map { it.first }
        } else {
            // 4. Sorting
            filtered = when (options.sortOrder) {
                SearchSortOrder.FILE_NAME -> filtered.sortedBy { it.name }
                SearchSortOrder.DIRECTORY_PATH -> filtered.sortedBy { it.relativePath }
                SearchSortOrder.FILE_SIZE -> filtered.sortedByDescending { it.size }
                SearchSortOrder.LAST_MODIFIED -> filtered.sortedByDescending { it.dateModified }
                SearchSortOrder.DATE_TAKEN -> filtered.sortedByDescending { it.dateModified }
                SearchSortOrder.RANDOM -> filtered.shuffled()
            }
        }
        filtered
    }

    private fun matchesType(item: MediaItem, type: MediaType): Boolean = when (type) {
        MediaType.IMAGES -> !item.isVideo
        MediaType.VIDEOS -> item.isVideo
        MediaType.GIFS -> item.mimeType == "image/gif"
        MediaType.RAW -> {
            val mime = item.mimeType.lowercase()
            mime.startsWith("image/x-") || 
            mime == "image/vnd.adobe.photoshop" ||
            mime == "image/heic" ||
            mime == "image/heif" ||
            mime == "image/tiff" ||
            item.name.lowercase().let { 
                it.endsWith(".dng") || it.endsWith(".cr2") || it.endsWith(".nef") || 
                it.endsWith(".arw") || it.endsWith(".orf") || it.endsWith(".raf")
            }
        }
        MediaType.SVGS -> item.mimeType == "image/svg+xml" || item.name.lowercase().endsWith(".svg")
        MediaType.PORTRAITS -> false // AI based detecting portraits is removed, maybe we can use some metadata later if available
    }

    private fun calculateScore(item: MediaItem, terms: List<String>): Int {
        var score = 0
        for (term in terms) {
            var termScore = 0
            
            // Special Keyword Handling for Media Types
            val isVideoKeyword = term == "video" || term == "videos"
            val isImageKeyword = term == "image" || term == "images"
            
            if (isVideoKeyword && item.isVideo) {
                termScore += 80
            } else if (isImageKeyword && !item.isVideo) {
                termScore += 80
            }

            if (item.name.contains(term, ignoreCase = true)) termScore += 100
            if (item.customTags.any { it.contains(term, ignoreCase = true) }) termScore += 90
            if (item.relativePath?.contains(term, ignoreCase = true) == true) termScore += 50
            if (termScore == 0) return 0
            score += termScore
        }
        return score
    }

    @Suppress("unused")
    fun groupResults(items: List<MediaItem>, groupBy: GroupBy): Map<String, List<MediaItem>> {
        val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return when (groupBy) {
            GroupBy.TIME_DAILY -> items.groupBy { df.applyPattern("MMM d, yyyy"); df.format(Date(it.dateModified * 1000)) }
            GroupBy.TIME_MONTHLY -> items.groupBy { df.applyPattern("MMMM yyyy"); df.format(Date(it.dateModified * 1000)) }
            GroupBy.TIME_YEARLY -> items.groupBy { df.applyPattern("yyyy"); df.format(Date(it.dateModified * 1000)) }
            GroupBy.EXTENSION -> items.groupBy { it.name.substringAfterLast(".", "unknown").uppercase() }
            GroupBy.MEDIA_TYPE -> items.groupBy { if (it.isVideo) "Videos" else "Images" }
            GroupBy.LOCATION -> items.groupBy { it.relativePath?.substringBeforeLast("/") ?: "Internal Storage" }
        }
    }
}

