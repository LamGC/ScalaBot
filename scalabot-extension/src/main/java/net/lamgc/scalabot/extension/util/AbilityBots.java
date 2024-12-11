package net.lamgc.scalabot.extension.util;

import org.telegram.telegrambots.abilitybots.api.bot.BaseAbilityBot;

import java.util.Map;

/**
 * 一些开发扩展中可以用到的工具类.
 */
public final class AbilityBots {

    private AbilityBots() {
    }

    /**
     * 取消某一对话的状态机.
     *
     * @param bot    AbilityBot 实例.
     * @param chatId 要删除状态机的聊天 Id.
     * @return 如果状态机存在, 则删除后返回 true, 不存在(未开启任何状态机, 即没有触发任何 Reply)则返回 false.
     */
    public static boolean cancelReplyState(BaseAbilityBot bot, long chatId) {
        Map<Long, Integer> stateMap = bot.getDb().getMap("user_state_replies");
        if (!stateMap.containsKey(chatId)) {
            return false;
        }
        stateMap.remove(chatId);
        return true;
    }

}
