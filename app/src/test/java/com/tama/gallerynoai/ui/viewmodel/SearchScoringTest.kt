package com.tama.gallerynoai.ui.viewmodel

import android.net.Uri
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.data.model.SearchOptions
import com.tama.gallerynoai.data.repository.MediaSearchProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class SearchScoringTest {

    private val searchProvider = MediaSearchProvider()

    @Test
    fun testProcessSearch_NameMatch() = runBlocking {
        val item1 = createMediaItem(name = "dog_photo.jpg")
        val item2 = createMediaItem(name = "cat_photo.jpg")
        val items = listOf(item1, item2)
        
        val results = searchProvider.processSearch(items, "dog", SearchOptions())
        
        assertEquals(1, results.size)
        assertEquals("dog_photo.jpg", results[0].name)
    }

    @Test
    fun testProcessSearch_CustomTagMatch() = runBlocking {
        val item1 = createMediaItem(customTags = listOf("holiday"))
        val item2 = createMediaItem(customTags = listOf("work"))
        val items = listOf(item1, item2)
        
        val results = searchProvider.processSearch(items, "holiday", SearchOptions())
        
        assertEquals(1, results.size)
        assertTrue(results[0].customTags.contains("holiday"))
    }

    @Test
    fun testProcessSearch_MultipleTerms() = runBlocking {
        val item1 = createMediaItem(name = "dog playing in park.jpg")
        val item2 = createMediaItem(name = "dog.jpg")
        val items = listOf(item1, item2)
        
        // Both match "dog", but only item1 matches "park"
        val results = searchProvider.processSearch(items, "dog park", SearchOptions())
        
        assertEquals(1, results.size)
        assertEquals("dog playing in park.jpg", results[0].name)
    }

    @Test
    fun testProcessSearch_NoMatch() = runBlocking {
        val item = createMediaItem(name = "cat.jpg")
        val items = listOf(item)
        
        val results = searchProvider.processSearch(items, "dog", SearchOptions())
        
        assertTrue(results.isEmpty())
    }

    @Test
    fun testProcessSearch_VideoKeyword() = runBlocking {
        val video = createMediaItem(name = "vacation.mp4", isVideo = true)
        val image = createMediaItem(name = "vacation.jpg", isVideo = false)
        val items = listOf(video, image)
        
        val results = searchProvider.processSearch(items, "video", SearchOptions())
        
        assertEquals(1, results.size)
        assertTrue(results[0].isVideo)
        assertEquals("vacation.mp4", results[0].name)
    }

    @Test
    fun testProcessSearch_VideosKeyword() = runBlocking {
        val video = createMediaItem(name = "vacation.mp4", isVideo = true)
        val image = createMediaItem(name = "vacation.jpg", isVideo = false)
        val items = listOf(video, image)
        
        val results = searchProvider.processSearch(items, "videos", SearchOptions())
        
        assertEquals(1, results.size)
        assertTrue(results[0].isVideo)
    }

    @Test
    fun testProcessSearch_ImageKeyword() = runBlocking {
        val video = createMediaItem(name = "vacation.mp4", isVideo = true)
        val image = createMediaItem(name = "vacation.jpg", isVideo = false)
        val items = listOf(video, image)
        
        val results = searchProvider.processSearch(items, "image", SearchOptions())
        
        assertEquals(1, results.size)
        assertTrue(!results[0].isVideo)
        assertEquals("vacation.jpg", results[0].name)
    }

    private fun createMediaItem(
        id: Long = 1L,
        name: String = "test.jpg",
        customTags: List<String> = emptyList(),
        isFavorite: Boolean = false,
        isVideo: Boolean = false
    ): MediaItem {
        val mockUri = mock(Uri::class.java)
        return MediaItem(
            id = id,
            uri = mockUri,
            name = name,
            dateModified = 0,
            size = 0,
            mimeType = if (isVideo) "video/mp4" else "image/jpeg",
            bucketId = "0",
            isVideo = isVideo,
            isFavorite = isFavorite,
            customTags = customTags
        )
    }
}

