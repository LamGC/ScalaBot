package net.lamgc.scalabot

import ch.qos.logback.core.PropertyDefinerBase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import mu.KotlinLogging
import net.lamgc.scalabot.util.*
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.RepositoryPolicy
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.ApiConstants
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

private val log = KotlinLogging.logger { }

/**
 * 机器人帐号信息.
 * @property name 机器人名称, 建议与实际设定的名称相同.
 * @property token 机器人 API Token.
 * @property creatorId 机器人创建者, 管理机器人需要使用该信息.
 */
internal data class BotAccount(
    val name: String,
    val token: String,
    val creatorId: Long = -1
) {

    val id
        // 不要想着每次获取都要从 token 里取出有性能损耗.
        // 由于 Gson 解析方式, 如果不这么做, 会出现 token 设置前 id 初始化完成, 就只有"0"了,
        // 虽然能过单元测试, 但实际使用过程是不能正常用的.
        get() = token.substringBefore(":").toLong()
}

/**
 * 机器人配置.
 * @property account 机器人帐号信息, 用于访问 API.
 * @property disableBuiltInAbility 是否禁用 AbilityBot 自带命令.
 * @property extensions 该机器人启用的扩展.
 * @property proxy 为该机器人单独设置的代理配置, 如无设置, 则使用 AppConfig 中的代理配置.
 */
internal data class BotConfig(
    val enabled: Boolean = true,
    val account: BotAccount,
    val disableBuiltInAbility: Boolean = false,
    val autoUpdateCommandList: Boolean = false,
    /*
     * 使用构件坐标来选择机器人所使用的扩展包.
     * 这么做的原因是我暂时没找到一个合适的方法来让开发者方便地设定自己的扩展 Id,
     * 而构件坐标(POM Reference 或者叫 GAV 坐标)是开发者创建 Maven/Gradle 项目时一定会设置的,
     * 所以就直接用了. :P
     */
    val extensions: Set<Artifact>,
    val proxy: ProxyConfig? = ProxyConfig(),
    val baseApiUrl: String? = ApiConstants.BASE_URL
)

/**
 * 代理配置.
 * @property type 代理类型.
 * @property host 代理服务端地址.
 * @property port 代理服务端端口.
 */
internal data class ProxyConfig(
    val type: DefaultBotOptions.ProxyType = DefaultBotOptions.ProxyType.NO_PROXY,
    val host: String = "127.0.0.1",
    val port: Int = 1080
) {

    fun toAetherProxy(): Proxy? {
        return if (type == DefaultBotOptions.ProxyType.HTTP) {
            Proxy(Proxy.TYPE_HTTP, host, port)
        } else {
            null
        }
    }

}

internal data class MetricsConfig(
    val enable: Boolean = false,
    val port: Int = 9386,
    val bindAddress: String? = "0.0.0.0",
    val authenticator: UsernameAuthenticator? = null
)

/**
 * Maven 远端仓库配置.
 * @property url 仓库地址.
 * @property proxy 访问仓库所使用的代理, 仅支持 http/https 代理.
 * @property layout 仓库布局版本, Maven 2 及以上使用 `default`, Maven 1 使用 `legacy`.
 */
internal data class MavenRepositoryConfig(
    val id: String? = null,
    val url: URL,
    val proxy: Proxy? = null,
    val layout: String = "default",
    val enableReleases: Boolean = true,
    val enableSnapshots: Boolean = true,
    // 可能要设计个 type 来判断解析成什么类型的 Authentication.
    val authentication: Authentication? = null
) {

    fun toRemoteRepository(proxyConfig: ProxyConfig): RemoteRepository {
        val builder =
            RemoteRepository.Builder(id ?: createDefaultRepositoryId(), checkRepositoryLayout(layout), url.toString())
        if (proxy != null) {
            builder.setProxy(proxy)
        } else if (proxyConfig.type == DefaultBotOptions.ProxyType.HTTP) {
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

    private companion object {
        fun checkRepositoryLayout(layoutType: String): String {
            val type = layoutType.trim().lowercase()
            if (type != "default" && type != "legacy") {
                throw IllegalArgumentException("Invalid layout type (expecting 'default' or 'legacy')")
            }
            return type
        }

        private val repoNumber = AtomicInteger(1)

        fun createDefaultRepositoryId(): String {
            return "Repository-${repoNumber.getAndIncrement()}"
        }

    }
}

/**
 * ScalaBot App 配置.
 *
 * App 配置信息与 BotConfig 分开, 分别存储在各自单独的文件中.
 * @property proxy Telegram API 代理配置.
 * @property metrics 运行指标数据配置. 可通过时序数据库记录运行数据.
 * @property mavenRepositories Maven 远端仓库配置.
 * @property mavenLocalRepository Maven 本地仓库路径. 相对于运行目录 (而不是 DATA_ROOT 目录)
 */
internal data class AppConfig(
    val proxy: ProxyConfig = ProxyConfig(),
    val metrics: MetricsConfig = MetricsConfig(),
    val mavenRepositories: List<MavenRepositoryConfig> = emptyList(),
    val mavenLocalRepository: String? = null
)

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

    DEFAULT_CONFIG_APPLICATION({ "$DATA_ROOT/config.json" }, {
        if (!file.exists()) {
            file.bufferedWriter(StandardCharsets.UTF_8).use {
                GsonConst.botConfigGson.toJson(
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
    DEFAULT_CONFIG_BOT({ "$DATA_ROOT/bot.json" }, {
        if (!file.exists()) {
            file.bufferedWriter(StandardCharsets.UTF_8).use {
                GsonConst.botConfigGson.toJson(
                    setOf(
                        BotConfig(
                            enabled = false,
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

    private object PathConst {
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

internal fun initialFiles() {
    val configFilesNotInitialized = !AppPaths.DEFAULT_CONFIG_APPLICATION.file.exists()
            && !AppPaths.DEFAULT_CONFIG_BOT.file.exists()

    for (path in AppPaths.values()) {
        path.initial()
    }

    if (configFilesNotInitialized) {
        log.warn { "配置文件已初始化, 请根据需要修改配置文件后重新启动本程序." }
        exitProcess(1)
    }
}

private object GsonConst {
    val baseGson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    val appConfigGson: Gson = baseGson.newBuilder()
        .registerTypeAdapter(DefaultBotOptions.ProxyType::class.java, ProxyTypeSerializer)
        .registerTypeAdapter(MavenRepositoryConfig::class.java, MavenRepositoryConfigSerializer)
        .registerTypeAdapter(Authentication::class.java, AuthenticationSerializer)
        .registerTypeAdapter(UsernameAuthenticator::class.java, UsernameAuthenticatorSerializer)
        .create()

    val botConfigGson: Gson = baseGson.newBuilder()
        .registerTypeAdapter(DefaultBotOptions.ProxyType::class.java, ProxyTypeSerializer)
        .registerTypeAdapter(Artifact::class.java, ArtifactSerializer)
        .create()
}

internal fun loadAppConfig(configFile: File = AppPaths.DEFAULT_CONFIG_APPLICATION.file): AppConfig {
    try {
        configFile.bufferedReader(StandardCharsets.UTF_8).use {
            return GsonConst.appConfigGson.fromJson(it, AppConfig::class.java)!!
        }
    } catch (e: Exception) {
        log.error { "读取 config.json 时发生错误, 请检查配置格式是否正确." }
        throw e
    }
}

internal fun loadBotConfig(botConfigFile: File = AppPaths.DEFAULT_CONFIG_BOT.file): Set<BotConfig>? {
    try {
        botConfigFile.bufferedReader(StandardCharsets.UTF_8).use {
            return GsonConst.botConfigGson.fromJson(it, object : TypeToken<Set<BotConfig>>() {}.type)!!
        }
    } catch (e: Exception) {
        log.error(e) { "读取 Bot 配置文件 (bot.json) 时发生错误, 请检查配置格式是否正确." }
        return null
    }
}
