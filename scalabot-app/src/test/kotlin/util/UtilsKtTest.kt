package net.lamgc.scalabot.util

import org.eclipse.aether.artifact.DefaultArtifact
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}