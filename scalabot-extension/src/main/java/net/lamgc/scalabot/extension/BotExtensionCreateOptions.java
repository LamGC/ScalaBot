package net.lamgc.scalabot.extension;

import net.lamgc.scalabot.config.ProxyConfig;

/**
 * BotExtension 创建参数.
 * <p>
 * 通过该类可向 {@link BotExtensionFactory} 提供更多创建 BotExtension 时可用的参数.
 */
@SuppressWarnings("unused")
public class BotExtensionCreateOptions {

    private final ProxyConfig proxy;

    public BotExtensionCreateOptions(ProxyConfig proxy) {
        this.proxy = proxy;
    }

    public ProxyConfig getProxy() {
        return proxy;
    }
}
