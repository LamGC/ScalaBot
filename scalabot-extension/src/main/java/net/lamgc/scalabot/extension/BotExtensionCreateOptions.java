package net.lamgc.scalabot.extension;

import net.lamgc.scalabot.config.ProxyConfig;

/**
 * BotExtension 创建参数.
 * <p>
 * 通过该类可向 {@link BotExtensionFactory} 提供更多创建 BotExtension 时可用的参数.
 */
@SuppressWarnings("unused")
public class BotExtensionCreateOptions {

    private final long botAccountId;
    private final ProxyConfig proxy;

    /**
     * 构造新的 BotExtensionCreateOptions.
     *
     * @param botAccountId 创建扩展的 Bot 账户 Id.
     * @param proxy        Bot 所使用的代理配置.
     */
    public BotExtensionCreateOptions(long botAccountId, ProxyConfig proxy) {
        this.botAccountId = botAccountId;
        this.proxy = proxy;
    }

    /**
     * 获取 Bot 使用的代理信息.
     *
     * @return 返回 Bot 中 TelegramClient 所使用的代理配置.
     */
    public ProxyConfig getProxy() {
        return proxy;
    }

    /**
     * 获取 Bot 的账户 Id.
     *
     * @return 返回 Bot 的账户 Id.
     */
    public long getBotAccountId() {
        return botAccountId;
    }
}
