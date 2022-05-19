package net.lamgc.scalabot.util

import com.google.gson.*
import net.lamgc.scalabot.MavenRepositoryConfig
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.util.repository.AuthenticationBuilder
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.lang.reflect.Type
import java.net.URL

internal object ProxyTypeSerializer : JsonDeserializer<DefaultBotOptions.ProxyType>,
    JsonSerializer<DefaultBotOptions.ProxyType> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): DefaultBotOptions.ProxyType {
        if (json.isJsonNull) {
            return DefaultBotOptions.ProxyType.NO_PROXY
        }
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

internal object ArtifactSerializer : JsonSerializer<Artifact>, JsonDeserializer<Artifact> {
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

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Artifact {
        if (!json.isJsonPrimitive) {
            throw JsonParseException("Wrong configuration value type.")
        }
        return DefaultArtifact(json.asString.trim())
    }

}

internal object AuthenticationSerializer : JsonDeserializer<Authentication> {

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Authentication {
        if (json !is JsonObject) {
            throw JsonParseException("Unsupported JSON type.")
        }
        val username = SerializerUtils.checkJsonKey(json, "username")
        val password = SerializerUtils.checkJsonKey(json, "password")
        val builder = AuthenticationBuilder()
        builder.addUsername(username)
        builder.addPassword(password)
        return builder.build()
    }

}

private object SerializerUtils {
    fun checkJsonKey(json: JsonObject, key: String): String {
        if (!json.has(key)) {
            throw JsonParseException("Required field does not exist: $key")
        } else if (!json.get(key).isJsonPrimitive) {
            throw JsonParseException("Wrong field `$key` type: ${json.get(key)::class.java}")
        }
        return json.get(key).asString
    }
}

internal object MavenRepositoryConfigSerializer
    : JsonDeserializer<MavenRepositoryConfig> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): MavenRepositoryConfig {
        return when (json) {
            is JsonObject -> {
                MavenRepositoryConfig(
                    id = json.get("id")?.asString,
                    url = URL(SerializerUtils.checkJsonKey(json, "url")),
                    proxy = if (json.has("proxy") && json.get("proxy").isJsonObject)
                        context.deserialize<Proxy>(
                            json.getAsJsonObject("proxy"), Proxy::class.java
                        ) else null,
                    layout = json.get("layout")?.asString ?: "default",
                    enableReleases = json.get("enableReleases")?.asBoolean ?: true,
                    enableSnapshots = json.get("enableSnapshots")?.asBoolean ?: true,
                    authentication = if (json.has("authentication") && json.get("authentication").isJsonObject)
                        context.deserialize<Authentication>(
                            json.getAsJsonObject("authentication"), Authentication::class.java
                        ) else null
                )
            }
            is JsonPrimitive -> {
                MavenRepositoryConfig(url = URL(json.asString))
            }
            else -> {
                throw JsonParseException("Unsupported Maven warehouse configuration type.")
            }
        }
    }
}
