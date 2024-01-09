package net.lamgc.scalabot.simple;

import net.lamgc.scalabot.extension.BotExtensionCreateOptions;
import net.lamgc.scalabot.extension.BotExtensionFactory;
import org.telegram.abilitybots.api.bot.BaseAbilityBot;
import org.telegram.abilitybots.api.util.AbilityExtension;

import java.io.File;

public class SimpleExtensionFactory implements BotExtensionFactory {

    @Override
    public AbilityExtension createExtensionInstance(BaseAbilityBot bot, File shareDataFolder, BotExtensionCreateOptions options) {
        return new SayHelloExtension(bot);
    }

}
