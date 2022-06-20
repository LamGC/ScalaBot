package net.lamgc.scalabot.config.serializer

import com.google.gson.*
import net.lamgc.scalabot.config.UsernameAuthenticator
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class UsernameAuthenticatorTest {

    @Test
    fun checkCredentialsTest() {
        val authenticator = UsernameAuthenticator("testUser", "testPassword")
        assertTrue(authenticator.checkCredentials("testUser", "testPassword"))
        assertFalse(authenticator.checkCredentials("falseUser", "testPassword"))
        assertFalse(authenticator.checkCredentials("testUser", "falsePassword"))
    }

    @Test
    fun toJsonObjectTest() {
        val authenticator = UsernameAuthenticator("testUser", "testPassword")
        val jsonObject = authenticator.toJsonObject()
        assertEquals("testUser", jsonObject["username"]?.asString)
        assertEquals("testPassword", jsonObject["password"]?.asString)
    }

    @Test
    fun equalsTest() {
        val authenticator = UsernameAuthenticator("testUser", "testPassword")
        assertEquals(authenticator, UsernameAuthenticator("testUser", "testPassword"))
        assertEquals(authenticator.hashCode(), UsernameAuthenticator("testUser", "testPassword").hashCode())
        assertNotEquals(authenticator, UsernameAuthenticator("testUser", "falsePassword"))
        assertNotEquals(authenticator.hashCode(), UsernameAuthenticator("testUser", "falsePassword").hashCode())
        assertNotEquals(authenticator, UsernameAuthenticator("falseUser", "testPassword"))
        assertNotEquals(authenticator.hashCode(), UsernameAuthenticator("falseUser", "testPassword").hashCode())
        assertNotEquals(authenticator, UsernameAuthenticator("falseUser", "falsePassword"))
        assertNotEquals(authenticator.hashCode(), UsernameAuthenticator("falseUser", "falsePassword").hashCode())
        assertFalse(authenticator.equals(null))
    }

}

class UsernameAuthenticatorSerializerTest {

    @Test
    fun serializeTest() {
        val authenticator = UsernameAuthenticator("testUser", "testPassword")
        val jsonElement = UsernameAuthenticatorSerializer.serialize(authenticator, null, null)
        assertTrue(jsonElement.isJsonObject)
        val jsonObject = jsonElement.asJsonObject
        assertEquals("testUser", jsonObject["username"]?.asString)
        assertEquals("testPassword", jsonObject["password"]?.asString)
    }

    @Test
    fun deserializeTest() {
        assertThrows<JsonParseException> {
            UsernameAuthenticatorSerializer.deserialize(JsonArray(), null, null)
        }
        assertThrows<JsonParseException> {
            UsernameAuthenticatorSerializer.deserialize(JsonPrimitive(""), null, null)
        }
        assertNull(UsernameAuthenticatorSerializer.deserialize(JsonNull.INSTANCE, null, null))
        assertThrows<JsonParseException> {
            UsernameAuthenticatorSerializer.deserialize(JsonObject().apply {
                addProperty("username", "testUser")
            }, null, null)
        }
        assertThrows<JsonParseException> {
            UsernameAuthenticatorSerializer.deserialize(JsonObject().apply {
                addProperty("username", "testUser")
                add("password", JsonArray())
            }, null, null)
        }
        assertThrows<JsonParseException> {
            UsernameAuthenticatorSerializer.deserialize(JsonObject().apply {
                addProperty("password", "testPassword")
            }, null, null)
        }
        assertThrows<JsonParseException> {
            UsernameAuthenticatorSerializer.deserialize(JsonObject().apply {
                add("username", JsonArray())
                addProperty("password", "testPassword")
            }, null, null)
        }
        assertThrows<JsonParseException> {
            UsernameAuthenticatorSerializer.deserialize(JsonObject().apply {
                addProperty("username", "")
                addProperty("password", "")
            }, null, null)
        }
        assertThrows<JsonParseException> {
            UsernameAuthenticatorSerializer.deserialize(JsonObject().apply {
                addProperty("username", "testUser")
                addProperty("password", "")
            }, null, null)
        }
        assertThrows<JsonParseException> {
            UsernameAuthenticatorSerializer.deserialize(JsonObject().apply {
                addProperty("username", "")
                addProperty("password", "testPassword")
            }, null, null)
        }

        val authenticator = UsernameAuthenticatorSerializer.deserialize(JsonObject().apply {
            addProperty("username", "testUser")
            addProperty("password", "testPassword")
        }, null, null)
        assertNotNull(authenticator)

        assertTrue(authenticator.checkCredentials("testUser", "testPassword"))
        assertFalse(authenticator.checkCredentials("falseUser", "testPassword"))
        assertFalse(authenticator.checkCredentials("testUser", "falsePassword"))
    }

}
