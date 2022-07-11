package net.lamgc.scalabot

import ch.qos.logback.core.PropertyDefinerBase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import mu.KotlinLogging
import net.lamgc.scalabot.config.*
import net.lamgc.scalabot.config.serializer.*
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.RepositoryPolicy
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger { }

internal fun ProxyType.toTelegramBotsType(): DefaultBotOptions.ProxyType {
    return when (this) {
        ProxyType.NO_PROXY -> DefaultBotOptions.ProxyType.NO_PROXY
        ProxyType.HTTP -> DefaultBotOptions.ProxyType.HTTP
        ProxyType.HTTPS -> DefaultBotOptions.ProxyType.HTTP
        ProxyType.SOCKS4 -> DefaultBotOptions.ProxyType.SOCKS4
        ProxyType.SOCKS5 -> DefaultBotOptions.ProxyType.SOCKS5
    }
}

internal fun ProxyConfig.toAetherProxy(): Proxy? {
    val typeStr = when (type) {
        ProxyType.HTTP -> Proxy.TYPE_HTTP
        ProxyType.HTTPS -> Proxy.TYPE_HTTPS
        else -> return null
    }
    return Proxy(typeStr, host, port)
}

internal fun MavenRepositoryConfig.toRemoteRepository(proxyConfig: ProxyConfig): RemoteRepository {
    val builder =
        RemoteRepository.Builder(id ?: createDefaultRepositoryId(), checkRepositoryLayout(layout), url.toString())
    if (proxy != null) {
        builder.setProxy(proxy)
    } else if (proxyConfig.type == ProxyType.HTTP) {
        builder.setProxy(proxyConfig.toAetherProxy())
    }

    builder.setReleasePolicy(
        RepositoryPolicy(
            enableReleases,
            RepositoryPolicy.UPDATE_POLICY_NEVER,
            RepositoryPolicy.CHECKSUM_POLICY_FAIL
        )
    )
    builder.setSnapshotPolicy(
        RepositoryPolicy(
            enableSnapshots,
            RepositoryPolicy.UPDATE_POLICY_ALWAYS,
            RepositoryPolicy.CHECKSUM_POLICY_WARN
        )
    )

    return builder.build()
}

private fun checkRepositoryLayout(layoutType: String): String {
    val type = layoutType.trim().lowercase()
    if (type != "default" && type != "legacy") {
        throw IllegalArgumentException("Invalid layout type (expecting 'default' or 'legacy')")
    }
    return type
}

private val repoNumberGenerator = AtomicInteger(1)

private fun createDefaultRepositoryId(): String {
    return "Repository-${repoNumberGenerator.getAndIncrement()}"
}

/**
 * 需要用到的路径.
 *
 * 必须提供 `pathSupplier` 或 `fileSupplier` 其中一个, 才能正常提供路径.
 */
internal enum class AppPaths(
    private val pathSupplier: () -> String = { fileSupplier.invoke().canonicalPath },
    private val initializer: AppPaths.() -> Unit = AppPaths::defaultInitializer,
    private val fileSupplier: () -> File = { File(pathSupplier()) }
) {
    /**
     * 数据根目录.
     *
     * 所有运行数据的存放位置.
     *
     * 提示: 结尾不带 `/`.
     */
    DATA_ROOT(fileSupplier = {
        File(
            System.getProperty(PathConst.PROP_DATA_PATH) ?: System.getenv(PathConst.ENV_DATA_PATH)
            ?: System.getProperty("user.dir") ?: "."
        )
    }, initializer = {
        val f = file
        if (!f.exists()) {
            f.mkdirs()
        }
    }),

    CONFIG_APPLICATION({ "$DATA_ROOT/config.json" }, {
        if (!file.exists()) {
            file.bufferedWriter(StandardCharsets.UTF_8).use {
                GsonConst.appConfigGson.toJson(
                    AppConfig(
                        mavenRepositories = listOf(
                            MavenRepositoryConfig(
                                id = "central",
                                url = URL(MavenRepositoryExtensionFinder.MAVEN_CENTRAL_URL)
                            )
                        )
                    ), it
                )
            }
        }
    }),
    CONFIG_BOT({ "$DATA_ROOT/bot.json" }, {
        if (!file.exists()) {
            file.bufferedWriter(StandardCharsets.UTF_8).use {
                GsonConst.botConfigGson.toJson(
                    setOf(
                        BotConfig(
                            enabled = true,
                            proxy = ProxyConfig(),
                            account = BotAccount(
                                "Bot Username",
                                "Bot API Token",
                                -1
                            ), extensions = emptySet()
                        )
                    ), it
                )
            }
        }
    }),
    DATA_DB({ "$DATA_ROOT/data/db/" }),
    DATA_LOGS({ "$DATA_ROOT/data/logs/" }),
    EXTENSIONS({ "$DATA_ROOT/extensions/" }),
    DATA_EXTENSIONS({ "$DATA_ROOT/data/extensions/" }),
    TEMP({ "$DATA_ROOT/tmp/" })
    ;

    val file: File
        get() = fileSupplier.invoke()
    val path: String
        get() = pathSupplier.invoke()

    private val initialized = AtomicBoolean(false)

    @Synchronized
    fun initial() {
        if (!initialized.get()) {
            initializer()
            initialized.set(true)
        }
    }

    override fun toString(): String {
        return path
    }

    object PathConst {
        const val PROP_DATA_PATH = "bot.path.data"
        const val ENV_DATA_PATH = "BOT_DATA_PATH"
    }

}

/**
 * 为 LogBack 提供日志目录路径.
 */
internal class LogDirectorySupplier : PropertyDefinerBase() {
    override fun getPropertyValue(): String {
        return AppPaths.DATA_LOGS.path
    }
}

internal object Const {
    val config = loadAppConfig()
}

private fun AppPaths.defaultInitializer() {
    val f = file
    val p = path
    if (!f.exists()) {
        val result = if (p.endsWith("/")) {
            f.mkdirs()
        } else {
            f.createNewFile()
        }
        if (!result) {
            log.warn { "初始化文件(夹)失败: $p" }
        }
    }
}

/**
 * 执行 AppPaths 所有项目的初始化, 并检查是否停止运行, 让用户编辑配置.
 *
 * @return 如果需要让用户编辑配置, 则返回 `true`.
 */
internal fun initialFiles(): Boolean {
    val configFilesNotInitialized = !AppPaths.CONFIG_APPLICATION.file.exists()
            && !AppPaths.CONFIG_BOT.file.exists()

    for (path in AppPaths.values()) {
        path.initial()
    }

    if (configFilesNotInitialized) {
        log.warn { "配置文件已初始化, 请根据需要修改配置文件后重新启动本程序." }
        return true
    }
    return false
}

internal object GsonConst {
    private val baseGson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    val appConfigGson: Gson = baseGson.newBuilder()
        .registerTypeAdapter(ProxyType::class.java, ProxyTypeSerializer)
        .registerTypeAdapter(MavenRepositoryConfig::class.java, MavenRepositoryConfigSerializer)
        .registerTypeAdapter(Authentication::class.java, AuthenticationSerializer)
        .registerTypeAdapter(UsernameAuthenticator::class.java, UsernameAuthenticatorSerializer)
        .create()

    val botConfigGson: Gson = baseGson.newBuilder()
        .registerTypeAdapter(ProxyType::class.java, ProxyTypeSerializer)
        .registerTypeAdapter(BotConfig::class.java, BotConfigSerializer)
        .registerTypeAdapter(Artifact::class.java, ArtifactSerializer)
        .registerTypeAdapter(BotAccount::class.java, BotAccountSerializer)
        .create()
}

internal fun loadAppConfig(configFile: File = AppPaths.CONFIG_APPLICATION.file): AppConfig {
    try {
        configFile.bufferedReader(StandardCharsets.UTF_8).use {
            return GsonConst.appConfigGson.fromJson(it, AppConfig::class.java)!!
        }
    } catch (e: Exception) {
        log.error { "读取 config.json 时发生错误, 请检查配置格式是否正确." }
        throw e
    }
}

internal fun loadBotConfigJson(botConfigFile: File = AppPaths.CONFIG_BOT.file): JsonArray? {
    try {
        botConfigFile.bufferedReader(StandardCharsets.UTF_8).use {
            return GsonConst.botConfigGson.fromJson(it, JsonArray::class.java)!!
        }
    } catch (e: Exception) {
        log.error(e) { "读取 Bot 配置文件 (bot.json) 时发生错误, 请检查配置格式是否正确." }
        return null
    }
}
