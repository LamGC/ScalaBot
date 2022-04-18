package net.lamgc.scalabot.extension.util;

import org.telegram.abilitybots.api.bot.BaseAbilityBot;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbilityBots {

    private final static Pattern botTokenPattern = Pattern.compile("([1-9]\\d+):([A-Za-z\\d_-]{35,})");

    private AbilityBots() {
    }

    /**
     * 获取 AbilityBot 的账户 Id.
     *
     * <p> 账户 Id 来自于 botToken 中, token 的格式为 "[AccountId]:[Secret]".
     * <p> 账户 Id 的真实性与 botToken 的有效性有关, 本方法并不会确保 botToken 的有效性, 一般情况下也无需考虑 Id 的有效性,
     * 如果有需要, 可尝试通过调用 {@link org.telegram.telegrambots.meta.api.methods.GetMe} 来确保 botToken 的有效性.
     *
     * @param bot 要获取账户 Id 的 AbilityBot 对象.
     * @return 返回 AbilityBot 的账户 Id.
     * @throws IllegalArgumentException 当 AbilityBot 的 botToken 格式错误时抛出该异常.
     */
    public static long getBotAccountId(BaseAbilityBot bot) {
        String botToken = bot.getBotToken();
        Matcher matcher = botTokenPattern.matcher(botToken);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid token format.");
        }
        return Long.parseLong(matcher.group(1));
    }

    /**
     * 取消某一对话的状态机.
     *
     * @param bot    AbilityBot 实例.
     * @param chatId 要删除状态机的聊天 Id.
     * @return 如果状态机存在, 则删除后返回 true, 不存在(未开启任何状态机, 即没有触发任何 Reply)则返回 false.
     */
    public static boolean cancelReplyState(BaseAbilityBot bot, long chatId) {
        Map<Long, Integer> stateMap = bot.db().getMap("user_state_replies");
        if (!stateMap.containsKey(chatId)) {
            return false;
        }
        stateMap.remove(chatId);
        return true;
    }

}
