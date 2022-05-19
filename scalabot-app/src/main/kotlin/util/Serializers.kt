package net.lamgc.scalabot.util

import com.google.gson.*
import mu.KotlinLogging
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

    private val log = KotlinLogging.logger { }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Authentication? {
        val builder = AuthenticationBuilder()
        when (json) {
            is JsonArray -> {
                for (element in json) {
                    if (element is JsonArray) {
                        builder.addCustom(jsonArrayToAuthentication(element))
                    } else if (element is JsonObject) {
                        jsonToAuthentication(element, builder)
                    }
                }
            }
            is JsonObject -> {
                jsonToAuthentication(json, builder)
            }
            else -> {
                throw JsonParseException("Unsupported JSON data type: ${json::class.java}")
            }
        }
        return builder.build()
    }

    private fun jsonArrayToAuthentication(jsonArray: JsonArray): Authentication {
        val builder = AuthenticationBuilder()
        for (element in jsonArray) {
            when (element) {
                is JsonObject -> jsonToAuthentication(element, builder)
                is JsonArray -> builder.addCustom(jsonArrayToAuthentication(element))
                else -> log.warn { "不支持的 Json 类型: ${element::class.java}" }
            }
        }
        return builder.build()
    }

    private const val KEY_TYPE = "type"

    private fun jsonToAuthentication(json: JsonObject, builder: AuthenticationBuilder) {
        if (!json.has(KEY_TYPE)) {
            log.warn { "缺少 type 字段, 无法判断 Maven 认证信息类型." }
            return
        } else if (!json.get(KEY_TYPE).isJsonPrimitive) {
            log.warn { "type 字段类型错误(应为 Primitive 类型), 无法判断 Maven 认证信息类型.(实际类型: `${json::class.java}`)" }
            return
        }

        when (json.get(KEY_TYPE).asString.trim().lowercase()) {
            "string" -> {
                builder.addString(
                    SerializerUtils.checkJsonKey(json, "key"),
                    SerializerUtils.checkJsonKey(json, "value")
                )
            }
            "secret" -> {
                builder.addSecret(
                    SerializerUtils.checkJsonKey(json, "key"),
                    SerializerUtils.checkJsonKey(json, "value")
                )
            }
        }

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
