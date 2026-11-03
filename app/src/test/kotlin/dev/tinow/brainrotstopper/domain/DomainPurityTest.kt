package dev.tinow.brainrotstopper.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** The domain package must stay free of the Android framework so it runs as plain JVM unit tests. */
class DomainPurityTest {
    @Test fun noAndroidImportsInDomain() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "src/main/kotlin/dev/tinow/brainrotstopper/domain") }
            .firstOrNull { it.isDirectory } ?: error("domain source dir not found from ${File("").absolutePath}")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> f.readLines().withIndex().filter { (_, l) -> Regex("""^\s*import\s+(android|androidx)\.""").containsMatchIn(l) }.map { "${f.name}:${it.index + 1}" } }
            .toList()
        assertThat(offenders).isEmpty()
    }
}
