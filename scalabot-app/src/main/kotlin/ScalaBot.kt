package net.lamgc.scalabot

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import mu.KotlinLogging
import org.eclipse.aether.artifact.Artifact
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.objects.Ability
import org.telegram.abilitybots.api.toggle.BareboneToggle
import org.telegram.abilitybots.api.toggle.DefaultToggle
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.api.methods.commands.DeleteMyCommands
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand

/**
 * 可扩展 Bot.
 * @property creatorId 机器人所有人的 Telegram 用户 Id. 可通过联系部分机器人来获取该信息.
 * (e.g. [@userinfobot](http://t.me/userinfobot))
 * @param db 机器人数据库对象. 用于状态机等用途.
 * @param options AbilityBot 设置对象.
 * @property extensions 扩展坐标集合.
 */
internal class ScalaBot(
    db: DBContext,
    options: DefaultBotOptions,
    extensionFinders: Set<ExtensionPackageFinder>,
    botConfig: BotConfig,
    private val creatorId: Long = botConfig.account.creatorId,
    val accountId: Long = botConfig.account.id,
    val extensions: Set<Artifact> = botConfig.extensions
) :
    AbilityBot(
        botConfig.account.token,
        botConfig.account.name,
        db,
        if (botConfig.disableBuiltInAbility)
            BareboneToggle()
        else
            DefaultToggle(),
        options
    ) {

    private val extensionLoader = ExtensionLoader(
        bot = this,
        extensionFinders = extensionFinders
    )

    init {
        log.info { "[Bot $botUsername] 正在加载扩展..." }
        val extensionEntries = extensionLoader.getExtensions()
        for (entry in extensionEntries) {
            addExtension(entry.extension)
            log.debug {
                "[Bot $botUsername] 扩展包 `${entry.extensionArtifact}` 中的扩展 `${entry.extension::class.qualifiedName}` " +
                        "(由工厂类 `${entry.factoryClass.name}` 创建) 已注册."
            }
        }
        log.info { "[Bot $botUsername] 扩展加载完成." }
    }

    override fun creatorId(): Long = creatorId

    override fun onUpdateReceived(update: Update?) {
        botUpdateCounter.labels(botUsername).inc()
        botUpdateGauge.labels(botUsername).inc()

        val timer = updateProcessTime.labels(botUsername).startTimer()
        try {
            super.onUpdateReceived(update)
        } catch (e: Exception) {
            exceptionHandlingCounter.labels(botUsername).inc()
            throw e
        } finally {
            timer.observeDuration()
            botUpdateGauge.labels(botUsername).dec()
        }
    }

    /**
     * 更新 Telegram Bot 的命令列表.
     *
     * 本方法将根据已注册的 [Ability] 更新 Telegram 中机器人的命令列表.
     *
     * 调用本方法前, 必须先调用一次 [registerAbilities], 否则无法获取 Ability 信息.
     * @return 更新成功返回 `true`.
     */
    fun updateCommandList(): Boolean {
        if (abilities() == null) {
            throw IllegalStateException("Abilities has not been initialized.")
        }

        val botCommands = abilities().values.map {
            val abilityInfo = if (it.info() == null || it.info().trim().isEmpty()) {
                log.warn { "[Bot $botUsername] Ability `${it.name()}` 没有说明信息." }
                "(The command has no description)"
            } else {
                log.debug { "[Bot $botUsername] Ability `${it.name()}` info `${it.info()}`" }
                it.info().trim()
            }
            BotCommand(it.name(), abilityInfo)
        }
        val setMyCommands = SetMyCommands()
        setMyCommands.commands = botCommands
        return execute(DeleteMyCommands()) && execute(setMyCommands)
    }

    override fun onRegister() {
        super.onRegister()
        onlineBotGauge.inc()
    }

    override fun onClosing() {
        super.onClosing()
        onlineBotGauge.dec()
    }

    companion object {
        @JvmStatic
        private val log = KotlinLogging.logger { }

        // ------------- Metrics -------------

        @JvmStatic
        private val botUpdateCounter = Counter.build()
            .name("updates_total")
            .help("Total number of updates received by all bots.")
            .labelNames("bot_name")
            .subsystem("telegrambots")
            .register()

        @JvmStatic
        private val botUpdateGauge = Gauge.build()
            .name("updates_in_progress")
            .help("Number of updates in process by all bots.")
            .labelNames("bot_name")
            .subsystem("telegrambots")
            .register()

        @JvmStatic
        private val onlineBotGauge = Gauge.build()
            .name("bots_online")
            .help("Number of bots Online.")
            .subsystem("telegrambots")
            .register()

        @JvmStatic
        private val updateProcessTime = Summary.build()
            .name("update_process_duration_seconds")
            .help(
                "Time to process update. (This indicator includes the pre-processing of update by TelegrammBots, " +
                        "so it may be different from the actual execution time of ability. " +
                        "It is not recommended to use it as the accurate execution time of ability)"
            )
            .labelNames("bot_name")
            .subsystem("telegrambots")
            .register()

        @JvmStatic
        private val exceptionHandlingCounter = Counter.build()
            .name("updates_exception_handling")
            .help("Number of exceptions during processing.")
            .labelNames("bot_name")
            .subsystem("telegrambots")
            .register()
    }
}
