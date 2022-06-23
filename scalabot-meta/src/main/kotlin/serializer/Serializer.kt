package net.lamgc.scalabot.config.serializer

import com.google.gson.*
import net.lamgc.scalabot.config.MavenRepositoryConfig
import net.lamgc.scalabot.config.ProxyType
import net.lamgc.scalabot.config.UsernameAuthenticator
import org.eclipse.aether.artifact.AbstractArtifact
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.util.repository.AuthenticationBuilder
import java.lang.reflect.Type
import java.net.MalformedURLException
import java.net.URL

object ProxyTypeSerializer : JsonDeserializer<ProxyType>,
    JsonSerializer<ProxyType> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ProxyType {
        if (json.isJsonNull) {
            return ProxyType.NO_PROXY
        }
        if (!json.isJsonPrimitive) {
            throw JsonParseException("Wrong configuration value type.")
        }
        val value = json.asString.trim()
        try {
            return ProxyType.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("Invalid value: $value")
        }
    }

    override fun serialize(
        src: ProxyType,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src.toString())
    }
}

object ArtifactSerializer : JsonSerializer<Artifact>, JsonDeserializer<Artifact> {
    override fun serialize(src: Artifact, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return if (src is AbstractArtifact) {
            JsonPrimitive(src.toString())
        } else {
            JsonPrimitive(
                DefaultArtifact(
                    src.groupId,
                    src.artifactId,
                    src.classifier,
                    src.extension,
                    src.version
                ).toString()
            )
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Artifact {
        if (!json.isJsonPrimitive) {
            throw JsonParseException("Wrong configuration value type.")
        }
        val artifactStr = json.asString.trim()
        try {
            return DefaultArtifact(artifactStr)
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("Invalid artifact format: `${artifactStr}`.")
        }
    }

}

object AuthenticationSerializer : JsonDeserializer<Authentication> {

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

object MavenRepositoryConfigSerializer
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
                try {
                    return MavenRepositoryConfig(url = URL(json.asString))
                } catch (e: MalformedURLException) {
                    throw JsonParseException("Invalid URL: ${json.asString}", e)
                }
            }
            else -> {
                throw JsonParseException("Unsupported Maven repository configuration type. (Only support JSON object or url string)")
            }
        }
    }
}

object UsernameAuthenticatorSerializer : JsonSerializer<UsernameAuthenticator>,
    JsonDeserializer<UsernameAuthenticator> {

    override fun serialize(
        src: UsernameAuthenticator,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return src.toJsonObject()
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): UsernameAuthenticator? {
        if (json.isJsonNull) {
            return null
        } else if (!json.isJsonObject) {
            throw JsonParseException("Invalid attribute value type.")
        }

        val jsonObj = json.asJsonObject

        if (jsonObj["username"]?.isJsonPrimitive != true) {
            throw JsonParseException("Invalid attribute value: username")
        } else if (jsonObj["password"]?.isJsonPrimitive != true) {
            throw JsonParseException("Invalid attribute value: password")
        }

        if (jsonObj["username"].asString.isEmpty() || jsonObj["password"].asString.isEmpty()) {
            throw JsonParseException("`username` or `password` is empty.")
        }
        return UsernameAuthenticator(jsonObj["username"].asString, jsonObj["password"].asString)
    }

}
