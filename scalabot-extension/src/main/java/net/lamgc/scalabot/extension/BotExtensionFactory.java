package net.lamgc.scalabot.extension;

import org.telegram.telegrambots.abilitybots.api.bot.BaseAbilityBot;
import org.telegram.telegrambots.abilitybots.api.db.DBContext;
import org.telegram.telegrambots.abilitybots.api.util.AbilityExtension;

import java.io.File;

/**
 * 该接口用于为指定的 {@link BaseAbilityBot} 创建扩展.
 *
 * <p> 由于 AbilityExtension 无法直接获取 {@link BaseAbilityBot} 的
 * 数据库对象 {@link DBContext},
 * 所以将通过该接口工厂来创建扩展对象.
 *
 * @author LamGC
 * @since 0.0.1
 */
public interface BotExtensionFactory {

    /**
     * 为给定的 {@link BaseAbilityBot} 对象创建扩展.
     *
     * <p> 如扩展无使用 {@link DBContext} 的话,
     * 也可以返回扩展单例, 因为 AbilityBot 本身并不禁止多个机器人共用一个扩展对象
     * (AbilityBot 只是调用了扩展中的方法来创建 Ability 对象).
     *
     * @param bot             机器人对象.
     * @param shareDataFolder ScalaBot App 为扩展提供的共享数据目录.
     *                        <p>路径格式为:
     *                        <pre> $DATA_ROOT/data/extensions/{GroupId}/{ArtifactId}</pre>
     *                        <b>同一个扩展包的 Factory</b> 接收到的共享数据目录<b>都是一样的</b>,
     *                        建议将数据存储在数据目录中, 便于数据的存储管理.
     * @param options         创建扩展时可用的参数.
     * @return 返回为该 Bot 对象创建的扩展对象, 如果不希望为该机器人提供扩展, 可返回 {@code null}.
     * @since 0.7.0
     */
    AbilityExtension createExtensionInstance(BaseAbilityBot bot, File shareDataFolder, BotExtensionCreateOptions options);

}
