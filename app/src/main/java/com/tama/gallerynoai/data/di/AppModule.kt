package com.tama.gallerynoai.data.di

import android.content.Context
import com.tama.gallerynoai.data.repository.MediaSearchProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMediaSearchProvider(): MediaSearchProvider {
        return MediaSearchProvider()
    }
}
