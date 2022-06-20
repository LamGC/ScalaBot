package net.lamgc.scalabot.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.math.abs

internal class BotAccountTest {

    @Test
    fun `id getter`() {
        val accountId = abs(Random().nextInt()).toLong()
        assertEquals(accountId, BotAccount("Test", "${accountId}:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10", 0).id)
    }

    @Test
    fun deserializerTest() {
        val accountId = abs(Random().nextInt()).toLong()
        val creatorId = abs(Random().nextInt()).toLong()
        val botAccountJsonObject = Gson().fromJson(
            """
            {
                "name": "TestBot",
                "token": "${accountId}:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10",
                "creatorId": $creatorId
            }
        """.trimIndent(), JsonObject::class.java
        )
        val botAccount = Gson().fromJson(botAccountJsonObject, BotAccount::class.java)
        assertEquals(botAccountJsonObject["name"].asString, botAccount.name)
        assertEquals(botAccountJsonObject["token"].asString, botAccount.token)
        assertEquals(accountId, botAccount.id, "BotAccount ID does not match expectations.")
        assertEquals(creatorId, botAccount.creatorId)
    }

}