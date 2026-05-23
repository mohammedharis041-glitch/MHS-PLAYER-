package com.mhs.player

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.mhs.player.navigation.AppNavigation
import com.mhs.player.settings.SettingsRepository
import com.mhs.player.ui.theme.MHSPlayerTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhs.player.updater.UpdateViewModel
import com.mhs.player.updater.ui.UpdateDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mhs.player.player.controller.PlayerController
import com.mhs.player.player.service.MhsPlaybackService
import android.content.Intent
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playerController: PlayerController
    @Inject lateinit var gestureController: com.mhs.player.player.gestures.GestureController

    private var externalUri = mutableStateOf<String?>(null)
    private var isDarkMode = mutableStateOf(true)
    private var showExitDialog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        // Load dark mode preference
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            isDarkMode.value = settings.darkMode
            // Keep listening for changes
            settingsRepository.settings.collect { isDarkMode.value = it.darkMode }
        }

        setContent {
            val darkMode by isDarkMode
            MHSPlayerTheme(darkTheme = darkMode) {
                val bgColor = if (darkMode) androidx.compose.ui.graphics.Color.Black
                              else androidx.compose.ui.graphics.Color(0xFFF6F5FA)
                Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
                    val updateViewModel: UpdateViewModel = hiltViewModel()
                    val updateResult by updateViewModel.updateResult.collectAsStateWithLifecycle()
                    val downloadState by updateViewModel.downloadState.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        updateViewModel.checkForUpdates(isManual = false)
                    }

                    AppNavigation(
                        externalUri = externalUri.value,
                        onExternalUriConsumed = {
                            android.util.Log.d("MHSPlayer-Intent", "externalUri state consumed and reset.")
                            externalUri.value = null
                        },
                        onRequestExit = { showExitDialog.value = true }
                    )

                    updateResult?.let { result ->
                        UpdateDialog(
                            updateResult = result,
                            downloadState = downloadState,
                            onUpdateClick = { updateViewModel.startDownload() },
                            onLaterClick = { updateViewModel.dismissDialog() },
                            onSkipClick = { updateViewModel.skipVersion() },
                            onInstallClick = { filePath -> updateViewModel.installApk(filePath) },
                            onCancelClick = { updateViewModel.cancelDownload() }
                        )
                    }

                    // Exit confirmation dialog
                    if (showExitDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showExitDialog.value = false },
                            title = { Text(stringResource(R.string.exit_app_title)) },
                            text = { Text(stringResource(R.string.exit_app_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    playerController.stop()
                                    stopService(Intent(this@MainActivity, MhsPlaybackService::class.java))
                                    finish()
                                }) {
                                    Text(stringResource(R.string.exit))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExitDialog.value = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type
        android.util.Log.d("MHSPlayer-Intent", "handleIncomingIntent: action=$action, type=$type")

        var uri: android.net.Uri? = null
        if (android.content.Intent.ACTION_VIEW == action) {
            uri = intent.data
            android.util.Log.d("MHSPlayer-Intent", "ACTION_VIEW incoming URI: $uri")
        } else if (android.content.Intent.ACTION_SEND == action && type != null) {
            val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM) as? android.net.Uri
            }
            if (streamUri != null) {
                uri = streamUri
                android.util.Log.d("MHSPlayer-Intent", "ACTION_SEND incoming stream URI: $uri")
            } else {
                val clipData = intent.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    uri = clipData.getItemAt(0).uri
                    android.util.Log.d("MHSPlayer-Intent", "ACTION_SEND incoming clipData URI: $uri")
                }
            }
        }

        if (uri != null) {
            // Verify permission state and acquire persistable permission if content URI
            if (uri.scheme == android.content.ContentResolver.SCHEME_CONTENT) {
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flags)
                    android.util.Log.d("MHSPlayer-Intent", "Successfully taken persistable read URI permission for: $uri")
                } catch (e: Exception) {
                    android.util.Log.w("MHSPlayer-Intent", "Could not take persistable read URI permission: ${e.message}")
                }

                // Verify access by querying the content resolver metadata
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            val displayName = if (nameIndex != -1) cursor.getString(nameIndex) else "unknown"
                            val sizeBytes = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                            android.util.Log.d("MHSPlayer-Intent", "Content validated. Name: $displayName, Size: $sizeBytes bytes")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MHSPlayer-Intent", "Failed to access/query content URI metadata: ${e.message}", e)
                }
            } else if (uri.scheme == android.content.ContentResolver.SCHEME_FILE) {
                val file = java.io.File(uri.path ?: "")
                android.util.Log.d("MHSPlayer-Intent", "File scheme validated. Path: ${file.absolutePath}, exists: ${file.exists()}, readable: ${file.canRead()}")
            }

            externalUri.value = uri.toString()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    private fun enableHighRefreshRate() {
        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = display ?: return
            val supportedModes = display.supportedModes
            if (supportedModes.size <= 1) return

            val currentMode = display.mode
            val bestMode = supportedModes
                .filter { mode ->
                    mode.physicalWidth == currentMode.physicalWidth &&
                    mode.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: supportedModes.maxByOrNull { it.refreshRate }

            if (bestMode != null && bestMode.modeId != currentMode.modeId) {
                window.attributes = window.attributes.also { lp ->
                    lp.preferredDisplayModeId = bestMode.modeId
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            if (gestureController.isPlayerActive()) {
                gestureController.triggerVolumeKey(isUp = true)
                return true
            }
        } else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (gestureController.isPlayerActive()) {
                gestureController.triggerVolumeKey(isUp = false)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (gestureController.isPlayerActive()) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}
