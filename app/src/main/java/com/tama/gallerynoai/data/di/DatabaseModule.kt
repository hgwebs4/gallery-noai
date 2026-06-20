package com.tama.gallerynoai.data.di

import android.content.Context
import com.tama.gallerynoai.data.local.db.MediaDao
import com.tama.gallerynoai.data.local.db.MediaDatabase
import com.tama.gallerynoai.data.local.db.SearchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMediaDatabase(@ApplicationContext context: Context): MediaDatabase {
        return MediaDatabase.getDatabase(context)
    }

    @Provides
    fun provideMediaDao(db: MediaDatabase): MediaDao {
        return db.mediaDao()
    }

    @Provides
    fun provideSearchHistoryDao(db: MediaDatabase): SearchHistoryDao {
        return db.searchHistoryDao()
    }
}
