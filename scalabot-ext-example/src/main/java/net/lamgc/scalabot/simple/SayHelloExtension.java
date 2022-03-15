package net.lamgc.scalabot.simple;

import org.telegram.abilitybots.api.bot.BaseAbilityBot;
import org.telegram.abilitybots.api.objects.*;
import org.telegram.abilitybots.api.util.AbilityExtension;

public class SayHelloExtension implements AbilityExtension {

    /**
     * 扩展所属的机器人对象.
     *
     * <p> 创建 ReplyFlow 时需要使用 Bot 的 DBContext.
     */
    private final BaseAbilityBot bot;

    public SayHelloExtension(BaseAbilityBot bot) {
        this.bot = bot;
    }

    public Ability sayHello() {
        return Ability.builder()
                .name("say_hello")
                .info("Say hello to you.")
                .privacy(Privacy.PUBLIC)
                .locality(Locality.ALL)
                .action(ctx -> {
                    String msg = "Hello! " + ctx.user().getUserName() +
                            " ( " + ctx.user().getId() + " ) [ " + ctx.user().getLanguageCode() + " ]" + "\n" +
                            "Current Chat ID: " + ctx.chatId();
                    ctx.bot().silent().send(msg, ctx.chatId());
                })
                .build();
    }

    /**
     * 更具特色的 `Say hello`.
     */
    public Ability test() {
        ReplyFlow botHello = ReplyFlow.builder(bot.db())
                .action((bot, upd) -> bot.silent().send("What is u name?", upd.getMessage().getChatId()))
                .onlyIf(update -> "hello".equalsIgnoreCase(update.getMessage().getText()))
                .next(Reply.of((bot, upd) -> bot.silent()
                                .send("OK! You name is " + upd.getMessage().getText().substring("my name is ".length()), upd.getMessage().getChatId()),
                        upd -> upd.getMessage().getText().startsWith("my name is ")))
                .build();

        return Ability.builder()
                .name("hello")
                .info("Say hello!")
                .locality(Locality.ALL)
                .privacy(Privacy.PUBLIC)
                .enableStats()
                .action(ctx -> ctx.bot().silent().send("Hello!", ctx.chatId()))
                .reply(botHello)
                .build();
    }

}
