package dev.tinow.brainrotstopper.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collects what must never be locked besides the static list (architecture.md 7): every home launcher (the default one
 * can change), the default dialer, and the enabled input methods. The input methods are also returned separately because
 * their windows are ignored by the foreground tracker. Needs the HOME `<queries>` entry in the manifest.
 */
class AndroidProtectedSetProvider(private val context: Context) : ProtectedSetProvider {
    override suspend fun load(): ProtectedSets = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
        val dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        val imes = runCatching {
            context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList.orEmpty().map { it.packageName }
        }.getOrDefault(emptyList())
        ProtectedSets(
            protectedPackages = (home + imes + listOfNotNull(dialer)).toSet(),
            inputMethods = imes.toSet(),
        )
    }
}
