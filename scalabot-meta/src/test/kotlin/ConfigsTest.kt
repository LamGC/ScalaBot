package net.lamgc.scalabot.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lamgc.scalabot.config.serializer.*
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.AuthenticationContext
import org.eclipse.aether.repository.Proxy
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import java.net.URL
import java.util.*
import kotlin.math.abs
import kotlin.test.*

internal class BotAccountTest {

    @Test
    fun `id getter`() {
        val accountId = abs(Random().nextInt()).toLong()
        Assertions.assertEquals(accountId, BotAccount("Test", "${accountId}:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10", 0).id)
    }

    private val gson = GsonBuilder()
        .create()

    @Test
    fun deserializerTest() {
        val accountId = abs(Random().nextInt()).toLong()
        val creatorId = abs(Random().nextInt()).toLong()
        val botAccountJsonObject = gson.fromJson(
            """
            {
                "name": "TestBot",
                "token": "${accountId}:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10",
                "creatorId": $creatorId
            }
        """.trimIndent(), JsonObject::class.java
        )
        val botAccount = Gson().fromJson(botAccountJsonObject, BotAccount::class.java)
        assertEquals(accountId, botAccount.id)
        assertEquals("TestBot", botAccount.name)
        assertEquals(creatorId, botAccount.creatorId)
        assertEquals("${accountId}:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10", botAccount.token)
    }

    @Test
    fun serializerTest() {
        val accountId = abs(Random().nextInt()).toLong()
        val creatorId = abs(Random().nextInt()).toLong()
        val botAccount = BotAccount("TestBot", "${accountId}:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10", creatorId)
        val botAccountJsonObject = gson.toJsonTree(botAccount)
        assertTrue(botAccountJsonObject is JsonObject)
        assertEquals(botAccount.name, botAccountJsonObject["name"].asString)
        assertEquals(botAccount.token, botAccountJsonObject["token"].asString)
        assertNull(botAccountJsonObject["id"])
        Assertions.assertEquals(creatorId, botAccountJsonObject["creatorId"].asLong)
    }

}

internal class BotConfigTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(BotConfig::class.java, BotConfigSerializer)
        .registerTypeAdapter(Artifact::class.java, ArtifactSerializer)
        .registerTypeAdapter(ProxyConfig::class.java, ProxyConfigSerializer)
        .create()

    @Test
    fun `json serialize`() {
        val minimumExpectConfig = BotConfig(
            account = BotAccount(
                name = "TestBot",
                token = "123456789:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10",
                creatorId = 123456789L
            ),
        )

        val json = gson.toJsonTree(minimumExpectConfig)
        assertTrue(json is JsonObject)
        assertEquals(minimumExpectConfig.enabled, json.get("enabled").asBoolean)

        assertEquals(minimumExpectConfig.account.name, json.get("account").asJsonObject.get("name").asString)
        assertEquals(minimumExpectConfig.account.token, json.get("account").asJsonObject.get("token").asString)
        assertEquals(minimumExpectConfig.account.creatorId, json.get("account").asJsonObject.get("creatorId").asLong)
        assertNull(json.get("account").asJsonObject.get("id"))

        assertEquals(minimumExpectConfig.proxy.host, json.get("proxy").asJsonObject.get("host").asString)
        assertEquals(minimumExpectConfig.proxy.port, json.get("proxy").asJsonObject.get("port").asInt)
        assertEquals(minimumExpectConfig.proxy.type.name, json.get("proxy").asJsonObject.get("type").asString)

        assertEquals(minimumExpectConfig.disableBuiltInAbility, json.get("disableBuiltInAbility").asBoolean)
        assertEquals(minimumExpectConfig.autoUpdateCommandList, json.get("autoUpdateCommandList").asBoolean)

        assertNotNull(json.get("extensions"))
        assertTrue(json.get("extensions").isJsonArray)
        assertTrue(json.get("extensions").asJsonArray.isEmpty)

        assertEquals(minimumExpectConfig.baseApiUrl, json.get("baseApiUrl").asString)
    }

    @Test
    fun `json deserialize`() {
        val expectExtensionArtifact = DefaultArtifact("org.example.test:test-extension:1.0.0")
        @Language("JSON5") val looksGoodJson = """
            {
                "enabled": false,
                "account": {
                    "name": "TestBot",
                    "token": "123456789:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10",
                    "creatorId": 123456789
                },
                "proxy": {
                    "host": "localhost",
                    "port": 8080,
                    "type": "HTTP"
                },
                "disableBuiltInAbility": false,
                "autoUpdateCommandList": true,
                "extensions": [
                    "$expectExtensionArtifact"
                ],
                "baseApiUrl": "http://localhost:8080"
            }
        """.trimIndent()

        val actualConfig = gson.fromJson(looksGoodJson, BotConfig::class.java)

        assertEquals(false, actualConfig.enabled)

        assertEquals("TestBot", actualConfig.account.name)
        assertEquals("123456789:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10", actualConfig.account.token)
        assertEquals(123456789L, actualConfig.account.creatorId)

        assertEquals("localhost", actualConfig.proxy.host)
        assertEquals(8080, actualConfig.proxy.port)
        assertEquals(ProxyType.HTTP, actualConfig.proxy.type)

        assertEquals(false, actualConfig.disableBuiltInAbility)
        assertEquals(true, actualConfig.autoUpdateCommandList)

        assertEquals(1, actualConfig.extensions.size)
        assertEquals(expectExtensionArtifact, actualConfig.extensions.first())

        assertEquals("http://localhost:8080", actualConfig.baseApiUrl)
    }

    @Test
    fun `json deserialize - minimum parameters`() {
        @Language("JSON5") val minimumLooksGoodJson = """
            {
                "account": {
                    "name": "TestBot",
                    "token": "123456789:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10",
                    "creatorId": 123456789
                }
            }
        """.trimIndent()
        val expectDefaultConfig = BotConfig(account = BotAccount("Test", "Test", 0))
        val actualMinimumConfig = gson.fromJson(minimumLooksGoodJson, BotConfig::class.java)
        assertNotNull(actualMinimumConfig)
        assertEquals("TestBot", actualMinimumConfig.account.name)
        assertEquals("123456789:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10", actualMinimumConfig.account.token)
        assertEquals(123456789, actualMinimumConfig.account.creatorId)

        assertEquals(expectDefaultConfig.enabled, actualMinimumConfig.enabled)
        assertEquals(expectDefaultConfig.disableBuiltInAbility, actualMinimumConfig.disableBuiltInAbility)
        assertEquals(expectDefaultConfig.autoUpdateCommandList, actualMinimumConfig.autoUpdateCommandList)
        assertEquals(expectDefaultConfig.proxy, actualMinimumConfig.proxy)
        assertEquals(expectDefaultConfig.baseApiUrl, actualMinimumConfig.baseApiUrl)

        assertTrue(expectDefaultConfig.extensions.containsAll(actualMinimumConfig.extensions))
        assertTrue(actualMinimumConfig.extensions.containsAll(expectDefaultConfig.extensions))
    }

}

internal class ProxyConfigTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ProxyConfig::class.java, ProxyConfigSerializer)
        .registerTypeAdapter(ProxyType::class.java, ProxyTypeSerializer)
        .create()

    @Test
    fun `json serialize`() {
        val proxyConfig = ProxyConfig(
            host = "localhost",
            port = 8080,
            type = ProxyType.HTTP
        )
        val json = gson.toJsonTree(proxyConfig)
        assertTrue(json is JsonObject)
        assertEquals(proxyConfig.host, json.get("host").asString)
        assertEquals(proxyConfig.port, json.get("port").asInt)
        assertEquals(proxyConfig.type.name, json.get("type").asString)
    }

    @Test
    fun `json deserialize`() {
        @Language("JSON5") val looksGoodJson = """
            {
                "host": "localhost",
                "port": 8080,
                "type": "HTTP"
            }
        """.trimIndent()

        val actualConfig = gson.fromJson(looksGoodJson, ProxyConfig::class.java)

        assertEquals("localhost", actualConfig.host)
        assertEquals(8080, actualConfig.port)
        assertEquals(ProxyType.HTTP, actualConfig.type)
    }
}

internal class MetricsConfigTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(UsernameAuthenticator::class.java, UsernameAuthenticatorSerializer)
        .create()

    @Test
    fun `json serializer`() {
        val config = MetricsConfig(
            enable = true,
            port = 8800,
            bindAddress = "127.0.0.1",
            authenticator = UsernameAuthenticator(
                username = "username",
                password = "password"
            )
        )

        val json = gson.toJsonTree(config).asJsonObject

        assertEquals(config.enable, json.get("enable").asBoolean)
        assertEquals(config.port, json.get("port").asInt)
        assertEquals(config.bindAddress, json.get("bindAddress").asString)
        assertNotNull(config.authenticator)
        assertTrue(
            config.authenticator!!.checkCredentials(
                json.get("authenticator").asJsonObject.get("username").asString,
                json.get("authenticator").asJsonObject.get("password").asString
            )
        )

        val expectDefaultValueConfig = MetricsConfig()
        val defaultValueJson = gson.toJsonTree(expectDefaultValueConfig).asJsonObject
        assertEquals(expectDefaultValueConfig.enable, defaultValueJson.get("enable").asBoolean)
        assertEquals(expectDefaultValueConfig.port, defaultValueJson.get("port").asInt)
        assertEquals(expectDefaultValueConfig.bindAddress, defaultValueJson.get("bindAddress").asString)
        assertNull(defaultValueJson.get("authenticator"))
    }

    @Test
    fun `json deserializer`() {
        val json = """
            {
                "enable": true,
                "port": 8800,
                "bindAddress": "127.0.0.1",
                "authenticator": {
                    "username": "username",
                    "password": "password"
                }
            }
            """.trimIndent()
        val config = gson.fromJson(json, MetricsConfig::class.java)
        assertEquals(true, config.enable)
        assertEquals(8800, config.port)
        assertEquals("127.0.0.1", config.bindAddress)
        assertNotNull(config.authenticator)
        assertTrue(config.authenticator!!.checkCredentials("username", "password"))

        val defaultValueConfig = MetricsConfig()
        val defaultValueJson = gson.toJsonTree(defaultValueConfig).asJsonObject
        assertEquals(defaultValueConfig.enable, defaultValueJson.get("enable").asBoolean)
        assertEquals(defaultValueConfig.port, defaultValueJson.get("port").asInt)
        assertEquals(defaultValueConfig.bindAddress, defaultValueJson.get("bindAddress").asString)
        assertNull(defaultValueJson.get("authenticator"))
    }

    @Test
    fun `json deserializer - default value`() {
        val actualConfig = gson.fromJson("{}", MetricsConfig::class.java)
        val expectConfig = MetricsConfig()

        assertEquals(expectConfig, actualConfig)
    }

}

internal class MavenRepositoryConfigTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(MavenRepositoryConfig::class.java, MavenRepositoryConfigSerializer)
        .registerTypeAdapter(Authentication::class.java, AuthenticationSerializer)
        .create()

    @Test
    fun `json serializer`() {
        val config = MavenRepositoryConfig(
            id = "test",
            url = URL("http://localhost:8080/repository"),
            proxy = Proxy(
                "http",
                "localhost",
                8080,
            ),
            layout = "legacy",
            enableReleases = false,
            enableSnapshots = true,
            authentication = null
        )
        val json = gson.toJsonTree(config).asJsonObject
        assertEquals(config.id, json.get("id").asString)
        assertEquals(config.url.toString(), json.get("url").asString)

        assertEquals(config.proxy!!.host, json.get("proxy").asJsonObject.get("host").asString)
        assertEquals(config.proxy!!.port, json.get("proxy").asJsonObject.get("port").asInt)
        assertEquals(config.proxy!!.type, json.get("proxy").asJsonObject.get("type").asString)

        assertEquals(config.layout, json.get("layout").asString)
        assertEquals(config.enableReleases, json.get("enableReleases").asBoolean)
        assertEquals(config.enableSnapshots, json.get("enableSnapshots").asBoolean)
        assertNull(json.get("authentication"))
    }


    @Test
    fun `json deserializer`() {
        @Language("JSON5")
        val json = """
            {
                "id": "test",
                "url": "http://localhost:8080/repository",
                "proxy": {
                    "host": "localhost",
                    "port": 8080,
                    "type": "HTTP"
                },
                "layout": "legacy",
                "enableReleases": false,
                "enableSnapshots": true,
                "authentication": {
                    "username": "testUser",
                    "password": "testPassword"
                }
            }
            """.trimIndent()

        val config = gson.fromJson(json, MavenRepositoryConfig::class.java)

        assertEquals("test", config.id)
        assertEquals(URL("http://localhost:8080/repository"), config.url)
        assertEquals(
            Proxy(
                "HTTP",
                "localhost",
                8080
            ), config.proxy
        )
        assertEquals("legacy", config.layout)
        assertEquals(false, config.enableReleases)
        assertEquals(true, config.enableSnapshots)
        assertNotNull(config.authentication)

        val authContext = mockk<AuthenticationContext> {
            every { put(ofType(String::class), any()) } answers { }
        }
        config.authentication!!.fill(authContext, null, emptyMap())

        verify {
            authContext.put(any(), "testUser")
            authContext.put(any(), "testPassword".toCharArray())
        }
    }

}

internal class AppConfigTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ProxyType::class.java, ProxyTypeSerializer)
        .registerTypeAdapter(ProxyConfig::class.java, ProxyConfigSerializer)
        .registerTypeAdapter(UsernameAuthenticator::class.java, UsernameAuthenticatorSerializer)
        .registerTypeAdapter(MavenRepositoryConfig::class.java, MavenRepositoryConfigSerializer)
        .create()

    @Test
    fun `json serializer - default value`() {
        val config = AppConfig()
        val json = gson.toJsonTree(config).asJsonObject
        assertEquals(config.proxy.type.name, json.get("proxy").asJsonObject.get("type").asString)
        assertEquals(config.proxy.host, json.get("proxy").asJsonObject.get("host").asString)
        assertEquals(config.proxy.port, json.get("proxy").asJsonObject.get("port").asInt)
        assertEquals(config.metrics.enable, json.get("metrics").asJsonObject.get("enable").asBoolean)
        assertEquals(config.metrics.port, json.get("metrics").asJsonObject.get("port").asInt)
        assertEquals(config.metrics.bindAddress, json.get("metrics").asJsonObject.get("bindAddress").asString)
        assertNull(json["metrics"].asJsonObject.get("authenticator"))
        assertTrue(json["mavenRepositories"].asJsonArray.isEmpty)
        assertNull(json["mavenLocalRepository"])
    }

    @Test
    fun `json serializer - Provide values`() {
        val config = AppConfig(
            proxy = ProxyConfig(
                type = ProxyType.HTTP,
                host = "localhost",
                port = 8080
            ),
            metrics = MetricsConfig(
                enable = true,
                port = 8800,
                bindAddress = "127.0.0.1",
                authenticator = UsernameAuthenticator(
                    username = "username",
                    password = "password"
                )
            ),
            mavenRepositories = listOf(
                MavenRepositoryConfig(
                    url = URL("https://repository.maven.apache.org/maven2/")
                )
            ),
            mavenLocalRepository = "file:///tmp/maven-local-repository"
        )

        val json = gson.toJsonTree(config).asJsonObject

        assertEquals(config.proxy.type.name, json.get("proxy").asJsonObject.get("type").asString)
        assertEquals(config.proxy.host, json.get("proxy").asJsonObject.get("host").asString)
        assertEquals(config.proxy.port, json.get("proxy").asJsonObject.get("port").asInt)

        assertEquals(config.metrics.enable, json.get("metrics").asJsonObject.get("enable").asBoolean)
        assertEquals(config.metrics.port, json.get("metrics").asJsonObject.get("port").asInt)
        assertEquals(config.metrics.bindAddress, json.get("metrics").asJsonObject.get("bindAddress").asString)
        assertNotNull(config.metrics.authenticator)
        assertTrue(
            config.metrics.authenticator!!.checkCredentials(
                json.get("metrics").asJsonObject.get("authenticator").asJsonObject.get("username").asString,
                json.get("metrics").asJsonObject.get("authenticator").asJsonObject.get("password").asString
            )
        )

        assertEquals(1, json["mavenRepositories"].asJsonArray.size())
        assertEquals(
            config.mavenRepositories[0].url.toString(),
            json["mavenRepositories"].asJsonArray[0].asJsonObject.get("url").asString
        )

        assertEquals(config.mavenLocalRepository, json["mavenLocalRepository"].asString)
    }

    @Test
    fun `json deserializer - complete`() {
        val json = """
            {
                "proxy": {
                    "type": "HTTP",
                    "host": "localhost",
                    "port": 8080
                },
                "metrics": {
                    "enable": true,
                    "port": 8800,
                    "bindAddress": "127.0.0.1",
                    "authenticator": {
                        "username": "username",
                        "password": "password"
                    }
                },
                "mavenRepositories": [
                    {
                        "url": "https://repository.maven.apache.org/maven2/"
                    }
                ],
                "mavenLocalRepository": "file:///tmp/maven-local-repository"
            }
            """.trimIndent()
        val config = gson.fromJson(json, AppConfig::class.java)

        assertEquals(ProxyType.HTTP, config.proxy.type)
        assertEquals("localhost", config.proxy.host)
        assertEquals(8080, config.proxy.port)

        assertEquals(true, config.metrics.enable)
        assertEquals(8800, config.metrics.port)
        assertEquals("127.0.0.1", config.metrics.bindAddress)
        assertNotNull(config.metrics.authenticator)
        assertTrue(config.metrics.authenticator!!.checkCredentials("username", "password"))

        assertEquals(1, config.mavenRepositories.size)
        assertEquals(URL("https://repository.maven.apache.org/maven2/"), config.mavenRepositories[0].url)

        assertEquals("file:///tmp/maven-local-repository", config.mavenLocalRepository)
    }

    @Test
    fun `json deserializer - default value`() {
        val actualConfig = gson.fromJson("{}", AppConfig::class.java)
        val expectConfig = AppConfig()

        assertEquals(expectConfig, actualConfig)
    }

}
