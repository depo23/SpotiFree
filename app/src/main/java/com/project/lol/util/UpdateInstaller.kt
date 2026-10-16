package com.project.lol.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.project.lol.R
import com.project.lol.ui.MainActivity
import java.io.File

/**
 * Installs a downloaded APK through the system installer. Android shows its own
 * confirmation screen, so the user only has to tap Install. The first time, Android also
 * asks to allow installs from Spotilol ([allowInstallsIntent]).
 */
/** What the in-app install dialog shows. Process-wide; the receiver updates it. */
sealed interface InstallState {
    data object Idle : InstallState
    data class Installing(val version: String) : InstallState
    data class Confirm(val version: String) : InstallState
    data class Installed(val version: String) : InstallState
    data class Failed(val message: String) : InstallState
}

object UpdateInstaller {

    /** Observed by the UI to show installing / confirm / installed / failed. */
    val state = mutableStateOf<InstallState>(InstallState.Idle)


    private const val ACTION_RESULT = "com.project.lol.action.UPDATE_RESULT"
    private const val PREFS_NAME = "spotilol_prefs"
    private const val KEY_JUST_UPDATED = "UpdateJustUpdated"

    /** Version carried through the installer so the result receiver can name it. */
    const val EXTRA_VERSION = "com.project.lol.extra.UPDATE_VERSION"

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Hands the downloaded APK to the system installer. Android shows its own confirmation
     * screen; the user only has to tap Install. The result arrives in [UpdateResultReceiver].
     */
    fun install(context: Context, apk: File, version: String) {
        // Remembered before committing: if the app is replaced before the result arrives,
        // the next start still knows which version was installed.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_JUST_UPDATED, version).apply()

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        // Android may otherwise approve an update silently. Require the user to confirm
        // in the system installer every time.
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        state.value = InstallState.Installing(version)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = Intent(context, UpdateResultReceiver::class.java)
                .setAction(ACTION_RESULT)
                .putExtra(EXTRA_VERSION, version)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                result,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
        Logger.i("update", "install session $sessionId committed for $version")
    }

    /**
     * Returns the version that was just installed, once, if the app is now running it.
     * Clears the marker either way.
     */
    fun consumeJustUpdated(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getString(KEY_JUST_UPDATED, null) ?: return null
        prefs.edit().remove(KEY_JUST_UPDATED).apply()
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        return pending.takeIf { it == installed }
    }
}

/** Receives the installer's status: shows the confirmation screen, or reports the outcome. */
class UpdateResultReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 4101
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                UpdateInstaller.state.value = InstallState.Confirm(intent.getStringExtra(UpdateInstaller.EXTRA_VERSION) ?: "")
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val version = intent.getStringExtra(UpdateInstaller.EXTRA_VERSION) ?: ""
                Logger.s("update", "update $version installed")
                UpdateInstaller.state.value = InstallState.Installed(version)
                notifyInstalled(context, version)
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                Logger.e("update", "install failed: $message")
                UpdateInstaller.state.value = InstallState.Failed(message)
                Toast.makeText(
                    context,
                    context.getString(R.string.update_install_failed, message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun notifyInstalled(context: Context, version: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_installed_title, version))
            .setContentText(context.getString(R.string.update_installed_text))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.update_open), open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
