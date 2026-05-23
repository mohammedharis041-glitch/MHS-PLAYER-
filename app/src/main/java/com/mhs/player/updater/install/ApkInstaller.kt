package com.mhs.player.updater.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.io.File

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
}
