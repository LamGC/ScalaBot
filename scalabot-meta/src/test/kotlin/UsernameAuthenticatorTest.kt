package net.lamgc.scalabot.config

import kotlin.test.*

internal class UsernameAuthenticatorTest {

    @Test
    fun checkCredentialsTest() {
        val authenticator = UsernameAuthenticator("testUser", "testPassword")
        assertTrue(authenticator.checkCredentials("testUser", "testPassword"))
        assertFalse(authenticator.checkCredentials("falseUser", "testPassword"))
        assertFalse(authenticator.checkCredentials("testUser", "falsePassword"))
        assertFalse(authenticator.checkCredentials("falseUser", "falsePassword"))
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
