package net.lamgc.scalabot.config.serializer

import com.google.gson.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lamgc.scalabot.config.*
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.AuthenticationContext
import org.eclipse.aether.repository.Proxy
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertThrows
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.net.URL
import kotlin.test.*

internal class SerializersKtTest {

    private val instance: Any
    private val method: Method

    init {
        val clazz = Class.forName("net.lamgc.scalabot.config.serializer.SerializerUtils")
        method = clazz.getDeclaredMethod("checkJsonKey", JsonObject::class.java, String::class.java)
        method.isAccessible = true
        instance = clazz.getDeclaredField("INSTANCE").apply {
            isAccessible = true
        }.get(null)
    }

    private fun invoke(json: JsonObject, key: String): String {
        try {
            return method.invoke(instance, json, key) as String
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    @Test
    fun `Json key checker test`() {
        assertThrows(JsonParseException::class.java) {
            invoke(JsonObject(), "NOT_EXIST_KEY")
        }
        assertThrows(JsonParseException::class.java) {
            invoke(JsonObject().apply { add("NULL_KEY", JsonNull.INSTANCE) }, "NULL_KEY")
        }
        assertThrows(JsonParseException::class.java) {
            invoke(JsonObject().apply { add("ARRAY_KEY", JsonArray()) }, "ARRAY_KEY")
        }
        assertThrows(JsonParseException::class.java) {
            invoke(JsonObject().apply { add("OBJECT_KEY", JsonObject()) }, "OBJECT_KEY")
        }

        val expectKey = "TEST"
        val expectString = "testString"
        val json = JsonObject().apply { addProperty(expectKey, expectString) }

        assertEquals(expectString, invoke(json, expectKey))
    }
}

internal class ProxyTypeSerializerTest {

    @Test
    fun `serialize test`() {
        for (type in ProxyType.values()) {
            assertEquals(
                JsonPrimitive(type.name), ProxyTypeSerializer.serialize(type, null, null),
                "ProxyType 序列化结果与预期不符."
            )
        }
    }

    @Test
    fun `deserialize test`() {
        assertThrows(JsonParseException::class.java) {
            ProxyTypeSerializer.deserialize(JsonObject(), null, null)
        }
        assertThrows(JsonParseException::class.java) {
            ProxyTypeSerializer.deserialize(JsonArray(), null, null)
        }

        assertThrows(JsonParseException::class.java) {
            ProxyTypeSerializer.deserialize(JsonPrimitive("NOT_IN_ENUM_VALUE"), null, null)
        }

        assertEquals(
            ProxyType.NO_PROXY,
            ProxyTypeSerializer.deserialize(JsonNull.INSTANCE, null, null)
        )

        for (type in ProxyType.values()) {
            assertEquals(
                type, ProxyTypeSerializer.deserialize(JsonPrimitive(type.name), null, null),
                "ProxyType 反序列化结果与预期不符."
            )

            assertEquals(
                type, ProxyTypeSerializer.deserialize(JsonPrimitive("  ${type.name}   "), null, null),
                "ProxyType 反序列化时未对 Json 字符串进行修剪(trim)."
            )
        }

    }

}

internal class MavenRepositoryConfigSerializerTest {

    @Test
    fun `unsupported json type deserialize test`() {
        assertThrows(JsonParseException::class.java) {
            MavenRepositoryConfigSerializer.deserialize(
                JsonArray(),
                MavenRepositoryConfig::class.java,
                TestJsonSerializationContext.default()
            )
        }
        assertThrows(JsonParseException::class.java) {
            MavenRepositoryConfigSerializer.deserialize(
                JsonNull.INSTANCE,
                MavenRepositoryConfig::class.java,
                TestJsonSerializationContext.default()
            )
        }
    }

    @Test
    fun `json primitive deserialize test`() {
        val expectRepoUrl = "https://repo.example.org/maven"
        val config = MavenRepositoryConfigSerializer.deserialize(
            JsonPrimitive(expectRepoUrl),
            MavenRepositoryConfig::class.java,
            TestJsonSerializationContext.default()
        )

        assertThrows(JsonParseException::class.java) {
            MavenRepositoryConfigSerializer.deserialize(
                JsonPrimitive("NOT A URL."),
                MavenRepositoryConfig::class.java,
                TestJsonSerializationContext.default()
            )
        }

        assertNull(config.id)
        assertEquals(URL(expectRepoUrl), config.url)
        assertNull(config.proxy, "Proxy 默认值不为 null.")
        assertEquals("default", config.layout)
        assertTrue(config.enableReleases)
        assertTrue(config.enableSnapshots)
        assertNull(config.authentication)
    }

    @Test
    fun `json object default deserialize test`() {
        val expectRepoUrl = "https://repo.example.org/maven"
        val jsonObject = JsonObject()
        jsonObject.addProperty("url", expectRepoUrl)
        val config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonSerializationContext.default()
        )

        assertNull(config.id)
        assertEquals(URL(expectRepoUrl), config.url)
        assertNull(config.proxy, "Proxy 默认值不为 null.")
        assertEquals("default", config.layout)
        assertTrue(config.enableReleases)
        assertTrue(config.enableSnapshots)
        assertNull(config.authentication)
    }

    @Test
    fun `json object deserialize test`() {
        @Language("JSON5")
        val looksGoodJsonString = """
            {
              "id": "test-repository",
              "url": "https://repo.example.org/maven",
              "proxy": {
                "type": "http",
                "host": "127.0.1.1",
                "port": 10800
              },
              "layout": "default",
              "enableReleases": false,
              "enableSnapshots": true
            }
        """.trimIndent()

        val jsonObject = Gson().fromJson(looksGoodJsonString, JsonObject::class.java)
        var config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonSerializationContext.default()
        )

        assertEquals(jsonObject["id"].asString, config.id)
        assertEquals(URL(jsonObject["url"].asString), config.url)
        assertEquals(Proxy("http", "127.0.1.1", 10800), config.proxy)
        assertEquals(jsonObject["layout"].asString, config.layout)
        assertEquals(jsonObject["enableReleases"].asBoolean, config.enableReleases)
        assertEquals(jsonObject["enableSnapshots"].asBoolean, config.enableSnapshots)

        // ------------------------------------

        jsonObject.add("proxy", JsonNull.INSTANCE)
        jsonObject.remove("layout")

        config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonSerializationContext.default()
        )

        assertEquals(jsonObject["id"].asString, config.id)
        assertEquals(URL(jsonObject["url"].asString), config.url)
        assertNull(config.proxy)
        assertEquals("default", config.layout)
        assertEquals(jsonObject["enableReleases"].asBoolean, config.enableReleases)
        assertEquals(jsonObject["enableSnapshots"].asBoolean, config.enableSnapshots)

        // ------------------------------------

        jsonObject.add("authentication", JsonArray())
        jsonObject.add("layout", mockk<JsonPrimitive> {
            every { asString }.returns(null)
        })

        config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonSerializationContext.default()
        )

        assertEquals(jsonObject["id"].asString, config.id)
        assertEquals(URL(jsonObject["url"].asString), config.url)
        assertNull(config.proxy)
        assertEquals("default", config.layout)
        assertEquals(jsonObject["enableReleases"].asBoolean, config.enableReleases)
        assertEquals(jsonObject["enableSnapshots"].asBoolean, config.enableSnapshots)
        assertNull(config.authentication)

        // ------------------------------------

        jsonObject.add("authentication", JsonObject().apply {
            addProperty("username", "testUsername")
            addProperty("password", "testPassword")
        })

        config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonSerializationContext.default()
        )

        assertEquals(jsonObject["id"].asString, config.id)
        assertEquals(URL(jsonObject["url"].asString), config.url)
        assertNull(config.proxy)
        assertEquals("default", config.layout)
        assertEquals(jsonObject["enableReleases"].asBoolean, config.enableReleases)
        assertEquals(jsonObject["enableSnapshots"].asBoolean, config.enableSnapshots)
        assertNotNull(config.authentication)
    }

}

private class TestJsonSerializationContext(private val gson: Gson) : JsonDeserializationContext,
    JsonSerializationContext {

    override fun <T : Any?> deserialize(json: JsonElement?, typeOfT: Type): T {
        return gson.fromJson(json, typeOfT)
    }

    companion object {
        fun default(): TestJsonSerializationContext {
            return TestJsonSerializationContext(
                GsonBuilder()
                    .registerTypeAdapter(MavenRepositoryConfig::class.java, MavenRepositoryConfigSerializer)
                    .registerTypeAdapter(BotConfig::class.java, BotConfigSerializer)
                    .registerTypeAdapter(ProxyType::class.java, ProxyTypeSerializer)
                    .registerTypeAdapter(Artifact::class.java, ArtifactSerializer)
                    .registerTypeAdapter(Authentication::class.java, AuthenticationSerializer)
                    .registerTypeAdapter(UsernameAuthenticator::class.java, UsernameAuthenticatorSerializer)
                    .registerTypeAdapter(ProxyConfig::class.java, ProxyConfigSerializer)
                    .create()
            )
        }
    }

    override fun serialize(src: Any?): JsonElement {
        return gson.toJsonTree(src)
    }

    override fun serialize(src: Any?, typeOfSrc: Type?): JsonElement {
        return gson.toJsonTree(src, typeOfSrc)
    }

}

internal class AuthenticationSerializerTest {

    @Test
    fun `deserialize test`() {
        assertThrows(JsonParseException::class.java) {
            AuthenticationSerializer.deserialize(
                JsonNull.INSTANCE,
                Authentication::class.java, TestJsonSerializationContext.default()
            )
        }
        assertThrows(JsonParseException::class.java) {
            AuthenticationSerializer.deserialize(
                JsonArray(),
                Authentication::class.java, TestJsonSerializationContext.default()
            )
        }
        assertThrows(JsonParseException::class.java) {
            AuthenticationSerializer.deserialize(
                JsonPrimitive("A STRING"),
                Authentication::class.java, TestJsonSerializationContext.default()
            )
        }

        val expectJsonObject = JsonObject().apply {
            addProperty("username", "testUsername")
            addProperty("password", "testPassword")
        }

        val mockContext = mockk<AuthenticationContext> {
            every { put(any(), any()) }.answers { }
        }

        val result = AuthenticationSerializer.deserialize(
            expectJsonObject,
            Authentication::class.java, TestJsonSerializationContext.default()
        )

        assertNotNull(result)
        result.fill(mockContext, "username", null)
        result.fill(mockContext, "password", null)

        verify {
            mockContext.put("username", "testUsername")
            mockContext.put("password", "testPassword".toCharArray())
        }
    }

}

internal class BotConfigSerializerTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(BotConfig::class.java, BotConfigSerializer)
        .registerTypeAdapter(Artifact::class.java, ArtifactSerializer)
        .registerTypeAdapter(ProxyType::class.java, ProxyTypeSerializer)
        .registerTypeAdapter(ProxyConfig::class.java, ProxyConfigSerializer)
        .create()

    @Test
    fun `serializer test`() {
        // 检查 BotConfig 的序列化
        val botConfig = BotConfig(
            account = BotAccount(
                name = "test-bot",
                token = "test-token",
                creatorId = 10000
            )
        )

        // 使用 gson 序列化 botConfig, 并检查序列化结果
        val jsonObject = gson.toJsonTree(botConfig) as JsonObject
        assertEquals("test-bot", jsonObject["account"].asJsonObject["name"].asString)
        assertEquals("test-token", jsonObject["account"].asJsonObject["token"].asString)
        assertEquals(10000, jsonObject["account"].asJsonObject["creatorId"].asInt)

        assertEquals(botConfig.enabled, jsonObject["enabled"].asBoolean)
        assertEquals(botConfig.proxy.host, jsonObject["proxy"].asJsonObject["host"].asString)
        assertEquals(botConfig.proxy.port, jsonObject["proxy"].asJsonObject["port"].asInt)
        assertEquals(botConfig.proxy.type.name, jsonObject["proxy"].asJsonObject["type"].asString)
        assertEquals(botConfig.disableBuiltInAbility, jsonObject["disableBuiltInAbility"].asBoolean)
        assertEquals(botConfig.autoUpdateCommandList, jsonObject["autoUpdateCommandList"].asBoolean)
        assertEquals(botConfig.extensions.isEmpty(), jsonObject["extensions"].asJsonArray.isEmpty)
        assertEquals(botConfig.baseApiUrl, jsonObject["baseApiUrl"].asString)
    }

    @Test
    fun `deserialize test`() {
        assertThrows(JsonParseException::class.java) {
            BotConfigSerializer.deserialize(
                JsonNull.INSTANCE,
                BotConfig::class.java, TestJsonSerializationContext(gson)
            )
        }
        assertThrows(JsonParseException::class.java) {
            BotConfigSerializer.deserialize(
                JsonArray(),
                BotConfig::class.java, TestJsonSerializationContext(gson)
            )
        }
        assertThrows(JsonParseException::class.java) {
            BotConfigSerializer.deserialize(
                JsonPrimitive("A STRING"),
                BotConfig::class.java, TestJsonSerializationContext(gson)
            )
        }

        // 检查 BotConfig 的反序列化中是否能正确判断 account 的类型
        assertThrows(JsonParseException::class.java) {
            BotConfigSerializer.deserialize(
                JsonObject().apply {
                    addProperty("account", "A STRING")
                },
                BotConfig::class.java, TestJsonSerializationContext(gson)
            )
        }
        assertThrows(JsonParseException::class.java) {
            BotConfigSerializer.deserialize(
                JsonObject().apply {
                    add("account", JsonNull.INSTANCE)
                },
                BotConfig::class.java, TestJsonSerializationContext(gson)
            )
        }
        assertThrows(JsonParseException::class.java) {
            BotConfigSerializer.deserialize(
                JsonObject().apply {
                    add("account", JsonArray())
                },
                BotConfig::class.java, TestJsonSerializationContext(gson)
            )
        }
        assertThrows(JsonParseException::class.java) {
            BotConfigSerializer.deserialize(
                JsonObject(),
                BotConfig::class.java, TestJsonSerializationContext(gson)
            )
        }


        val expectBotAccount = BotAccount(
            name = "test-bot",
            token = "test-token",
            creatorId = 10000
        )
        val expectDefaultBotConfig = BotConfig(account = expectBotAccount)
        val minimumJsonObject = JsonObject().apply {
            add("account", gson.toJsonTree(expectBotAccount))
        }
        val actualMinimumBotConfig = BotConfigSerializer.deserialize(
            minimumJsonObject, BotConfig::class.java, TestJsonSerializationContext(gson)
        )
        assertNotNull(actualMinimumBotConfig)
        assertEquals(expectDefaultBotConfig, actualMinimumBotConfig)

        val expectDefaultProxy = ProxyConfig(
            type = ProxyType.HTTP,
            host = "https://example.com",
            port = 443
        )

        // -------------------------------------------------

        val jsonObject = JsonObject().apply {
            add(
                "account", gson.toJsonTree(
                    BotAccount(
                        name = "test-bot",
                        token = "test-token",
                        creatorId = 10000
                    )
                )
            )
            addProperty("enabled", true)
            add("proxy", gson.toJsonTree(expectDefaultProxy))
            addProperty("disableBuiltInAbility", true)
            addProperty("autoUpdateCommandList", true)
            addProperty("baseApiUrl", "https://test.com")
            add("extensions", JsonArray())
        }

        val botConfig = BotConfigSerializer.deserialize(
            jsonObject,
            BotConfig::class.java, TestJsonSerializationContext(gson)
        )

        assertEquals("test-bot", botConfig.account.name)
        assertEquals("test-token", botConfig.account.token)
        assertEquals(10000, botConfig.account.creatorId)
        assertEquals(true, botConfig.enabled)
        assertEquals(expectDefaultProxy, botConfig.proxy)
        assertEquals(true, botConfig.disableBuiltInAbility)
        assertEquals(true, botConfig.autoUpdateCommandList)
        assertEquals("https://test.com", botConfig.baseApiUrl)
        assertEquals(true, botConfig.extensions.isEmpty())
    }
}

internal class ProxyConfigSerializerTest {

    // 测试 ProxyConfig 的 Json 序列化
    @Test
    fun `serialize test`() {
        assertEquals(JsonNull.INSTANCE, ProxyConfigSerializer.serialize(null, null, null))

        val expectDefaultConfig = ProxyConfig()
        val actualDefaultJson = ProxyConfigSerializer.serialize(expectDefaultConfig, null, null)
        assertTrue(actualDefaultJson is JsonObject)
        assertEquals(expectDefaultConfig.type.name, actualDefaultJson["type"].asString)
        assertEquals(expectDefaultConfig.host, actualDefaultJson["host"].asString)
        assertEquals(expectDefaultConfig.port, actualDefaultJson["port"].asInt)
    }

    @Test
    fun `Bad type deserialize test`() {
        val defaultConfig = ProxyConfig()
        assertEquals(defaultConfig, ProxyConfigSerializer.deserialize(null, null, null))
        assertEquals(defaultConfig, ProxyConfigSerializer.deserialize(JsonNull.INSTANCE, null, null))
    }

    @Test
    fun `deserialize test - object`() {
        val defaultConfig = ProxyConfig()

        assertThrows(JsonParseException::class.java) {
            ProxyConfigSerializer.deserialize(JsonArray(), null, null)
        }

        val jsonWithoutType = JsonObject().apply {
            addProperty("host", "example.com")
            addProperty("port", 8080)
        }
        assertEquals(defaultConfig, ProxyConfigSerializer.deserialize(jsonWithoutType, null, null))

        val looksGoodJson = JsonObject().apply {
            addProperty("type", "HTTP")
            addProperty("host", "example.com")
            addProperty("port", 8080)
        }
        assertEquals(
            ProxyConfig(
                type = ProxyType.HTTP,
                host = "example.com",
                port = 8080
            ), ProxyConfigSerializer.deserialize(looksGoodJson, null, null)
        )

        assertThrows(JsonParseException::class.java) {
            ProxyConfigSerializer.deserialize(JsonObject().apply {
                addProperty("type", "UNKNOWN")
                addProperty("host", "example.com")
                addProperty("port", 8080)
            }, null, null)
        }

        assertThrows(JsonParseException::class.java) {
            ProxyConfigSerializer.deserialize(JsonObject().apply {
                addProperty("type", "HTTP")
                addProperty("host", "example.com")
            }, null, null)
        }
        assertThrows(JsonParseException::class.java) {
            ProxyConfigSerializer.deserialize(JsonObject().apply {
                addProperty("type", "HTTP")
                addProperty("port", 8080)
            }, null, null)
        }
    }
}

internal class ArtifactSerializerTest {

    @org.junit.jupiter.api.Test
    fun badJsonType() {
        assertFailsWith<JsonParseException> { ArtifactSerializer.deserialize(JsonObject(), null, null) }
        assertFailsWith<JsonParseException> { ArtifactSerializer.deserialize(JsonArray(), null, null) }
        assertFailsWith<JsonParseException> { ArtifactSerializer.deserialize(JsonPrimitive("A STRING"), null, null) }
    }

    @org.junit.jupiter.api.Test
    fun `Basic format serialization`() {
        val gav = "org.example.software:test:1.0.0-SNAPSHOT"
        val expectArtifact = DefaultArtifact(gav)
        val actualArtifact = DefaultArtifact(ArtifactSerializer.serialize(expectArtifact, null, null).asString)
        assertEquals(expectArtifact, actualArtifact)
    }

    @org.junit.jupiter.api.Test
    fun `Full format serialization`() {
        val gav = "org.example.software:test:war:javadoc:1.0.0-SNAPSHOT"
        val expectArtifact = DefaultArtifact(gav)
        val actualArtifact = DefaultArtifact(ArtifactSerializer.serialize(expectArtifact, null, null).asString)
        assertEquals(expectArtifact, actualArtifact)
    }

    @org.junit.jupiter.api.Test
    fun `Bad format serialization`() {
        assertFailsWith<JsonParseException> {
            ArtifactSerializer.deserialize(JsonPrimitive("org.example~test"), null, null)
        }
    }

    @org.junit.jupiter.api.Test
    fun `Other artifact implementation serialization`() {
        val gav = "org.example.software:test:war:javadoc:1.0.0-SNAPSHOT"
        val expectArtifact = DefaultArtifact(gav)
        val otherArtifactImpl = mockk<Artifact> {
            every { groupId } returns expectArtifact.groupId
            every { artifactId } returns expectArtifact.artifactId
            every { version } returns expectArtifact.version
            every { classifier } returns expectArtifact.classifier
            every { extension } returns expectArtifact.extension
        }

        val json = ArtifactSerializer.serialize(otherArtifactImpl, null, null)
        assertTrue(json is JsonPrimitive)
        assertEquals(expectArtifact.toString(), json.asString)
    }

    @org.junit.jupiter.api.Test
    fun deserialize() {
        val gav = "org.example.software:test:war:javadoc:1.0.0-SNAPSHOT"
        val expectArtifact = DefaultArtifact(gav)
        val actualArtifact = ArtifactSerializer.deserialize(JsonPrimitive(gav), null, null)
        assertEquals(expectArtifact, actualArtifact)
    }
}
