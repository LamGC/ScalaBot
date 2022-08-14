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
import java.util.function.Supplier
import kotlin.reflect.KProperty

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

internal fun MavenRepositoryConfig.toRemoteRepository(proxyConfig: ProxyConfig? = null): RemoteRepository {
    val repositoryId = if (id == null) {
        val generatedRepoId = createDefaultRepositoryId()
        log.debug { "仓库 Url `$url` 未设置仓库 Id, 已分配缺省 Id: $generatedRepoId" }
        generatedRepoId
    } else {
        id
    }
    val builder = RemoteRepository.Builder(repositoryId, checkRepositoryLayout(layout), url.toString())
    if (proxy != null) {
        val selfProxy = proxy!!
        builder.setProxy(selfProxy)
        log.debug { "仓库 $repositoryId 已使用独立的代理配置: ${selfProxy.type}://${selfProxy.host}:${selfProxy.port}" }
    } else if (proxyConfig != null) {
        if (proxyConfig.type in (ProxyType.HTTP..ProxyType.HTTPS)) {
            builder.setProxy(proxyConfig.toAetherProxy())
            log.debug { "仓库 $repositoryId 已使用 全局/Bot 代理配置: $proxyConfig" }
        } else {
            log.debug { "仓库 $repositoryId 不支持 全局/Bot 的代理配置: `$proxyConfig` (仅支持 HTTP 和 HTTPS)" }
        }
    } else {
        log.debug { "仓库 $repositoryId 不使用代理." }
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
    private val pathSupplier: PathSupplier,
    private val initializer: AppPaths.() -> Unit = AppPaths::defaultInitializer,
    private val fileSupplier: FileSupplier,
) {
    /**
     * 数据根目录.
     *
     * 所有运行数据的存放位置.
     *
     * 提示: 结尾不带 `/`.
     */
    DATA_ROOT(fileSupplier = FileSupplier {
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

    CONFIG_APPLICATION(PathSupplier { "$DATA_ROOT/config.json" }, {
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
    CONFIG_BOT(PathSupplier { "$DATA_ROOT/bot.json" }, {
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

    constructor(pathSupplier: PathSupplier, initializer: AppPaths.() -> Unit = AppPaths::defaultInitializer) : this(
        fileSupplier = FileSupplier { File(pathSupplier.path).canonicalFile },
        pathSupplier = pathSupplier,
        initializer = initializer
    )

    constructor(fileSupplier: FileSupplier, initializer: AppPaths.() -> Unit = AppPaths::defaultInitializer) : this(
        fileSupplier = fileSupplier,
        pathSupplier = PathSupplier { fileSupplier.file.canonicalPath },
        initializer = initializer
    )

    constructor(pathSupplier: () -> String) : this(
        fileSupplier = FileSupplier { File(pathSupplier.invoke()).canonicalFile },
        pathSupplier = PathSupplier { pathSupplier.invoke() }
    )

    val file: File by fileSupplier
    val path: String by pathSupplier

    private val initialized = AtomicBoolean(false)

    @Synchronized
    fun initial() {
        if (!initialized.get()) {
            initializer()
            initialized.set(true)
        }
    }

    /**
     * 一个内部方法, 用于将 [initialized] 状态重置.
     *
     * 如果不重置该状态, 将使得单元测试无法让 AppPath 重新初始化文件.
     *
     * 警告: 该方法不应该被非测试代码调用.
     */
    @Suppress("unused")
    private fun reset() {
        log.warn {
            "初始化状态已重置: `${this.name}`, 如果在非测试环境中重置状态, 请报告该问题."
        }
        initialized.set(false)
    }

    override fun toString(): String {
        return path
    }

    object PathConst {
        const val PROP_DATA_PATH = "bot.path.data"
        const val ENV_DATA_PATH = "BOT_DATA_PATH"
    }

    private class FileSupplier(private val supplier: Supplier<File>) {
        operator fun getValue(appPaths: AppPaths, property: KProperty<*>): File = supplier.get()

        val file: File
            get() = supplier.get()
    }

    private class PathSupplier(private val supplier: Supplier<String>) {
        operator fun getValue(appPaths: AppPaths, property: KProperty<*>): String = supplier.get()

        val path: String
            get() = supplier.get()
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
