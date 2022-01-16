package net.lamgc.scalabot

import net.lamgc.scalabot.util.toHaxString
import org.mapdb.DBMaker
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.db.MapDBContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object BotDBMaker {
    fun getBotMaker(botAccount: BotAccount): DBContext {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        val digestBytes = digest.digest(botAccount.token.toByteArray(StandardCharsets.UTF_8))
        val dbPath = AppPaths.DATA_DB.path + "${digestBytes.toHaxString()}.db"
        val db = DBMaker.fileDB(dbPath)
            .closeOnJvmShutdownWeakReference()
            .checksumStoreEnable()
            .fileChannelEnable()
            .make()
        return MapDBContext(db)
    }

}