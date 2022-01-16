package net.lamgc.scalabot

import mu.KotlinLogging
import org.eclipse.aether.artifact.Artifact
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.toggle.BareboneToggle
import org.telegram.abilitybots.api.toggle.DefaultToggle
import org.telegram.telegrambots.bots.DefaultBotOptions

internal class ScalaBot(
    name: String,
    token: String,
    private val creatorId: Long,
    db: DBContext,
    options: DefaultBotOptions,
    val extensions: Set<Artifact>,
    disableBuiltInAbility: Boolean
) :
    AbilityBot(token, name, db, if (disableBuiltInAbility) DefaultToggle() else BareboneToggle(), options) {

    companion object {
        @JvmStatic
        private val log = KotlinLogging.logger { }
    }

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
}
