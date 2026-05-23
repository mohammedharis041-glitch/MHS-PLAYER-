package com.mhs.player.player.subtitles.di

import com.mhs.player.player.subtitles.api.MsoneApiService
import com.mhs.player.player.subtitles.api.OpenSubtitlesApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubtitleModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE // Set to BODY for debugging
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideMsoneApiService(okHttpClient: OkHttpClient): MsoneApiService {
        return Retrofit.Builder()
            .baseUrl(MsoneApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MsoneApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenSubtitlesApiService(okHttpClient: OkHttpClient): OpenSubtitlesApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.opensubtitles.com/api/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenSubtitlesApiService::class.java)
    }
}
