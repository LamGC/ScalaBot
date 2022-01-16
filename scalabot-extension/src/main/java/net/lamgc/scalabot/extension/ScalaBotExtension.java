package net.lamgc.scalabot.extension;

import org.telegram.abilitybots.api.bot.BaseAbilityBot;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.abilitybots.api.util.AbilityExtension;

/**
 *
 */
public abstract class ScalaBotExtension implements AbilityExtension {

    /**
     * 扩展所属的机器人对象.
     *
     * <p> 不要给该属性添加 Getter, 会被当成 Ability 添加, 导致出现异常.
     */
    protected final BaseAbilityBot bot;

    public ScalaBotExtension(BaseAbilityBot bot) {
        this.bot = bot;
    }

    protected MessageSender getSender() {
        return bot.sender();
    }

    protected DBContext getDBContext() {
        return bot.db();
    }

}
