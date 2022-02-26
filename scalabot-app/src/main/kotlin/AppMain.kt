package net.lamgc.scalabot

import io.prometheus.client.exporter.HTTPServer
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import net.lamgc.scalabot.util.registerShutdownHook
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.generics.BotSession
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import kotlin.system.exitProcess

private val log = KotlinLogging.logger { }

private val launcher = Launcher()
    .registerShutdownHook()

fun main(args: Array<String>): Unit = runBlocking {
    log.info { "ScalaBot 正在启动中..." }
    log.info { "数据目录: ${AppPaths.DATA_ROOT}" }
    log.debug { "启动参数: ${args.joinToString(prefix = "[", postfix = "]")}" }
    initialFiles()
    if (Const.config.metrics.enable) {
        startMetricsServer()
    }
    if (!launcher.launch()) {
        exitProcess(1)
    }
}

/**
 * 启动运行指标服务器.
 * 使用 Prometheus 指标格式.
 */
fun startMetricsServer() {
    val builder = HTTPServer.Builder()
        .withDaemonThreads(true)
        .withPort(Const.config.metrics.port)
        .withHostname(Const.config.metrics.bindAddress)

    val httpServer = builder
        .build()
        .registerShutdownHook()
    log.info { "运行指标服务器已启动. (Port: ${httpServer.port})" }
}

internal class Launcher : AutoCloseable {

    companion object {
        @JvmStatic
        private val log = KotlinLogging.logger { }
    }

    private val botApi = TelegramBotsApi(DefaultBotSession::class.java)
    private val botSessionMap = mutableMapOf<ScalaBot, BotSession>()

    @Synchronized
    fun launch(): Boolean {
        val botConfigs = loadBotConfig()
        if (botConfigs.isEmpty()) {
            log.warn { "尚未配置任何机器人, 请先配置机器人后再启动本程序." }
            return false
        }
        for (botConfig in botConfigs) {
            launchBot(botConfig)
        }
        return true
    }

    private fun launchBot(botConfig: BotConfig) {
        if (!botConfig.enabled) {
            log.debug { "机器人 `${botConfig.account.name}` 已禁用, 跳过启动." }
            return
        }
        log.info { "正在启动机器人 `${botConfig.account.name}`..." }
        val botOption = DefaultBotOptions().apply {
            val proxyConfig =
                if (botConfig.proxy != null && botConfig.proxy.type != DefaultBotOptions.ProxyType.NO_PROXY) {
                    botConfig.proxy
                } else if (Const.config.proxy.type != DefaultBotOptions.ProxyType.NO_PROXY) {
                    Const.config.proxy
                } else {
                    null
                }
            if (proxyConfig != null) {
                proxyType = proxyConfig.type
                proxyHost = Const.config.proxy.host
                proxyPort = Const.config.proxy.port
                log.debug { "机器人 `${botConfig.account.name}` 已启用代理配置: $proxyConfig" }
            }

            if (botConfig.baseApiUrl != null) {
                baseUrl = botConfig.baseApiUrl
            }
        }
        val account = botConfig.account

        val remoteRepositories = Const.config.mavenRepositories
            .map(MavenRepositoryConfig::toRemoteRepository)
            .toMutableList().apply {
                add(MavenRepositoryExtensionFinder.getMavenCentralRepository(proxy = Const.config.proxy.toAetherProxy()))
            }.toList()
        val extensionPackageFinders = setOf(
            MavenRepositoryExtensionFinder(
                remoteRepositories = remoteRepositories,
                proxy = Const.config.proxy.toAetherProxy()
            )
        )

        val bot = ScalaBot(
            account.name,
            account.token,
            account.creatorId,
            BotDBMaker.getBotMaker(account),
            botOption,
            botConfig.extensions,
            extensionPackageFinders,
            botConfig.disableBuiltInAbility
        )
        botSessionMap[bot] = botApi.registerBot(bot)
        log.info { "机器人 `${bot.botUsername}` 已启动." }
    }

    @Synchronized
    override fun close() {
        botSessionMap.forEach {
            log.info { "正在关闭机器人 `${it.key.botUsername}` ..." }
            it.value.stop()
            log.info { "已关闭机器人 `${it.key.botUsername}`." }
        }
    }

}

