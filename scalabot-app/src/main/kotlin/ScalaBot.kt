package net.lamgc.scalabot

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import mu.KotlinLogging
import org.eclipse.aether.artifact.Artifact
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.toggle.BareboneToggle
import org.telegram.abilitybots.api.toggle.DefaultToggle
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.api.objects.Update

/**
 * 可扩展 Bot.
 * @param name 机器人名称. 建议设为机器人用户名.
 * @param token 机器人 API 令牌.
 * @property creatorId 机器人所有人的 Telegram 用户 Id. 可通过联系部分机器人来获取该信息.
 * (e.g. [@userinfobot](http://t.me/userinfobot))
 * @param db 机器人数据库对象. 用于状态机等用途.
 * @param options AbilityBot 设置对象.
 * @property extensions 扩展坐标集合.
 * @param disableBuiltInAbility 是否禁用 [AbilityBot] 内置命令.
 */
internal class ScalaBot(
    name: String,
    token: String,
    private val creatorId: Long,
    db: DBContext,
    options: DefaultBotOptions,
    val extensions: Set<Artifact>,
    disableBuiltInAbility: Boolean
) :
    AbilityBot(token, name, db, if (disableBuiltInAbility) BareboneToggle() else DefaultToggle(), options) {

    private val extensionLoader = ExtensionLoader(this)

    init {
        val extensionEntries = extensionLoader.getExtensions()
        for (entry in extensionEntries) {
            addExtension(entry.extension)
            log.debug {
                "[Bot ${botUsername}] 扩展包 `${entry.extensionArtifact}` 中的扩展 `${entry.extension::class.qualifiedName}` " +
                        "(由工厂类 `${entry.factoryClass.name}` 创建) 已注册."
            }
        }
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
