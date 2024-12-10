package util

import net.lamgc.scalabot.util.TelegramBotAccounts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TelegramBotAccountsTest {

    @Test
    fun getBotAccountIdTest() {
        val expectToken = "1234567890:AAHXcNDBRZTKfyPED5Gi3PZDIKPOM6xhxwo"
        val actual: Long = TelegramBotAccounts.getBotAccountId(expectToken)
        assertEquals(1234567890, actual)

        val badTokenA = "12c34d56a7890:AAHXcNDBRZTKfyPED5Gi3PZDIKPOM6xhxwo"
        assertThrows(
            IllegalArgumentException::class.java
        ) { TelegramBotAccounts.getBotAccountId(badTokenA) }

        val badTokenB = "12c34d56a7890AAHXcNDBRZTKfyPED5Gi3PZDIKPOM6xhxwo"
        assertThrows(
            IllegalArgumentException::class.java
        ) { TelegramBotAccounts.getBotAccountId(badTokenB) }
    }

}
