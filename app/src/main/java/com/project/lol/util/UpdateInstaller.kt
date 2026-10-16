package com.project.lol.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Installs a downloaded APK by handing it to Android's own installer, the same way a file
 * manager would. The installer shows the confirmation, the progress, and an Open button
 * when it finishes. The first time, Android also asks to allow installs from Spotilol
 * ([allowInstallsIntent]).
 */
object UpdateInstaller {

    private const val PREFS_NAME = "spotilol_prefs"
    private const val KEY_JUST_UPDATED = "UpdateJustUpdated"

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Opens the system installer for [apk]. Throws if no installer can handle it. */
    fun install(context: Context, apk: File, version: String) {
        // Remembered so the next start can confirm which version was installed.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_JUST_UPDATED, version).apply()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        Logger.i("update", "system installer opened for $version")
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
