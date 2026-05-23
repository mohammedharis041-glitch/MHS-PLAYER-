package com.mhs.player.di

import android.content.Context
import com.mhs.player.media.detection.MediaTypeDetector
import com.mhs.player.media.filesystem.FileResolver
import com.mhs.player.media.filesystem.FolderTreeBuilder
import com.mhs.player.media.filesystem.MediaIndexer
import com.mhs.player.media.filesystem.StorageManager
import com.mhs.player.media.folders.FolderManager
import com.mhs.player.media.scanner.MediaScanner
import com.mhs.player.media.search.SearchManager
import com.mhs.player.media.sorting.SortManager
import com.mhs.player.player.audio.AudioEffectsManager
import com.mhs.player.player.controller.FullscreenManager
import com.mhs.player.player.gestures.GestureController
import com.mhs.player.player.controller.PlaybackManager
import com.mhs.player.player.controller.PlayerController
import com.mhs.player.player.controller.PreviewFrameManager
import com.mhs.player.player.controller.QueueManager
import com.mhs.player.player.ai.translation.SubtitleTranslator
import com.mhs.player.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Most classes now use @Inject constructor and don't need explicit @Provides
}
