package net.lamgc.scalabot

import com.google.gson.Gson
import java.util.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class BotAccountTest {

    @Test
    fun deserializerTest() {
        val accountId = abs(Random().nextInt()).toLong()
        val creatorId = abs(Random().nextInt()).toLong()
        val botAccount = Gson().fromJson(
            """
            {
                "name": "TestBot",
                "token": "${accountId}:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10",
                "creatorId": $creatorId
            }
        """.trimIndent(), BotAccount::class.java
        )
        assertEquals("TestBot", botAccount.name)
        assertEquals("${accountId}:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10", botAccount.token)
        assertEquals(accountId, botAccount.id, "Botaccount ID does not match expectations.")
        assertEquals(creatorId, botAccount.creatorId)
    }

}

