package net.lamgc.scalabot

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import mu.KotlinLogging
import net.lamgc.scalabot.util.ArtifactSerializer
import net.lamgc.scalabot.util.ProxyTypeSerializer
import org.eclipse.aether.artifact.Artifact
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

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
)

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
    val disableBuiltInAbility: Boolean = true,
    /*
     * 使用构件坐标来选择机器人所使用的扩展包.
     * 这么做的原因是我暂时没找到一个合适的方法来让开发者方便地设定自己的扩展 Id,
     * 而构件坐标(POM Reference 或者叫 GAV 坐标)是开发者创建 Maven/Gradle 项目所一定会设置的,
     * 所以就直接用了. :P
     */
    val extensions: Set<Artifact>,
    val proxy: ProxyConfig? = null,
    val baseApiUrl: String? = null
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
)

/**
 * ScalaBot App 配置.
 *
 * App 配置信息与 BotConfig 分开, 分别存储在各自单独的文件中.
 * @property proxy Telegram API 代理配置.
 */
internal data class AppConfig(
    val proxy: ProxyConfig = ProxyConfig(),
)

/**
 * 需要用到的路径.
 */
internal enum class AppPaths(
    private val pathSupplier: () -> String,
    private val initializer: AppPaths.() -> Unit = AppPaths::defaultInitializer
) {
    DEFAULT_CONFIG_APPLICATION({ "./config.json" }, {
        if (!file.exists()) {
            file.bufferedWriter(StandardCharsets.UTF_8).use {
                GsonConst.botConfigGson.toJson(AppConfig(), it)
            }
        }
    }),
    DEFAULT_CONFIG_BOT({ "./bot.json" }, {
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
    DATA_DB({ "./data/db/" }),
    DATA_LOGS({ "./data/logs/" }),
    EXTENSIONS({ "./extensions/" }),
    DATA_EXTENSIONS({ "./data/extensions/" }),
    TEMP({ "./tmp/" })
    ;

    val file: File
        get() = File(pathSupplier.invoke())
    val path: String
        get() = pathSupplier.invoke()

    private val initialized = AtomicBoolean(false)

    @Synchronized
    fun initial() {
        if (!initialized.get()) {
            initializer(this)
            initialized.set(true)
        }
    }
}

internal object Const {
    val config = loadAppConfig()
}

private fun AppPaths.defaultInitializer() {
    if (!file.exists()) {
        val result = if (path.endsWith("/")) {
            file.mkdirs()
        } else {
            file.createNewFile()
        }
        if (!result) {
            log.warn { "初始化文件(夹)失败: $path" }
        }
    }
}

internal fun initialFiles() {
    for (path in AppPaths.values()) {
        path.initial()
    }
}

private object GsonConst {
    val baseGson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    val appConfigGson: Gson = baseGson.newBuilder()
        .registerTypeAdapter(DefaultBotOptions.ProxyType::class.java, ProxyTypeSerializer)
        .create()

    val botConfigGson: Gson = baseGson.newBuilder()
        .registerTypeAdapter(DefaultBotOptions.ProxyType::class.java, ProxyTypeSerializer)
        .registerTypeAdapter(Artifact::class.java, ArtifactSerializer)
        .create()
}

internal fun loadAppConfig(configFile: File = AppPaths.DEFAULT_CONFIG_APPLICATION.file): AppConfig {
    configFile.bufferedReader(StandardCharsets.UTF_8).use {
        return GsonConst.appConfigGson.fromJson(it, AppConfig::class.java)!!
    }
}

internal fun loadBotConfig(botConfigFile: File = AppPaths.DEFAULT_CONFIG_BOT.file): Set<BotConfig> {
    botConfigFile.bufferedReader(StandardCharsets.UTF_8).use {
        return GsonConst.botConfigGson.fromJson(it, object : TypeToken<Set<BotConfig>>() {}.type)!!
    }
}
