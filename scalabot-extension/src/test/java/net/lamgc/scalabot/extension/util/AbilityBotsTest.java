package net.lamgc.scalabot.extension.util;

import org.junit.jupiter.api.Test;
import org.mapdb.DBMaker;
import org.telegram.telegrambots.abilitybots.api.bot.AbilityBot;
import org.telegram.telegrambots.abilitybots.api.bot.BaseAbilityBot;
import org.telegram.telegrambots.abilitybots.api.db.MapDBContext;
import org.telegram.telegrambots.abilitybots.api.objects.*;
import org.telegram.telegrambots.abilitybots.api.sender.SilentSender;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class AbilityBotsTest {

    public static final User USER = User.builder()
            .userName("username")
            .id(1L)
            .firstName("first")
            .lastName("last")
            .isBot(false)
            .build();
    public static final User CREATOR = User.builder()
            .userName("creatorUsername")
            .id(1337L)
            .firstName("creatorFirst")
            .lastName("creatorLast")
            .isBot(false)
            .build();

    static Update mockFullUpdate(BaseAbilityBot bot, User user, String args) {
        bot.users().put(USER.getId(), USER);
        bot.users().put(CREATOR.getId(), CREATOR);
        bot.userIds().put(CREATOR.getUserName(), CREATOR.getId());
        bot.userIds().put(USER.getUserName(), USER.getId());

        bot.admins().add(CREATOR.getId());

        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(user);
        when(message.getText()).thenReturn(args);
        when(message.hasText()).thenReturn(true);
        when(message.isUserMessage()).thenReturn(true);
        when(message.getChatId()).thenReturn(user.getId());
        when(update.getMessage()).thenReturn(message);
        return update;
    }

    @Test
    void cancelReplyStateTest() {
        User userA = User.builder()
                .id(10001L)
                .firstName("first")
                .lastName("last")
                .userName("username")
                .isBot(false)
                .build();
        User userB = User.builder()
                .id(10101L)
                .firstName("first")
                .lastName("last")
                .userName("username")
                .isBot(false)
                .build();
        SilentSender silent = mock(SilentSender.class);
        BaseAbilityBot bot = new TestingAbilityBot("", silent);
        bot.onRegister();
        bot.consume(mockFullUpdate(bot, userA, "/set_reply"));
        verify(silent, times(1)).send("Reply set!", userA.getId());
        bot.consume(mockFullUpdate(bot, userA, "reply_01"));
        verify(silent, times(1)).send("Reply 01", userA.getId());
        assertTrue(AbilityBots.cancelReplyState(bot, userA.getId()));
        bot.consume(mockFullUpdate(bot, userA, "reply_02"));
        verify(silent, never()).send("Reply 02", userA.getId());

        assertFalse(AbilityBots.cancelReplyState(bot, userB.getId()));

        silent = mock(SilentSender.class);
        bot = new TestingAbilityBot("", silent);
        bot.onRegister();

        bot.consume(mockFullUpdate(bot, userA, "/set_reply"));
        verify(silent, times(1)).send("Reply set!", userA.getId());
        bot.consume(mockFullUpdate(bot, userA, "reply_01"));
        verify(silent, times(1)).send("Reply 01", userA.getId());
        bot.consume(mockFullUpdate(bot, userA, "reply_02"));
        verify(silent, times(1)).send("Reply 02", userA.getId());
    }

    public static class TestingAbilityBot extends AbilityBot {

        public TestingAbilityBot(String botUsername, SilentSender silentSender) {
            super(new NoOpTelegramClient(), botUsername, new MapDBContext(DBMaker.heapDB().make()));
            this.silent = silentSender;
        }

        @SuppressWarnings("unused")
        public Ability setReply() {
            return Ability.builder()
                    .name("set_reply")
                    .enableStats()
                    .locality(Locality.ALL)
                    .privacy(Privacy.PUBLIC)
                    .action(ctx -> ctx.bot().getSilent().send("Reply set!", ctx.chatId()))
                    .reply(ReplyFlow.builder(getDb())
                            .action((bot, upd) -> bot.getSilent().send("Reply 01", upd.getMessage().getChatId()))
                            .onlyIf(upd -> upd.hasMessage() && upd.getMessage().getText().equals("reply_01"))
                            .next(Reply.of((bot, upd) ->
                                            bot.getSilent().send("Reply 02", upd.getMessage().getChatId()),
                                    upd -> upd.hasMessage() && upd.getMessage().getText().equals("reply_02")))
                            .build()
                    )
                    .build();
        }

        @Override
        public long creatorId() {
            return 0;
        }
    }

}
