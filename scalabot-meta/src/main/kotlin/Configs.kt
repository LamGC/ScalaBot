package net.lamgc.scalabot.config

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.Proxy
import org.telegram.telegrambots.meta.TelegramUrl
import java.net.URI
import java.net.URL

/**
 * 机器人帐号信息.
 * @property name 机器人名称, 建议与实际设定的名称相同.
 * @property token 机器人 API Token.
 * @property creatorId 机器人创建者, 管理机器人需要使用该信息.
 * @property id 机器人账号 ID.
 */
data class BotAccount(
    val name: String,
    val token: String,
    val creatorId: Long
) {

    val id
        // 不要想着每次获取都要从 token 里取出有性能损耗.
        // 由于 Gson 解析方式, 如果不这么做, 会出现 token 设置前 id 初始化完成, 就只有"0"了,
        // 虽然能过单元测试, 但实际使用过程是不能正常用的.
        get() = token.substringBefore(":").toLong()
}

val defaultTelegramApiUrl: String = URL(
    TelegramUrl.DEFAULT_URL.schema,
    TelegramUrl.DEFAULT_URL.host,
    TelegramUrl.DEFAULT_URL.port,
    "/"
).toExternalForm()

/**
 * 机器人配置.
 *
 * 使用 Gson 解析时, 请添加以下类型适配器:
 * - [net.lamgc.scalabot.config.serializer.ProxyTypeSerializer]
 * - [net.lamgc.scalabot.config.serializer.BotConfigSerializer]
 * - [net.lamgc.scalabot.config.serializer.BotAccountSerializer]
 * - [net.lamgc.scalabot.config.serializer.ArtifactSerializer]
 *
 * @property enabled 是否启用机器人.
 * @property account 机器人帐号信息, 用于访问 API.
 * @property disableBuiltInAbility 是否禁用 AbilityBot 自带命令.
 * @property autoUpdateCommandList 是否自动更新机器人在 Telegram 的命令列表.
 * @property extensions 该机器人启用的扩展.
 * @property proxy 为该机器人单独设置的代理配置, 如无设置, 则使用 AppConfig 中的代理配置.
 * @property baseApiUrl 机器人所使用的 API 地址, 适用于自建 Telegram Bot API 端点.
 */
data class BotConfig(
    val enabled: Boolean = false,
    val account: BotAccount,
    val disableBuiltInAbility: Boolean = false,
    val autoUpdateCommandList: Boolean = false,
    /*
     * 使用构件坐标来选择机器人所使用的扩展包.
     * 这么做的原因是我暂时没找到一个合适的方法来让开发者方便地设定自己的扩展 Id,
     * 而构件坐标(POM Reference 或者叫 GAV 坐标)是开发者创建 Maven/Gradle 项目时一定会设置的,
     * 所以就直接用了. :P
     */
    val extensions: Set<Artifact> = emptySet(),
    val proxy: ProxyConfig = ProxyConfig(type = ProxyType.NO_PROXY),
    val baseApiUrl: String = defaultTelegramApiUrl
) {
    fun getBaseApiTelegramUrl(): TelegramUrl {
        if (this.baseApiUrl == defaultTelegramApiUrl) {
            return TelegramUrl.DEFAULT_URL
        } else {
            URI.create(baseApiUrl).let {
                return TelegramUrl.builder()
                    .host(it.host)
                    .port(it.port)
                    .schema(it.scheme)
                    .build()
            }
        }
    }
}

/**
 * 代理类型.
 */
enum class ProxyType {
    NO_PROXY,
    HTTP,
    HTTPS,
    SOCKS4,
    SOCKS5
}

/**
 * 代理配置.
 * @property type 代理类型.
 * @property host 代理服务端地址.
 * @property port 代理服务端端口.
 */
data class ProxyConfig(
    val type: ProxyType = ProxyType.NO_PROXY,
    val host: String = "127.0.0.1",
    val port: Int = 1080,
) {
    override fun toString(): String {
        return if (type != ProxyType.NO_PROXY) {
            "$type://$host:$port"
        } else {
            "NO_PROXY"
        }
    }
}

/**
 * ScalaBot 的运行指标公开配置.
 *
 * ScalaBot 内置了用于公开运行指标的服务端,
 * 该指标遵循 Prometheus 的标准, 可以通过 Prometheus 的工具来查看.
 *
 * @property enable 是否启用运行指标服务端.
 * @property port 运行指标服务端的端口.
 * @property bindAddress 运行指标服务端的绑定地址, 绑定后只有该地址可以访问.
 * @property authenticator 运行指标服务端的 HTTP 认证配置.
 */
data class MetricsConfig(
    val enable: Boolean = false,
    val port: Int = 9386,
    val bindAddress: String? = "0.0.0.0",
    val authenticator: UsernameAuthenticator? = null
)

/**
 * Maven 远端仓库配置.
 * @property id 远端仓库 ID, 如果该属性未配置 (null), 那么运行时将会自动分配一个 Id.
 * @property url 仓库地址.
 * @property proxy 访问仓库所使用的代理, 仅支持 http/https 代理.
 * @property layout 仓库布局版本, Maven 2 及以上使用 `default`, Maven 1 使用 `legacy`.
 * @property enableReleases 是否在该远端仓库获取发布版本.
 * @property enableSnapshots 是否在该远端仓库获取快照版本.
 * @property authentication 访问该远端仓库所使用的认证配置.
 */
data class MavenRepositoryConfig(
    val id: String? = null,
    val url: URL,
    val proxy: Proxy? = null,
    val layout: String = "default",
    val enableReleases: Boolean = true,
    val enableSnapshots: Boolean = true,
    // 可能要设计个 type 来判断解析成什么类型的 Authentication.
    val authentication: Authentication? = null
)

/**
 * ScalaBot App 配置.
 *
 * App 配置信息与 BotConfig 分开, 分别存储在各自单独的文件中.
 *
 * 使用 Gson 解析时, 请添加以下类型适配器:
 * - [net.lamgc.scalabot.config.serializer.ProxyTypeSerializer]
 * - [net.lamgc.scalabot.config.serializer.MavenRepositoryConfigSerializer]
 * - [net.lamgc.scalabot.config.serializer.AuthenticationSerializer]
 * - [net.lamgc.scalabot.config.serializer.UsernameAuthenticatorSerializer]
 *
 * @property proxy Telegram API 代理配置.
 * @property metrics 运行指标数据配置. 可通过时序数据库记录运行数据.
 * @property mavenRepositories Maven 远端仓库配置.
 * @property mavenLocalRepository Maven 本地仓库路径. 相对于运行目录 (而不是 DATA_ROOT 目录)
 */
data class AppConfig(
    val proxy: ProxyConfig = ProxyConfig(),
    val metrics: MetricsConfig = MetricsConfig(),
    val mavenRepositories: List<MavenRepositoryConfig> = emptyList(),
    val mavenLocalRepository: String? = null
)
