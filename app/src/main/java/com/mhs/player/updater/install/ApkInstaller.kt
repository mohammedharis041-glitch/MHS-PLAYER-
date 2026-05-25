package com.mhs.player.updater.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageInstaller
import android.widget.Toast
import java.io.File
import java.util.zip.ZipInputStream

object ApkInstaller {

    fun getInstallIntent(context: Context, file: File): Intent {
        val apkUri = FileProviderUtils.getUriForFile(context, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "Installer file not found", Toast.LENGTH_SHORT).show()
            return
        }

        // For Android 8.0+, check and request permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    context, 
                    "Please allow MHS Player to install updates", 
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        try {
            val intent = getInstallIntent(context, file)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error launching installer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun installSplitApk(context: Context, zipFile: File) {
        if (!zipFile.exists()) {
            Toast.makeText(context, "Split APK archive not found", Toast.LENGTH_SHORT).show()
            return
        }

        // For Android 8.0+, check and request permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    context, 
                    "Please allow MHS Player to install updates", 
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val tempDir = File(context.cacheDir, "split_apk_temp")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        tempDir.mkdirs()

        try {
            // Unzip the APK files
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        val outFile = File(tempDir, entry.name.substringAfterLast("/"))
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { output ->
                            zip.copyTo(output)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val apkFiles = tempDir.listFiles { _, name -> name.endsWith(".apk", ignoreCase = true) }?.toList()
            if (apkFiles.isNullOrEmpty()) {
                Toast.makeText(context, "No APK files found inside the archive", Toast.LENGTH_SHORT).show()
                return
            }

            // Begin session-based package installation
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            try {
                for (file in apkFiles) {
                    val size = file.length()
                    file.inputStream().use { inputStream ->
                        session.openWrite(file.name, 0, size).use { output ->
                            val buffer = ByteArray(65536)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                            session.fsync(output)
                        }
                    }
                }

                val intent = Intent(context, SplitInstallReceiver::class.java).apply {
                    action = "com.mhs.player.updater.INSTALL_STATUS"
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                )

                session.commit(pendingIntent.intentSender)
            } finally {
                session.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Split APK installation failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            // Clean up extraction files
            try {
                tempDir.deleteRecursively()
            } catch (ignored: Exception) {}
        }
    }
}
