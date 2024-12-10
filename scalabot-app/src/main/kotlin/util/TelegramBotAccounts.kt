package net.lamgc.scalabot.util

import java.util.regex.Matcher
import java.util.regex.Pattern

object TelegramBotAccounts {

    private val botTokenPattern: Pattern = Pattern.compile("([1-9]\\d+):([A-Za-z\\d_-]{35,})")

    /**
     * 获取 AbilityBot 的账户 Id.
     *
     *
     * 账户 Id 来自于 botToken 中, token 的格式为 "{AccountId}:{Secret}".
     *
     * 账户 Id 的真实性与 botToken 的有效性有关, 本方法并不会确保 botToken 的有效性, 一般情况下也无需考虑 Id 的有效性,
     * 如果有需要, 可尝试通过调用 [org.telegram.telegrambots.meta.api.methods.GetMe] 来确保 botToken 的有效性.
     *
     * @param botToken 要获取账户 Id 的 botToken 字符串.
     * @return 返回 AbilityBot 的账户 Id.
     * @throws IllegalArgumentException 当 AbilityBot 的 botToken 格式错误时抛出该异常.
     */
    fun getBotAccountId(botToken: String): Long {
        val matcher: Matcher = botTokenPattern.matcher(botToken)
        require(matcher.matches()) { "Invalid token format." }
        return matcher.group(1).toLong()
    }

}
