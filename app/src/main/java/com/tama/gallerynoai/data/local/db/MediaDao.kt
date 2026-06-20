package com.tama.gallerynoai.data.local.db

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE bucketId NOT IN (:hiddenIds) ORDER BY dateModified DESC")
    fun getAllPagedDateNewest(hiddenIds: List<String>): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media_items WHERE bucketId NOT IN (:hiddenIds) ORDER BY dateModified ASC")
    fun getAllPagedDateOldest(hiddenIds: List<String>): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media_items WHERE bucketId NOT IN (:hiddenIds) ORDER BY size DESC")
    fun getAllPagedSizeLargest(hiddenIds: List<String>): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media_items WHERE bucketId NOT IN (:hiddenIds) ORDER BY size ASC")
    fun getAllPagedSizeSmallest(hiddenIds: List<String>): PagingSource<Int, MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaEntity>)

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Update
    suspend fun update(item: MediaEntity)

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT COUNT(*) FROM media_items")
    fun getTotalMediaCount(): Flow<Int>

    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaEntity>

    @Query("SELECT * FROM media_items ORDER BY dateModified DESC")
    fun getAllFlow(): Flow<List<MediaEntity>>

    @Query("SELECT id, name, dateModified, bucketId, bucketName FROM media_items")
    suspend fun getAllIdsAndDates(): List<MediaIdAndDate>

    @Query("SELECT customTags FROM media_items")
    suspend fun getAllCustomTags(): List<String>

    @Query("""
        SELECT 
            bucketId, 
            MAX(bucketName) as bucketName, 
            MAX(uri) as coverUri, 
            COUNT(*) as itemCount, 
            SUM(size) as totalSize, 
            MAX(relativePath) as relativePath 
        FROM media_items 
        GROUP BY bucketId
    """)
    fun getAlbumStats(): Flow<List<AlbumStatEntity>>

    @Query("""
        SELECT media_items.* FROM media_items 
        JOIN media_items_fts ON media_items.id = media_items_fts.rowid 
        WHERE media_items_fts MATCH :query AND media_items.bucketId NOT IN (:hiddenIds)
        ORDER BY dateModified DESC
    """)
    fun searchPaged(query: String, hiddenIds: List<String>): PagingSource<Int, MediaEntity>
}