package dev.tinow.brainrotstopper.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.data.system.PackageCatalog
import dev.tinow.brainrotstopper.domain.gate.ProtectedPackages
import dev.tinow.brainrotstopper.service.AndroidProtectedSetProvider
import dev.tinow.brainrotstopper.ui.apps.AppsModel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Against the platform: the `<queries>` MAIN/LAUNCHER entry is enough to see launchable apps (no QUERY_ALL_PACKAGES).
 * Needs the dummy test app (`:testapp:installOneDebug`) and AnkiDroid installed, as for the other emulator runs.
 */
@RunWith(AndroidJUnit4::class)
class PackageCatalogTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun dummyIsListedOwnPackageAndAnkiDroidAreNot() = runBlocking {
        val installed = PackageCatalog(context).launchable()
        val pkgs = installed.map { it.pkg }
        println("BSTest catalog ${installed.size} apps")
        // raw catalog: launchable apps incl. AnkiDroid (visible through <queries>), one entry per package
        assertThat(pkgs).contains(DUMMY)
        assertThat(pkgs).contains(ProtectedPackages.ANKIDROID)
        assertThat(pkgs).containsNoDuplicates()
        assertThat(installed.first { it.pkg == DUMMY }.label).isNotEmpty()

        val dynamic = AndroidProtectedSetProvider(context).load().protectedPackages
        val sections = AppsModel.sections(installed, emptySet(), "", dynamic)
        val shown = sections.others.map { it.pkg }
        assertThat(shown).contains(DUMMY)
        assertThat(shown).containsNoneOf(ProtectedPackages.OWN_PACKAGE, ProtectedPackages.ANKIDROID, "com.android.settings")
        assertThat(shown).containsNoneIn(dynamic)
    }

    private companion object {
        const val DUMMY = "dev.tinow.brainrotstopper.dummy"
    }
}
