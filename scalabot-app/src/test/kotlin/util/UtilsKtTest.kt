package net.lamgc.scalabot.util

import org.eclipse.aether.artifact.DefaultArtifact
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

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

}