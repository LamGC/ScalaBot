package net.lamgc.scalabot.util

import com.google.gson.*
import com.sun.net.httpserver.BasicAuthenticator
import java.lang.reflect.Type

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

object UsernameAuthenticatorSerializer : JsonSerializer<UsernameAuthenticator>,
    JsonDeserializer<UsernameAuthenticator> {

    override fun serialize(
        src: UsernameAuthenticator,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return src.toJsonObject()
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): UsernameAuthenticator? {
        if (json.isJsonNull) {
            return null
        } else if (!json.isJsonObject) {
            throw JsonParseException("Invalid attribute value type.")
        }

        val jsonObj = json.asJsonObject

        if (jsonObj["username"]?.isJsonPrimitive != true) {
            throw JsonParseException("Invalid attribute value: username")
        } else if (jsonObj["password"]?.isJsonPrimitive != true) {
            throw JsonParseException("Invalid attribute value: password")
        }

        if (jsonObj["username"].asString.isEmpty() || jsonObj["password"].asString.isEmpty()) {
            throw JsonParseException("`username` or `password` is empty.")
        }
        return UsernameAuthenticator(jsonObj["username"].asString, jsonObj["password"].asString)
    }

}
