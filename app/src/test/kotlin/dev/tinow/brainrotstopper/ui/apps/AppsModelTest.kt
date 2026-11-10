package dev.tinow.brainrotstopper.ui.apps

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.gate.ProtectedPackages
import org.junit.Test

class AppsModelTest {
    private val installed = listOf(
        InstalledApp("com.zhiliaoapp.musically", "TikTok"),
        InstalledApp("com.instagram.android", "Instagram"),
        InstalledApp("com.android.chrome", "Chrome"),
        InstalledApp("org.example.oe", "Ökotest"),
        InstalledApp("org.example.ob", "Obst"),
        InstalledApp("com.google.android.youtube", "YouTube"),
        InstalledApp(ProtectedPackages.OWN_PACKAGE, "Brainrot Stopper"),
        InstalledApp(ProtectedPackages.ANKIDROID, "AnkiDroid"),
        InstalledApp("com.android.settings", "Einstellungen"),
        InstalledApp("dev.tinow.brainrotstopper.dummy", "dummy"),
        InstalledApp("com.google.android.apps.nexuslauncher", "Pixel Launcher"),
    )

    private fun pkgs(items: List<AppItem>) = items.map { it.pkg }

    @Test fun `protected packages are never listed, dummy apps are`() {
        val s = AppsModel.sections(installed, emptySet(), "", dynamicProtected = setOf("com.google.android.apps.nexuslauncher"))
        assertThat(pkgs(s.others)).containsNoneOf(
            ProtectedPackages.OWN_PACKAGE, ProtectedPackages.ANKIDROID, "com.android.settings", "com.google.android.apps.nexuslauncher",
        )
        assertThat(pkgs(s.others)).contains("dev.tinow.brainrotstopper.dummy")
        assertThat(s.blocked).isEmpty()
    }

    @Test fun `blocked apps only in the blocked section, both sorted by german collator`() {
        val s = AppsModel.sections(installed, setOf("com.zhiliaoapp.musically", "com.instagram.android"), "")
        assertThat(s.blocked.map { it.label }).containsExactly("Instagram", "TikTok").inOrder()
        assertThat(s.blocked.all { it.blocked }).isTrue()
        // case-insensitive, umlaut sorted next to its base letter (Obst < Ökotest), not after z
        assertThat(s.others.map { it.label }).containsExactly("Chrome", "dummy", "Obst", "Ökotest", "Pixel Launcher", "YouTube").inOrder()
        assertThat(s.others.none { it.blocked }).isTrue()
    }

    @Test fun `blocked but uninstalled package stays visible with its package name`() {
        val s = AppsModel.sections(installed, setOf("com.reddit.frontpage", "com.instagram.android"), "")
        assertThat(s.blocked.map { it.label }).containsExactly("com.reddit.frontpage", "Instagram").inOrder()
    }

    @Test fun `protected package in the blocklist is not shown`() {
        val s = AppsModel.sections(installed, setOf(ProtectedPackages.ANKIDROID, "com.instagram.android"), "")
        assertThat(pkgs(s.blocked)).containsExactly("com.instagram.android")
    }

    @Test fun `search filters both sections by a case-insensitive substring of the label`() {
        val s = AppsModel.sections(installed, setOf("com.google.android.youtube"), "  TUB ")
        assertThat(pkgs(s.blocked)).containsExactly("com.google.android.youtube")
        assertThat(s.others).isEmpty()
        val t = AppsModel.sections(installed, setOf("com.google.android.youtube"), "ö")
        assertThat(t.blocked).isEmpty()
        assertThat(t.others.map { it.label }).containsExactly("Ökotest")
    }

    @Test fun `nothing installed and nothing blocked`() {
        val s = AppsModel.sections(emptyList(), emptySet(), "")
        assertThat(s).isEqualTo(AppSections(emptyList(), emptyList()))
    }
}
