package net.lamgc.scalabot.config

import com.google.gson.JsonObject
import com.sun.net.httpserver.BasicAuthenticator

class UsernameAuthenticator(private val username: String, private val password: String) :
    BasicAuthenticator("metrics") {
    override fun checkCredentials(username: String?, password: String?): Boolean =
        this.username == username && this.password == password

    fun toJsonObject(): JsonObject = JsonObject().apply {
        addProperty("username", username)
        addProperty("password", password)
    }

    override fun equals(other: Any?): Boolean {
        return other is UsernameAuthenticator && this.username == other.username && this.password == other.password
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + password.hashCode()
        return result
    }

}
