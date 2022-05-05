package net.lamgc.scalabot.util

import net.lamgc.scalabot.ExtensionPackageFinder
import net.lamgc.scalabot.FinderPriority
import net.lamgc.scalabot.FinderRules
import net.lamgc.scalabot.FoundExtensionPackage
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class UtilsKtTest {

    @Test
    fun `Extension Function - Artifact_equalsArtifact`() {
        val equalGAV = "org.example:demo:1.0.0-SNAPSHOT"
        assertTrue(DefaultArtifact(equalGAV).equalsArtifact(DefaultArtifact(equalGAV)))
        assertFalse(
            DefaultArtifact("org.example:demo:1.0.0")
                .equalsArtifact(DefaultArtifact("com.example:demo-2:1.0.0-SNAPSHOT"))
        )
    }

    @Test
    fun `bytes to hex`() {
        assertEquals("48656c6c6f20576f726c64", "Hello World".toByteArray(StandardCharsets.UTF_8).toHexString())
    }

    @Test
    fun `ExtensionPackageFinder - getPriority`() {
        open class BaseTestFinder : ExtensionPackageFinder {
            override fun findByArtifact(extensionArtifact: Artifact, extensionsPath: File): Set<FoundExtensionPackage> {
                throw IllegalStateException("Calling this class is not allowed.")
            }
        }

        @FinderRules(FinderPriority.ALTERNATE)
        class StandardTestFinder : BaseTestFinder()
        assertEquals(
            FinderPriority.ALTERNATE, StandardTestFinder().getPriority(),
            "获取到的优先值与预期不符"
        )

        @FinderRules(-1)
        class OutOfRangePriorityFinder : BaseTestFinder()
        assertThrows<IllegalArgumentException>("getPriority 方法没有对超出范围的优先值抛出异常.") {
            OutOfRangePriorityFinder().getPriority()
        }

        class NoAnnotationFinder : BaseTestFinder()
        assertThrows<NoSuchFieldException> {
            NoAnnotationFinder().getPriority()
        }

    }
}