package dev.tinow.brainrotstopper.ui.apps

import dev.tinow.brainrotstopper.domain.gate.ProtectedPackages
import java.text.Collator
import java.util.Locale

/** A launchable app of the own profile (package name and its label as the launcher shows it). */
data class InstalledApp(val pkg: String, val label: String)

/** The two sections of B1 after protection, sorting and search. */
data class AppSections(val blocked: List<AppItem>, val others: List<AppItem>)

/**
 * B1 rules (architecture.md 8), android-free:
 * - protected packages (static and dynamic: launchers, input methods, dialer) are never shown, so they cannot be picked;
 * - blocked packages that are no longer installed stay in the blocked section with the package name as label (unlockable);
 * - blocked apps are not repeated under "alle apps";
 * - both sections sorted by label (German collator), the search filters both by a case-insensitive substring of the label.
 */
object AppsModel {
    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }

    fun sections(
        installed: List<InstalledApp>,
        blockedPackages: Set<String>,
        query: String,
        dynamicProtected: Set<String> = emptySet(),
    ): AppSections {
        val byPkg = installed.filterNot { ProtectedPackages.isProtected(it.pkg, dynamicProtected) }.associateBy { it.pkg }
        val blocked = blockedPackages
            .filterNot { ProtectedPackages.isProtected(it, dynamicProtected) }
            .map { pkg -> AppItem(pkg, byPkg[pkg]?.label ?: pkg, blocked = true) }
        val others = byPkg.values.filter { it.pkg !in blockedPackages }.map { AppItem(it.pkg, it.label, blocked = false) }
        val needle = query.trim().lowercase(Locale.GERMAN)
        fun List<AppItem>.shown() = filter { needle.isEmpty() || needle in it.label.lowercase(Locale.GERMAN) }
            .sortedWith { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.pkg.compareTo(b.pkg) }
        return AppSections(blocked.shown(), others.shown())
    }
}
