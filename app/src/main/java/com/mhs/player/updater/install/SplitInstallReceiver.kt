package com.mhs.player.updater.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast

class SplitInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.d("MHSPlayer-SplitInstall", "Status received: status=$status, message=$message")
        
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Tiramisu (Android 13) and above require passing Class to getParcelableExtra
                val confirmationIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }
                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Update installed successfully!", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(context, "Installation failed: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
