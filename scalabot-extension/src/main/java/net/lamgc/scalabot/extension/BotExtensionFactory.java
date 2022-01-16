package net.lamgc.scalabot.extension;

import org.telegram.abilitybots.api.bot.BaseAbilityBot;
import org.telegram.abilitybots.api.util.AbilityExtension;

import java.io.File;

/**
 * 该接口用于为指定的 {@link BaseAbilityBot} 创建扩展.
 *
 * <p> 由于 AbilityExtension 无法直接获取 {@link BaseAbilityBot} 所属的 {@link org.telegram.abilitybots.api.db.DBContext} 对象,
 * 所以将通过该接口工厂来创建扩展对象.
 *
 * @author LamGC
 */
public interface BotExtensionFactory {

    /**
     * 为给定的 {@link BaseAbilityBot} 对象创建扩展.
     *
     * <p> 如扩展无使用 {@link org.telegram.abilitybots.api.db.DBContext} 的话,
     * 也可以返回扩展单例, 因为 AbilityBot 本身并不禁止多个机器人共用一个扩展对象
     * (因为 AbilityBot 只是调用了扩展中的方法来创建了功能对象).
     *
     * @param bot             机器人对象.
     * @param shareDataFolder ScalaBot App 为扩展提供的数据目录, 建议存储在数据目录中, 便于数据的存储管理.
     * @return 返回为该 Bot 对象创建的扩展对象.
     */
    AbilityExtension createExtensionInstance(BaseAbilityBot bot, File shareDataFolder);

}
