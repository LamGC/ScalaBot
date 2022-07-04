package net.lamgc.scalabot.config.serializer

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import net.lamgc.scalabot.config.*
import net.lamgc.scalabot.config.serializer.SerializeUtils.getPrimitiveValueOrThrow
import org.eclipse.aether.artifact.AbstractArtifact
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.util.repository.AuthenticationBuilder
import java.lang.reflect.Type
import java.net.MalformedURLException
import java.net.URL
import java.util.regex.Pattern

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
        val username = json.getPrimitiveValueOrThrow("username").asString
        val password = json.getPrimitiveValueOrThrow("password").asString
        val builder = AuthenticationBuilder()
        builder.addUsername(username)
        builder.addPassword(password)
        return builder.build()
    }

}

internal object SerializeUtils {

    fun JsonObject.getPrimitiveValueOrThrow(fieldName: String): JsonPrimitive {
        val value = get(fieldName) ?: throw JsonParseException("Missing `$fieldName` field.")
        if (value !is JsonPrimitive) {
            throw JsonParseException("Invalid `account` field type.")
        }
        return value
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
                    url = URL(json.getPrimitiveValueOrThrow("url").asString),
                    proxy = if (json.has("proxy"))
                        context.deserialize<Proxy>(
                            json.get("proxy"), Proxy::class.java
                        ) else null,
                    layout = json.get("layout")?.asString ?: "default",
                    enableReleases = json.get("enableReleases")?.asBoolean ?: true,
                    enableSnapshots = json.get("enableSnapshots")?.asBoolean ?: true,
                    authentication = if (json.has("authentication"))
                        context.deserialize<Authentication>(
                            json.get("authentication"), Authentication::class.java
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

object ProxyConfigSerializer : JsonSerializer<ProxyConfig>, JsonDeserializer<ProxyConfig> {
    override fun serialize(src: ProxyConfig?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        if (src == null) {
            return JsonNull.INSTANCE
        }
        return JsonObject().apply {
            addProperty("type", src.type.name)
            addProperty("host", src.host)
            addProperty("port", src.port)
        }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ProxyConfig {
        if (json == null || json.isJsonNull) {
            return ProxyConfig()
        } else if (json !is JsonObject) {
            throw JsonParseException("Invalid json type.")
        }

        val typeStr = json["type"]?.asString ?: return ProxyConfig()
        val type = try {
            ProxyType.valueOf(typeStr)
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("Invalid proxy type: `$typeStr`")
        }

        if (!json.has("host") || !json.has("port")) {
            throw JsonParseException("Missing `host` field or `port` field.")
        }

        return ProxyConfig(
            type = type,
            host = json["host"].asString,
            port = json["port"].asInt
        )
    }

}

object BotConfigSerializer : JsonSerializer<BotConfig>, JsonDeserializer<BotConfig> {

    private val defaultConfig = BotConfig(account = BotAccount("__Default__", "__Default__", 0))

    override fun serialize(src: BotConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonObject().apply {
            addProperty("enabled", src.enabled)
            add("account", context.serialize(src.account))
            addProperty("disableBuiltInAbility", src.disableBuiltInAbility)
            addProperty("autoUpdateCommandList", src.autoUpdateCommandList)
            add("extensions", context.serialize(src.extensions))
            add("proxy", ProxyConfigSerializer.serialize(src.proxy, ProxyConfig::class.java, context))
            addProperty("baseApiUrl", src.baseApiUrl)
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): BotConfig {
        if (json !is JsonObject) {
            throw JsonParseException("Unsupported JSON type.")
        }

        if (!json.has("account")) {
            throw JsonParseException("Missing `account` field.")
        } else if (!json.get("account").isJsonObject) {
            throw JsonParseException("Invalid `account` field type.")
        }

        // 从 json 反序列化 BotConfig（使用构造函数）
        return BotConfig(
            enabled = json.get("enabled")?.asBoolean ?: defaultConfig.enabled,
            account = context.deserialize(json.get("account"), BotAccount::class.java)!!,
            disableBuiltInAbility = json.get("disableBuiltInAbility")?.asBoolean ?: defaultConfig.disableBuiltInAbility,
            autoUpdateCommandList = json.get("autoUpdateCommandList")?.asBoolean ?: defaultConfig.autoUpdateCommandList,
            extensions = context.deserialize(json.get("extensions"), object : TypeToken<Set<Artifact>>() {}.type)
                ?: defaultConfig.extensions,
            proxy = context.deserialize(json.get("proxy"), ProxyConfig::class.java) ?: defaultConfig.proxy,
            baseApiUrl = json.get("baseApiUrl")?.asString ?: defaultConfig.baseApiUrl
        )
    }
}

object BotAccountSerializer : JsonDeserializer<BotAccount> {

    private val tokenCheckRegex = Pattern.compile("\\d+:[a-zA-Z\\d_-]{35}")

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): BotAccount {
        if (json == null || json.isJsonNull) {
            throw JsonParseException("Missing `account` field.")
        } else if (!json.isJsonObject) {
            throw JsonParseException("Invalid `account` field type.")
        }
        val jsonObj = json.asJsonObject

        val name = jsonObj.getPrimitiveValueOrThrow("name").asString
        val token = jsonObj.getPrimitiveValueOrThrow("token").asString.let {
            if (it.isEmpty()) {
                throw JsonParseException("`token` cannot be empty.")
            } else if (!tokenCheckRegex.matcher(it).matches()) {
                throw JsonParseException("`token` is invalid.")
            } else {
                it
            }
        }
        val creatorId = try {
            jsonObj.getPrimitiveValueOrThrow("creatorId").asLong
        } catch (e: NumberFormatException) {
            throw JsonParseException("`creatorId` must be a number.")
        }.apply {
            if (this < 0) {
                throw JsonParseException("`creatorId` must be a positive number.")
            }
        }

        return BotAccount(name, token, creatorId)
    }

}
