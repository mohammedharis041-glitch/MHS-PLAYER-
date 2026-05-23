package com.mhs.player.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet with Delete / Copy / Move / Share / Open Folder actions for a media file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOperationsSheet(
    item: MediaItemModel,
    onDismiss: () -> Unit,
    onActionDone: (message: String) -> Unit,
    onOpenFolder: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isCopying by remember { mutableStateOf(false) }
    var isMovePending by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Android 11+: request delete via MediaStore (scoped storage)
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onActionDone("Deleted \"${item.displayName}\"")
            onDismiss()
        }
    }

    // SAF launcher for Copy — user picks a location + name for the new file
    val copyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(item.mimeType.ifBlank { "*/*" })
    ) { destUri ->
        if (destUri != null) {
            isCopying = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(item.uri)?.use { input ->
                            context.contentResolver.openOutputStream(destUri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        true
                    }.getOrDefault(false)
                }
                isCopying = false
                onActionDone(if (success) "Copied \"${item.displayName}\"" else "Copy failed")
                onDismiss()
            }
        }
    }

    // SAF launcher for Move — copy then delete original
    val moveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(item.mimeType.ifBlank { "*/*" })
    ) { destUri ->
        if (destUri != null) {
            isMovePending = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(item.uri)?.use { input ->
                            context.contentResolver.openOutputStream(destUri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        // Delete original after successful copy
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11+ delete handled via MediaStore on main thread below
                        } else {
                            context.contentResolver.delete(item.uri, null, null)
                        }
                        true
                    }.getOrDefault(false)
                }
                isMovePending = false
                if (success) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Request permission to delete on Android 11+
                        val intentSender = MediaStore.createDeleteRequest(
                            context.contentResolver, listOf(item.uri)
                        ).intentSender
                        deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    } else {
                        onActionDone("Moved \"${item.displayName}\"")
                        onDismiss()
                    }
                } else {
                    onActionDone("Move failed")
                    onDismiss()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                maxLines = 1
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            // Share
            FileActionRow(icon = Icons.Default.Share, label = "Share") {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, item.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                onDismiss()
            }

            // Copy file
            FileActionRow(icon = Icons.Default.FileCopy, label = "Copy to…") {
                copyLauncher.launch(item.displayName)
            }

            // Move file
            FileActionRow(icon = Icons.Default.DriveFileMove, label = "Move to…") {
                moveLauncher.launch(item.displayName)
            }


            // Copy path to clipboard
            FileActionRow(icon = Icons.Default.ContentCopy, label = "Copy path") {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("Path", item.path)
                )
                onActionDone("Path copied to clipboard")
                onDismiss()
            }

            // Open folder internally
            if (onOpenFolder != null) {
                FileActionRow(icon = Icons.Default.FolderOpen, label = "Open folder") {
                    onOpenFolder(item.folderPath)
                    onDismiss()
                }
            }

            // Delete
            FileActionRow(
                icon = Icons.Default.Delete,
                label = "Delete",
                tint = MaterialTheme.colorScheme.error
            ) {
                showDeleteConfirm = true
            }

            // Busy indicators
            if (isCopying || isMovePending) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        if (isCopying) "Copying…" else "Moving…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Delete file?", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "\"${item.displayName}\" will be permanently deleted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intentSender = MediaStore.createDeleteRequest(
                                context.contentResolver,
                                listOf(item.uri)
                            ).intentSender
                            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        } else {
                            val deleted = try {
                                context.contentResolver.delete(item.uri, null, null) > 0
                            } catch (e: Exception) { false }
                            if (deleted) {
                                onActionDone("Deleted \"${item.displayName}\"")
                                onDismiss()
                            } else {
                                onActionDone("Could not delete file")
                                onDismiss()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Primary)
                }
            }
        )
    }
}

@Composable
private fun FileActionRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/**
 * Opens the folder containing the media file in an appropriate file manager.
 *
 * Strategy (most specific → most generic):
 * 1. Build the correct DocumentsUI tree URI for this folder (internal or SD card).
 * 2. Try DocumentsUI (Files by Google / AOSP Files) via ACTION_VIEW with tree URI.
 * 3. Try a file:// URI intent (works on many older file managers).
 * 4. Try launching any app with CATEGORY_APP_FILES (system Files app).
 */
private fun openFolderInFileManager(context: android.content.Context, folderPath: String) {
    // ── Step 1: work out the storage root and relative segment ──────────
    // Internal storage: /storage/emulated/0  → authority "primary"
    // SD card:          /storage/XXXX-XXXX   → authority "XXXX-XXXX"
    val internalRoot = "/storage/emulated/0"
    val (storageId, relativePath) = when {
        folderPath.startsWith(internalRoot) -> {
            "primary" to folderPath.removePrefix(internalRoot).trimStart('/')
        }
        folderPath.startsWith("/storage/") -> {
            // SD card: /storage/XXXX-XXXX/some/path
            val withoutStorage = folderPath.removePrefix("/storage/")
            val slash = withoutStorage.indexOf('/')
            if (slash != -1) {
                withoutStorage.substring(0, slash) to withoutStorage.substring(slash + 1)
            } else {
                withoutStorage to ""
            }
        }
        folderPath.startsWith("/sdcard/") -> {
            "primary" to folderPath.removePrefix("/sdcard/").trimStart('/')
        }
        else -> {
            // Unknown prefix — encode as-is under primary
            "primary" to folderPath.trimStart('/')
        }
    }

    // ── Step 2: DocumentsUI tree URI ─────────────────────────────────────
    // Tree URI = content://com.android.externalstorage.documents/tree/<storageId>:<relativePath>
    // Document URI = tree + /document/<storageId>:<relativePath>
    val encodedDoc = Uri.encode("$storageId:$relativePath")
    val treeUri = Uri.parse(
        "content://com.android.externalstorage.documents/tree/$encodedDoc/document/$encodedDoc"
    )

    var opened = false

    // Try DocumentsUI (Files by Google, AOSP Files app, OEM Files apps)
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(treeUri, "vnd.android.document/directory")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        context.startActivity(intent)
        opened = true
    }

    // ── Step 3: file:// intent (many file managers understand this) ───────
    if (!opened) {
        runCatching {
            val fileUri = Uri.fromFile(java.io.File(folderPath))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Open folder with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            opened = true
        }
    }

    // ── Step 4: generic "files" app ───────────────────────────────────────
    if (!opened) {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_FILES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
