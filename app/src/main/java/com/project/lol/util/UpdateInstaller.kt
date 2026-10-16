package com.project.lol.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.project.lol.R
import com.project.lol.ui.MainActivity
import java.io.File

/** What the in-app install dialog shows. Process-wide; the receiver updates it. */
sealed interface InstallState {
    data object Idle : InstallState
    /** percent < 0 means finishing (indeterminate). */
    data class Installing(val version: String, val percent: Int) : InstallState
    data class Confirm(val version: String) : InstallState
    data class Installed(val version: String) : InstallState
    data class Failed(val message: String) : InstallState
}

object UpdateInstaller {

    /** Observed by the UI. Only written on the main thread. */
    val state = mutableStateOf<InstallState>(InstallState.Idle)

    /** The installer's confirmation screen, kept so the dialog can open it again. */
    var pendingConfirm: Intent? = null

    private const val ACTION_RESULT = "com.project.lol.action.UPDATE_RESULT"
    private const val PREFS_NAME = "spotilol_prefs"
    private const val KEY_JUST_UPDATED = "UpdateJustUpdated"
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFY_ID = 4101
    private val main = Handler(Looper.getMainLooper())

    /** Version carried through the installer so the result receiver can name it. */
    const val EXTRA_VERSION = "com.project.lol.extra.UPDATE_VERSION"

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Post a state change to the main thread (install runs on a worker thread). */
    fun setState(value: InstallState) {
        main.post { state.value = value }
    }

    /**
     * Copies the APK into a session and commits it. Runs on a worker thread. The system
     * installer should then show its confirmation screen (USER_ACTION_REQUIRED).
     */
    fun install(context: Context, apk: File, version: String) {
        // Remembered before committing: if the app is replaced before the result arrives,
        // the next start still knows which version was installed.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_JUST_UPDATED, version).apply()

        setState(InstallState.Installing(version, 0))
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                // Never approve silently: the user confirms in the system installer.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                val total = apk.length().coerceAtLeast(1L)
                var done = 0L
                var last = -1
                apk.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        val percent = (done * 100 / total).toInt()
                        if (percent != last) {
                            last = percent
                            setState(InstallState.Installing(version, percent))
                        }
                    }
                }
                session.fsync(out)
            }
            setState(InstallState.Installing(version, -1))
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

    /** Opens the system installer confirmation again, from the app or a notification. */
    fun openConfirm(context: Context): Boolean {
        val confirm = pendingConfirm ?: return false
        return try {
            context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Logger.w("update", "could not open installer", e)
            false
        }
    }

    /**
     * Returns the version that was just installed, once, if the app is now running it.
     * Clears the marker either way.
     */
    fun consumeJustUpdated(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getString(KEY_JUST_UPDATED, null) ?: return null
        prefs.edit().remove(KEY_JUST_UPDATED).apply()
        return pending.takeIf { it == installedVersion(context) }
    }

    fun installedVersion(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    /** Notification that the update finished; tapping it opens the app. */
    fun notifyUpdated(context: Context) {
        val version = installedVersion(context) ?: return
        notify(
            context,
            title = context.getString(R.string.update_installed_title, version),
            text = context.getString(R.string.update_installed_text),
            intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            requestCode = 1
        )
    }

    /** Notification that the installer needs a tap, used when it can't open by itself. */
    fun notifyConfirm(context: Context, version: String, confirm: Intent) {
        notify(
            context,
            title = context.getString(R.string.update_confirm_title, version),
            text = context.getString(R.string.update_confirm_notification),
            intent = Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            requestCode = 2
        )
    }

    private fun notify(context: Context, title: String, text: String, intent: Intent, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_HIGH)
        )
        val tap = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .addAction(0, context.getString(R.string.update_open), tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(NOTIFY_ID, notification)
    }
}

/** Receives the installer's status: shows the confirmation screen, or reports the outcome. */
class UpdateResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val version = intent.getStringExtra(UpdateInstaller.EXTRA_VERSION) ?: ""
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                UpdateInstaller.pendingConfirm = confirm
                UpdateInstaller.setState(InstallState.Confirm(version))
                Logger.i("update", "installer needs confirmation for $version")
                val opened = try {
                    confirm?.let { context.startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    confirm != null
                } catch (e: Exception) {
                    Logger.w("update", "installer could not open from background", e)
                    false
                }
                if (!opened && confirm != null) UpdateInstaller.notifyConfirm(context, version, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val version = intent.getStringExtra(UpdateInstaller.EXTRA_VERSION) ?: ""
                Logger.s("update", "update $version installed")
                UpdateInstaller.setState(InstallState.Installed(version))
                UpdateInstaller.notifyUpdated(context)
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                Logger.e("update", "install failed: $message")
                UpdateInstaller.setState(InstallState.Failed(message))
                Toast.makeText(
                    context,
                    context.getString(R.string.update_install_failed, message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

/** The app was replaced by an update: tell the user it finished and offer to open it. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            UpdateInstaller.notifyUpdated(context)
        }
    }
}
