package dev.tinow.brainrotstopper.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tinow.brainrotstopper.data.store.SettingsRepository
import dev.tinow.brainrotstopper.service.ProtectedSetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What B1 shows; `null` sections while the catalog is loading for the first time. */
data class AppsUi(val query: String = "", val sections: AppSections? = null)

/**
 * B1 state: installed apps (reloaded on every [refresh], i.e. on resume of the main app, so new and removed apps show up),
 * the dynamic protected set, the blocklist from settings and the search text. A toggle writes `blockedPackages`; the gate
 * controller observes settings and applies it without a restart.
 */
class AppsViewModel(
    private val load: suspend () -> List<InstalledApp>,
    private val protectedSets: ProtectedSetProvider,
    private val settings: SettingsRepository,
    private val log: (String) -> Unit = {},
) : ViewModel() {
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val dynamicProtected = MutableStateFlow<Set<String>>(emptySet())
    private val query = MutableStateFlow("")
    private var job: Job? = null

    val ui: StateFlow<AppsUi> = combine(installed.filterNotNull(), dynamicProtected, settings.flow, query) { apps, prot, s, q ->
        AppsUi(q, AppsModel.sections(apps, s.blockedPackages, q, prot))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUi())

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            dynamicProtected.value = runCatching { protectedSets.load().protectedPackages }.getOrDefault(dynamicProtected.value)
            installed.value = load()
            log("catalog ${installed.value?.size ?: 0} apps")
        }
    }

    fun search(text: String) {
        query.value = text
    }

    fun toggle(pkg: String, on: Boolean) {
        viewModelScope.launch {
            val s = settings.update { it.copy(blockedPackages = if (on) it.blockedPackages + pkg else it.blockedPackages - pkg) }
            log("toggle ${if (on) "on" else "off"} blocked=${s.blockedPackages.size}")
        }
    }
}
