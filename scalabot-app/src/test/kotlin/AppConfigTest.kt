package net.lamgc.scalabot

import com.github.stefanbirkner.systemlambda.SystemLambda
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import mu.KotlinLogging
import net.lamgc.scalabot.config.MavenRepositoryConfig
import net.lamgc.scalabot.config.ProxyConfig
import net.lamgc.scalabot.config.ProxyType
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.test.*

internal class AppPathsTest {

    @Test
    fun `Consistency check`() {
        for (path in AppPaths.entries) {
            assertEquals(
                File(path.path).canonicalPath,
                path.file.canonicalPath,
                "路径 File 与 Path 不一致: ${path.name}"
            )
        }
    }

    @Test
    fun `Data root path priority`() {
        System.setProperty(AppPaths.PathConst.PROP_DATA_PATH, "fromSystemProperties")

        assertEquals("fromSystemProperties", AppPaths.DATA_ROOT.file.path, "`DATA_ROOT`没有优先返回 Property 的值.")
        System.getProperties().remove(AppPaths.PathConst.PROP_DATA_PATH)

        val expectEnvValue = "fromEnvironmentVariable"
        SystemLambda.withEnvironmentVariable(AppPaths.PathConst.ENV_DATA_PATH, expectEnvValue).execute {
            assertEquals(
                expectEnvValue, AppPaths.DATA_ROOT.file.path,
                "`DATA_ROOT`没有优先返回 env 的值."
            )
        }

        SystemLambda.withEnvironmentVariable(AppPaths.PathConst.ENV_DATA_PATH, null).execute {
            assertEquals(
                System.getProperty("user.dir"), AppPaths.DATA_ROOT.file.path,
                "`DATA_ROOT`没有返回 System.properties `user.dir` 的值."
            )
            val userDir = System.getProperty("user.dir")
            System.getProperties().remove("user.dir")
            assertEquals(".", AppPaths.DATA_ROOT.file.path, "`DATA_ROOT`没有返回替补值 `.`(当前目录).")
            System.setProperty("user.dir", userDir)
            assertNotNull(System.getProperty("user.dir"), "环境还原失败!")
        }
    }

    @Test
    fun `default initializer`(@TempDir testDir: File) {
        val defaultInitializerMethod = Class.forName("net.lamgc.scalabot.AppConfigsKt")
            .getDeclaredMethod("defaultInitializer", AppPaths::class.java)
            .apply { isAccessible = true }

        val dirPath = "${testDir.canonicalPath}/directory/"
        val dirFile = File(dirPath)
        mockk<AppPaths> {
            every { file }.returns(File(dirPath))
            every { path }.returns(dirPath)
            every { initial() }.answers {
                defaultInitializerMethod.invoke(null, this@mockk)
            }
        }.initial()
        assertTrue(dirFile.exists() && dirFile.isDirectory, "默认初始器未正常初始化【文件夹】.")

        File(testDir, "test.txt").apply {
            mockk<AppPaths> {
                every { file }.returns(this@apply)
                every { path }.returns(this@apply.canonicalPath)
                every { initial() }.answers {
                    defaultInitializerMethod.invoke(null, this@mockk)
                }
            }.initial()
            assertTrue(this@apply.exists() && this@apply.isFile, "默认初始器未正常初始化【文件】.")
        }

        val alreadyExistsFile = File("${testDir.canonicalPath}/alreadyExists.txt").apply {
            if (!exists()) {
                createNewFile()
            }
        }
        assertTrue(alreadyExistsFile.exists(), "文件状态与预期不符.")
        mockk<File> {
            every { exists() }.returns(true)
            every { canonicalPath }.answers { alreadyExistsFile.canonicalPath }
            every { createNewFile() }.answers { alreadyExistsFile.createNewFile() }
            every { mkdirs() }.answers { alreadyExistsFile.mkdirs() }
            every { mkdir() }.answers { alreadyExistsFile.mkdir() }
        }.apply {
            mockk<AppPaths> {
                every { file }.returns(this@apply)
                every { path }.returns(this@apply.canonicalPath)
                every { initial() }.answers {
                    defaultInitializerMethod.invoke(null, this@mockk)
                }
            }.initial()
            verify(exactly = 0) { createNewFile() }
            verify(exactly = 0) { mkdir() }
            verify(exactly = 0) { mkdirs() }
        }

        mockk<File> {
            every { exists() }.returns(false)
            every { canonicalPath }.answers { alreadyExistsFile.canonicalPath }
            every { createNewFile() }.answers { false }
            every { mkdirs() }.answers { false }
            every { mkdir() }.answers { false }
        }.apply {
            mockk<AppPaths> {
                every { file }.returns(this@apply)
                every { path }.returns(this@apply.canonicalPath)
                every { initial() }.answers {
                    defaultInitializerMethod.invoke(null, this@mockk)
                }
            }.initial()
            verify(exactly = 1) { createNewFile() }
            verify(exactly = 0) { mkdir() }
            verify(exactly = 0) { mkdirs() }
        }

        defaultInitializerMethod.isAccessible = false
    }

    @Test
    fun `loadBotConfig test`(@TempDir testDir: File) {
        assertNull(loadBotConfigJson(File("/NOT_EXISTS_FILE")), "加载 BotConfigs 失败时应该返回 null.")

        SystemLambda.withEnvironmentVariable(AppPaths.PathConst.ENV_DATA_PATH, testDir.canonicalPath).execute {
            assertNull(loadBotConfigJson(), "加载 BotConfigs 失败时应该返回 null.")

            File(testDir, "bot.json").apply {
                //language=JSON5
                writeText(
                    """
                        [
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
                              "org.example.test:test-extension:1.0.0"
                            ],
                            "baseApiUrl": "http://localhost:8080"
                          }
                        ]
                    """.trimIndent()
                )
            }

            val botConfigJsons = loadBotConfigJson()
            assertNotNull(botConfigJsons)
            assertEquals(1, botConfigJsons.size())
        }
    }

    @Test
    fun `loadAppConfig test`(@TempDir testDir: File) {
        assertThrows<IOException>("加载失败时应该抛出 IOException.") {
            loadAppConfig(File("/NOT_EXISTS_FILE"))
        }

        SystemLambda.withEnvironmentVariable(AppPaths.PathConst.ENV_DATA_PATH, testDir.canonicalPath).execute {
            assertThrows<IOException>("加载失败时应该抛出 IOException.") {
                loadAppConfig()
            }

            File(testDir, "config.json").apply {
                //language=JSON5
                writeText(
                    """
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
                )
            }

            val appConfigs = loadAppConfig()
            assertNotNull(appConfigs)
        }
    }

    @Test
    fun `ProxyType_toTelegramBotsType test`() {
        val expectTypeMapping = mapOf(
            ProxyType.NO_PROXY to null,
            ProxyType.SOCKS5 to Proxy.Type.SOCKS,
            ProxyType.SOCKS4 to Proxy.Type.SOCKS,
            ProxyType.HTTP to Proxy.Type.HTTP,
            ProxyType.HTTPS to Proxy.Type.HTTP
        )

        for (proxyType in ProxyType.entries) {
            assertEquals(
                expectTypeMapping[proxyType],
                proxyType.toJavaProxyType(),
                "ProxyType 转换失败."
            )
        }
    }

    @Test
    fun `ProxyConfig_toAetherProxy test`() {
        val host = "proxy.example.org"
        val port = 1080

        val expectNotNullProxyType = setOf(
            ProxyType.HTTP,
            ProxyType.HTTPS
        )
        for (proxyType in ProxyType.entries) {
            val proxyConfig = ProxyConfig(proxyType, host, port)
            val aetherProxy = proxyConfig.toAetherProxy()
            if (expectNotNullProxyType.contains(proxyType)) {
                assertNotNull(aetherProxy, "支持的代理类型应该不为 null.")
                assertEquals(host, aetherProxy.host)
                assertEquals(port, aetherProxy.port)
            } else {
                assertNull(aetherProxy, "不支持的代理类型应该返回 null.")
            }
        }
    }

    @Test
    fun `MavenRepositoryConfig_toRemoteRepository test`() {
        val defaultMavenRepositoryConfig = MavenRepositoryConfig(
            url = URL(MavenRepositoryExtensionFinder.MAVEN_CENTRAL_URL),
            enableReleases = true,
            enableSnapshots = false
        )
        val remoteRepositoryWithoutId = defaultMavenRepositoryConfig.toRemoteRepository(
            ProxyConfig(ProxyType.NO_PROXY, "", 0)
        )
        assertEquals(MavenRepositoryExtensionFinder.MAVEN_CENTRAL_URL, remoteRepositoryWithoutId.url.toString())
        assertNotNull(remoteRepositoryWithoutId.id)
        assertTrue(remoteRepositoryWithoutId.getPolicy(false).isEnabled)
        assertFalse(remoteRepositoryWithoutId.getPolicy(true).isEnabled)

        val remoteRepositoryWithId = defaultMavenRepositoryConfig.copy(id = "test-repo").toRemoteRepository(
            ProxyConfig(ProxyType.HTTP, "127.0.0.1", 1080)
        )

        assertEquals("test-repo", remoteRepositoryWithId.id)
        assertEquals(MavenRepositoryExtensionFinder.MAVEN_CENTRAL_URL, remoteRepositoryWithId.url.toString())
        assertEquals("http", remoteRepositoryWithId.proxy.type)
        assertEquals("127.0.0.1", remoteRepositoryWithId.proxy.host)
        assertEquals(1080, remoteRepositoryWithId.proxy.port)
        assertEquals(remoteRepositoryWithId.id, remoteRepositoryWithId.id)

        val remoteRepositoryWithProxy = defaultMavenRepositoryConfig.copy(
            id = "test-repo",
            proxy = ProxyConfig(ProxyType.HTTP, "example.org", 1080).toAetherProxy()
        ).toRemoteRepository(ProxyConfig(ProxyType.HTTP, "localhost", 8080))
        assertEquals("http", remoteRepositoryWithProxy.proxy.type)
        assertEquals("example.org", remoteRepositoryWithProxy.proxy.host, "未优先使用 MavenRepositoryConfig 中的 proxy 属性.")
        assertEquals(1080, remoteRepositoryWithProxy.proxy.port, "未优先使用 MavenRepositoryConfig 中的 proxy 属性.")
    }

    @Test
    fun `checkRepositoryLayout test`() {
        val noProxyConfig = ProxyConfig(ProxyType.NO_PROXY, "", 0)
        assertEquals(
            "default", MavenRepositoryConfig(url = URL("https://repo.example.org"))
                .toRemoteRepository(noProxyConfig).contentType
        )
        assertEquals(
            "legacy", MavenRepositoryConfig(url = URL("https://repo.example.org"), layout = "LEgaCY")
                .toRemoteRepository(noProxyConfig).contentType
        )
        assertThrows<IllegalArgumentException> {
            MavenRepositoryConfig(
                url = URL("https://repo.example.org"),
                layout = "NOT_EXISTS_LAYOUT"
            ).toRemoteRepository(noProxyConfig)
        }
    }

    @Test
    fun `initialFiles test`(@TempDir testDir: Path) {
        // 这么做是为了让日志文件创建在其他地方, 由于日志文件在运行时会持续占用, 在 windows 中文件会被锁定,
        // 导致测试框架无法正常清除测试所使用的临时文件夹.
        val logsDir = Files.createTempDirectory("ammmmmm-logs-")
        System.setProperty(AppPaths.PathConst.PROP_DATA_PATH, logsDir.toString())
        assertEquals(logsDir.toString(), AppPaths.DATA_ROOT.path, "日志目录设定失败.")
        KotlinLogging.logger("TEST").error { "日志占用.(无需理会), 日志目录: $logsDir" }
        AppPaths.DATA_LOGS.file.listFiles { _, name -> name.endsWith(".log") }?.forEach {
            it.deleteOnExit()
        }

        val fullInitializeDir = Files.createTempDirectory(testDir, "fullInitialize")
        fullInitializeDir.deleteExisting()
        System.setProperty(AppPaths.PathConst.PROP_DATA_PATH, fullInitializeDir.toString())
        assertEquals(fullInitializeDir.toString(), AppPaths.DATA_ROOT.path, "测试路径设定失败.")

        assertTrue(initialFiles(), "方法未能提醒用户编辑初始配置文件.")

        for (path in AppPaths.entries) {
            assertTrue(path.file.exists(), "文件未初始化成功: ${path.path}")
            if (path.file.isFile) {
                assertNotEquals(0, path.file.length(), "文件未初始化成功(大小为 0): ${path.path}")
            }
            path.reset()
        }

        assertFalse(initialFiles(), "方法试图在配置已初始化的情况下提醒用户编辑初始配置文件.")

        for (path in AppPaths.entries) {
            assertTrue(path.file.exists(), "文件未初始化成功: ${path.path}")
            if (path.file.isFile) {
                assertNotEquals(0, path.file.length(), "文件未初始化成功(大小为 0): ${path.path}")
            }
            path.reset()
        }

        assertTrue(AppPaths.CONFIG_APPLICATION.file.delete(), "config.json 删除失败.")
        assertFalse(initialFiles(), "方法试图在部分配置已初始化的情况下提醒用户编辑初始配置文件.")

        for (path in AppPaths.entries) {
            assertTrue(path.file.exists(), "文件未初始化成功: ${path.path}")
            if (path.file.isFile) {
                assertNotEquals(0, path.file.length(), "文件未初始化成功(大小为 0): ${path.path}")
            }
            path.reset()
        }

        assertTrue(AppPaths.CONFIG_BOT.file.delete(), "bot.json 删除失败.")
        assertFalse(initialFiles(), "方法试图在部分配置已初始化的情况下提醒用户编辑初始配置文件.")

        for (path in AppPaths.entries) {
            assertTrue(path.file.exists(), "文件未初始化成功: ${path.path}")
            if (path.file.isFile) {
                assertNotEquals(0, path.file.length(), "文件未初始化成功(大小为 0): ${path.path}")
            }
            path.reset()
        }

        assertTrue(AppPaths.CONFIG_APPLICATION.file.delete(), "config.json 删除失败.")
        assertTrue(AppPaths.CONFIG_BOT.file.delete(), "bot.json 删除失败.")
        assertTrue(
            initialFiles(),
            "在主要配置文件(config.json 和 bot.json)不存在的情况下初始化文件后, 方法未能提醒用户编辑初始配置文件."
        )

        for (path in AppPaths.entries) {
            assertTrue(path.file.exists(), "文件未初始化成功: ${path.path}")
            if (path.file.isFile) {
                assertNotEquals(0, path.file.length(), "文件未初始化成功(大小为 0): ${path.path}")
            }
        }

        AppPaths.CONFIG_APPLICATION.file.writeText("Test-APPLICATION")
        AppPaths.CONFIG_BOT.file.writeText("Test-BOT")
        assertFalse(initialFiles(), "方法试图在部分配置已初始化的情况下提醒用户编辑初始配置文件.")
        assertEquals(
            "Test-APPLICATION", AppPaths.CONFIG_APPLICATION.file.readText(),
            "config.json 被覆盖. initialized 并未阻止重复初始化."
        )
        assertEquals(
            "Test-BOT", AppPaths.CONFIG_BOT.file.readText(),
            "bot.json 被覆盖. initialized 并未阻止重复初始化."
        )

        System.getProperties().remove(AppPaths.PathConst.PROP_DATA_PATH)
    }

    private fun AppPaths.reset() {
        val method = AppPaths::class.java.getDeclaredMethod("reset")
        method.isAccessible = true
        method.invoke(this)
        method.isAccessible = false
    }

}
