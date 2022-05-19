@file:Suppress("PackageDirectoryMismatch")

package net.lamgc.scalabot.util

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import org.eclipse.aether.artifact.DefaultArtifact
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class ArtifactSerializerTest {

    @Test
    fun badJsonType() {
        assertFailsWith<JsonParseException> { ArtifactSerializer.deserialize(JsonObject(), null, null) }
    }

    @Test
    fun `Basic format serialization`() {
        val gav = "org.example.software:test:1.0.0-SNAPSHOT"
        val expectArtifact = DefaultArtifact(gav)
        val actualArtifact = DefaultArtifact(ArtifactSerializer.serialize(expectArtifact, null, null).asString)
        assertEquals(expectArtifact, actualArtifact)
    }

    @Test
    fun `Full format serialization`() {
        val gav = "org.example.software:test:war:javadoc:1.0.0-SNAPSHOT"
        val expectArtifact = DefaultArtifact(gav)
        val actualArtifact = DefaultArtifact(ArtifactSerializer.serialize(expectArtifact, null, null).asString)
        assertEquals(expectArtifact, actualArtifact)
    }

    @Test
    fun deserialize() {
        val gav = "org.example.software:test:1.0.0-SNAPSHOT"
        val expectArtifact = DefaultArtifact(gav)
        val actualArtifact = ArtifactSerializer.deserialize(JsonPrimitive(gav), null, null)
        assertEquals(expectArtifact, actualArtifact)
    }
}