package net.lamgc.scalabot.util

import com.google.gson.*
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.lang.reflect.Type

object ProxyTypeSerializer : JsonDeserializer<DefaultBotOptions.ProxyType>,
    JsonSerializer<DefaultBotOptions.ProxyType> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): DefaultBotOptions.ProxyType {
        if (!json.isJsonPrimitive) {
            throw JsonParseException("Wrong configuration value type.")
        }
        val value = json.asString.trim()
        try {
            return DefaultBotOptions.ProxyType.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("Invalid value: $value")
        }
    }

    override fun serialize(
        src: DefaultBotOptions.ProxyType,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src.toString())
    }
}

object ArtifactSerializer : JsonSerializer<Artifact>, JsonDeserializer<Artifact> {
    override fun serialize(src: Artifact, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val gavBuilder = StringBuilder("${src.groupId}:${src.artifactId}")
        if (!src.extension.equals("jar")) {
            gavBuilder.append(':').append(src.extension)
        }
        if (src.classifier.isNotEmpty()) {
            gavBuilder.append(':').append(src.classifier)
        }
        return JsonPrimitive(gavBuilder.append(':').append(src.version).toString())
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Artifact {
        if (!json!!.isJsonPrimitive) {
            throw JsonParseException("Wrong configuration value type.")
        }
        return DefaultArtifact(json.asString.trim())
    }

}

