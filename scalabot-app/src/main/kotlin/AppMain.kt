package net.lamgc.scalabot

import com.google.gson.JsonParseException
import io.prometheus.client.exporter.HTTPServer
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import net.lamgc.scalabot.config.*
import net.lamgc.scalabot.util.registerShutdownHook
import okhttp3.OkHttpClient
import org.eclipse.aether.repository.LocalRepository
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.BotSession
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.system.exitProcess

private val log = KotlinLogging.logger { }

fun main(args: Array<String>): Unit = runBlocking {
    log.info { "ScalaBot 正在启动中..." }
    log.info { "数据目录: ${AppPaths.DATA_ROOT}" }
    log.debug { "Kotlin: ${KotlinVersion.CURRENT}, JVM: ${Runtime.version()}" }
    log.debug { "启动参数: ${args.joinToString(prefix = "[", postfix = "]")}" }
    if (initialFiles()) {
        exitProcess(1)
    }

    val launcher = Launcher()
        .registerShutdownHook()
    startMetricsServer()?.registerShutdownHook()
    if (!launcher.launch()) {
        exitProcess(1)
    }
}

/**
 * 启动运行指标服务器.
 * 使用 Prometheus 指标格式.
 */
internal fun startMetricsServer(config: MetricsConfig = Const.config.metrics): HTTPServer? {
    if (!config.enable) {
        log.debug { "运行指标服务器已禁用." }
        return null
    }

    val builder = HTTPServer.Builder()
        .withDaemonThreads(true)
        .withAuthenticator(config.authenticator)
        .withPort(config.port)
        .withHostname(config.bindAddress)

    val httpServer = builder
        .build()
    log.info { "运行指标服务器已启动. (Port: ${httpServer.port})" }
    return httpServer
}

internal class Launcher(
    private val config: AppConfig = Const.config,
    private val configFile: File = AppPaths.CONFIG_APPLICATION.file,
) : AutoCloseable {

    companion object {
        @JvmStatic
        private val log = KotlinLogging.logger { }
    }

    private val botApi = TelegramBotsLongPollingApplication()
    private val botSessionMap = mutableMapOf<ScalaBot, BotSession>()
    private val mavenLocalRepository = getMavenLocalRepository()

    private fun getMavenLocalRepository(): LocalRepository {
        val localPath =
            if (config.mavenLocalRepository != null && config.mavenLocalRepository!!.isNotEmpty()) {
                val repoPath = configFile.toPath().resolve(config.mavenLocalRepository!!).apply {
                    if (!exists()) {
                        if (!parent.isWritable() || !parent.isReadable()) {
                            throw IOException("Unable to read and write the directory where Maven repository is located.")
                        }
                        if (System.getProperty("os.name").lowercase().startsWith("windows")) {
                            createDirectories()
                        } else {
                            val fileAttributes = setOf(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.GROUP_READ,
                                PosixFilePermission.GROUP_WRITE,
                                PosixFilePermission.OTHERS_READ,
                            )
                            createDirectories(PosixFilePermissions.asFileAttribute(fileAttributes))
                        }
                    }
                }
                    .toRealPath()
                    .toFile()
                repoPath
            } else {
                File("${System.getProperty("user.home")}/.m2/repository")
            }
        if (!localPath.exists()) {
            localPath.mkdirs()
        }
        return LocalRepository(localPath)
    }

    @Synchronized
    fun launch(): Boolean {
        val botConfigs = loadBotConfigJson() ?: return false
        if (botConfigs.isEmpty) {
            log.warn { "尚未配置任何机器人, 请先配置机器人后再启动本程序." }
            return false
        }
        var launchedCounts = 0
        for (botConfigJson in botConfigs) {
            val botConfig = try {
                GsonConst.botConfigGson.fromJson(botConfigJson, BotConfig::class.java)
            } catch (e: JsonParseException) {
                val botName = try {
                    botConfigJson.asJsonObject.get("account")?.asJsonObject?.get("name")?.asString ?: "Unknown"
                } catch (e: Exception) {
                    "Unknown"
                }
                log.error(e) { "机器人 `$botName` 配置有误, 跳过该机器人的启动." }
                continue
            }

            try {
                launchBot(botConfig)
                launchedCounts++
            } catch (e: Exception) {
                if (e is TelegramApiRequestException && e.errorCode == 401) {
                    log.error { "机器人 `${botConfig.account.name}` 的 Bot Token 无效, 请检查配置: [${e.errorCode}] ${e.apiResponse}" }
                } else {
                    log.error(e) { "机器人 `${botConfig.account.name}` 启动时发生错误." }
                }
            }
        }

        botApi.start()
        botApi.registerShutdownHook()

        return if (launchedCounts != 0) {
            log.info { "已启动 $launchedCounts 个机器人." }
            true
        } else {
            log.warn { "未启动任何机器人, 请检查配置并至少启用一个机器人." }
            false
        }
    }

    private fun launchBot(botConfig: BotConfig) {
        if (!botConfig.enabled) {
            log.debug { "机器人 `${botConfig.account.name}` 已禁用, 跳过启动." }
            return
        }
        log.info { "正在启动机器人 `${botConfig.account.name}`..." }
        val proxyConfig =
            if (botConfig.proxy.type != ProxyType.NO_PROXY) {
                log.debug { "[Bot ${botConfig.account.name}] 使用独立代理: ${botConfig.proxy.type}" }
                botConfig.proxy
            } else if (config.proxy.type != ProxyType.NO_PROXY) {
                log.debug { "[Bot ${botConfig.account.name}] 使用全局代理: ${botConfig.proxy.type}" }
                config.proxy
            } else {
                log.debug { "[Bot ${botConfig.account.name}] 不使用代理." }
                ProxyConfig(type = ProxyType.NO_PROXY)
            }

        val okhttpClientBuilder = OkHttpClient.Builder()

        if (proxyConfig.type != ProxyType.NO_PROXY) {
            val proxyType = proxyConfig.type.toJavaProxyType()
            val proxyAddress = InetSocketAddress.createUnresolved(proxyConfig.host, proxyConfig.port)
            okhttpClientBuilder.proxy(Proxy(proxyType, proxyAddress))
        }

        val account = botConfig.account
        val telegramClient =
            OkHttpTelegramClient(okhttpClientBuilder.build(), account.token, botConfig.getBaseApiTelegramUrl())

        val remoteRepositories = config.mavenRepositories
            .map { it.toRemoteRepository(proxyConfig) }
            .toMutableList().apply {
                if (this.none {
                        it.url == MavenRepositoryExtensionFinder.MAVEN_CENTRAL_URL
                                || it.url == MavenRepositoryExtensionFinder.MAVEN_CENTRAL_URL.trimEnd('/')
                    }) {
                    add(MavenRepositoryExtensionFinder.getMavenCentralRepository(proxy = proxyConfig.toAetherProxy()))
                }
            }.toList()
        val extensionPackageFinders = setOf(
            MavenRepositoryExtensionFinder(
                localRepository = mavenLocalRepository,
                remoteRepositories = remoteRepositories,
                proxy = config.proxy.toAetherProxy()
            )
        )

        val bot = ScalaBot(
            BotDBMaker.getBotDbInstance(account),
            telegramClient,
            extensionPackageFinders,
            botConfig
        )

        val botUser = bot.telegramClient.execute(GetMe())
        log.debug { "已验证 Bot Token 有效性, Bot Username: ${botUser.userName}" }

        botSessionMap[bot] = botApi.registerBot(botConfig.account.token, bot)
        log.info { "机器人 `${bot.botUsername}` 已启动." }

        if (botConfig.autoUpdateCommandList) {
            log.debug { "[Bot ${botConfig.account.name}] 正在自动更新命令列表..." }
            try {
                val result = bot.updateCommandList()
                if (result) {
                    log.info { "[Bot ${botConfig.account.name}] 已成功更新 Bot 命令列表." }
                } else {
                    log.warn { "[Bot ${botConfig.account.name}] 自动更新 Bot 命令列表失败!" }
                }
            } catch (e: Exception) {
                log.warn(e) { "命令列表自动更新失败." }
            }
        }
    }

    @Synchronized
    override fun close() {
        botSessionMap.forEach {
            try {
                if (!it.value.isRunning) {
                    return@forEach
                }
                log.info { "正在关闭机器人 `${it.key.botUsername}` ..." }
                it.value.stop()
                log.info { "已关闭机器人 `${it.key.botUsername}`." }
            } catch (e: Exception) {
                log.error(e) { "机器人 `${it.key.botUsername}` 关闭时发生异常." }
            }
        }
    }

}

