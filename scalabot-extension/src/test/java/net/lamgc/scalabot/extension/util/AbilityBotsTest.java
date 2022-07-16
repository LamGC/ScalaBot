package net.lamgc.scalabot.extension.util;

import org.junit.jupiter.api.Test;
import org.mapdb.DBMaker;
import org.telegram.abilitybots.api.bot.AbilityBot;
import org.telegram.abilitybots.api.bot.BaseAbilityBot;
import org.telegram.abilitybots.api.db.MapDBContext;
import org.telegram.abilitybots.api.objects.*;
import org.telegram.abilitybots.api.sender.SilentSender;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AbilityBotsTest {

    public static final User USER = new User(1L, "first", false, "last", "username", null, false, false, false, false, false);
    public static final User CREATOR = new User(1337L, "creatorFirst", false, "creatorLast", "creatorUsername", null, false, false, false, false, false);

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
    void getBotAccountIdTest() {
        String expectToken = "1234567890:AAHXcNDBRZTKfyPED5Gi3PZDIKPOM6xhxwo";
        long actual = AbilityBots.getBotAccountId(new TestingAbilityBot(expectToken, "test"));
        assertEquals(1234567890, actual);

        String badTokenA = "12c34d56a7890:AAHXcNDBRZTKfyPED5Gi3PZDIKPOM6xhxwo";
        assertThrows(IllegalArgumentException.class, () ->
                AbilityBots.getBotAccountId(new TestingAbilityBot(badTokenA, "test")));

        String badTokenB = "12c34d56a7890AAHXcNDBRZTKfyPED5Gi3PZDIKPOM6xhxwo";
        assertThrows(IllegalArgumentException.class, () ->
                AbilityBots.getBotAccountId(new TestingAbilityBot(badTokenB, "test")));
    }

    @Test
    void cancelReplyStateTest() {
        User userA = new User(10001L, "first", false, "last", "username", null, false, false, false, false, false);
        User userB = new User(10101L, "first", false, "last", "username", null, false, false, false, false, false);
        SilentSender silent = mock(SilentSender.class);
        BaseAbilityBot bot = new TestingAbilityBot("", "", silent);
        bot.onRegister();
        bot.onUpdateReceived(mockFullUpdate(bot, userA, "/set_reply"));
        verify(silent, times(1)).send("Reply set!", userA.getId());
        bot.onUpdateReceived(mockFullUpdate(bot, userA, "reply_01"));
        verify(silent, times(1)).send("Reply 01", userA.getId());
        assertTrue(AbilityBots.cancelReplyState(bot, userA.getId()));
        bot.onUpdateReceived(mockFullUpdate(bot, userA, "reply_02"));
        verify(silent, never()).send("Reply 02", userA.getId());

        assertFalse(AbilityBots.cancelReplyState(bot, userB.getId()));

        silent = mock(SilentSender.class);
        bot = new TestingAbilityBot("", "", silent);
        bot.onRegister();

        bot.onUpdateReceived(mockFullUpdate(bot, userA, "/set_reply"));
        verify(silent, times(1)).send("Reply set!", userA.getId());
        bot.onUpdateReceived(mockFullUpdate(bot, userA, "reply_01"));
        verify(silent, times(1)).send("Reply 01", userA.getId());
        bot.onUpdateReceived(mockFullUpdate(bot, userA, "reply_02"));
        verify(silent, times(1)).send("Reply 02", userA.getId());
    }

    public static class TestingAbilityBot extends AbilityBot {

        public TestingAbilityBot(String botToken, String botUsername) {
            super(botToken, botUsername, new MapDBContext(DBMaker.heapDB().make()));
        }

        public TestingAbilityBot(String botToken, String botUsername, SilentSender silentSender) {
            super(botToken, botUsername, new MapDBContext(DBMaker.heapDB().make()));
            this.silent = silentSender;
        }

        @SuppressWarnings("unused")
        public Ability setReply() {
            return Ability.builder()
                    .name("set_reply")
                    .enableStats()
                    .locality(Locality.ALL)
                    .privacy(Privacy.PUBLIC)
                    .action(ctx -> ctx.bot().silent().send("Reply set!", ctx.chatId()))
                    .reply(ReplyFlow.builder(db())
                            .action((bot, upd) -> bot.silent().send("Reply 01", upd.getMessage().getChatId()))
                            .onlyIf(upd -> upd.hasMessage() && upd.getMessage().getText().equals("reply_01"))
                            .next(Reply.of((bot, upd) ->
                                            bot.silent().send("Reply 02", upd.getMessage().getChatId()),
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
