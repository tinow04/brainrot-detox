package dev.tinow.brainrotstopper.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.tinow.brainrotstopper.ui.apps.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launchable apps of the own profile (MAIN/LAUNCHER), visible through the `<queries>` intent entry; no
 * QUERY_ALL_PACKAGES. Work profile and private space are not covered (other users). One entry per package with the
 * application label (an app with several launcher icons appears once). Protection is applied by [AppsModel].
 */
class PackageCatalog(private val context: Context) {
    suspend fun launchable(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .map { info -> InstalledApp(info.packageName, runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName)) }
    }
}
