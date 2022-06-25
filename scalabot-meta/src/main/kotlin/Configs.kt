package net.lamgc.scalabot.config

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.Proxy
import org.telegram.telegrambots.meta.ApiConstants
import java.net.URL

/**
 * 机器人帐号信息.
 * @property name 机器人名称, 建议与实际设定的名称相同.
 * @property token 机器人 API Token.
 * @property creatorId 机器人创建者, 管理机器人需要使用该信息.
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

/**
 * 机器人配置.
 * @property account 机器人帐号信息, 用于访问 API.
 * @property disableBuiltInAbility 是否禁用 AbilityBot 自带命令.
 * @property extensions 该机器人启用的扩展.
 * @property proxy 为该机器人单独设置的代理配置, 如无设置, 则使用 AppConfig 中的代理配置.
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
    val baseApiUrl: String = ApiConstants.BASE_URL
)

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
    val port: Int = 1080
)

data class MetricsConfig(
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
