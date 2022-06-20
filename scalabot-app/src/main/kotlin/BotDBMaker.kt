package net.lamgc.scalabot

import com.google.common.io.Files
import mu.KotlinLogging
import net.lamgc.scalabot.util.toHexString
import org.mapdb.DB
import org.mapdb.DBException
import org.mapdb.DBMaker
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.db.MapDBContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 数据库适配器列表.
 * 应按照新到旧的顺序放置, 新的适配器应该在上面.
 * @suppress 由于本列表需要设置已弃用的适配器以保证旧版数据库的正常使用, 故忽略弃用警告.
 */
@Suppress("DEPRECATION")
private val adapters = arrayListOf<DbAdapter>(
    BotAccountIdDbAdapter, // since [v0.2.0 ~ latest)
    BotTokenDbAdapter // since [v0.0.1 ~ v0.2.0)
)
private const val FIELD_DB_VERSION = "::DB_VERSION"

internal object BotDBMaker {
    private val logger = KotlinLogging.logger { }

    fun getBotDbInstance(botAccount: BotAccount): DBContext {
        for (adapter in adapters) {
            val botDb = try {
                adapter.getBotDb(botAccount, create = false) ?: continue
            } catch (e: Exception) {
                logger.error(e) { "适配器 ${adapter::class.java} 打开数据库时发生异常." }
                continue
            }
            if (!adapter.dbVersionMatches(botDb)) {
                logger.warn {
                    "数据库版本号与适配器不符. " +
                            "(Adapter: ${adapter::class.java};(${adapter.dbVersion})," +
                            " DatabaseVer: ${adapter.getDbVersion(botDb)})"
                }
                botDb.close()
                continue
            } else {
                if (adapter != adapters[0]) {
                    logger.debug {
                        "数据库适配器不是最新的, 正在升级数据库... " +
                                "(Old: ${adapter::class.java}; New: ${adapters[0]::class.java})"
                    }
                    val db = try {
                        botDb.close()
                        val newDb = adapters[0].migrateDb(botAccount, adapter)
                        logger.debug { "数据库版本升级完成." }
                        newDb
                    } catch (e: Exception) {
                        logger.warn(e) { "Bot 数据库版本升级失败, 将继续使用旧版数据库." }
                        adapter.getBotDb(botAccount, create = false) ?: continue
                    }
                    return MapDBContext(db)
                }
                return MapDBContext(botDb)
            }
        }

        logger.debug { "没有适配器成功打开数据库, 使用最新的适配器创建数据库. (Adapter: ${adapters[0]::class.java})" }
        val newDb = adapters[0].getBotDb(botAccount, create = true)
            ?: throw IllegalStateException("No adapter is available to get the database.")
        adapters[0].setDbVersion(newDb, adapters[0].dbVersion)
        return MapDBContext(newDb)
    }

}

/**
 * 数据库适配器.
 *
 * 用于解决数据库格式更新带来的问题, 通过迁移机制, 将数据库从旧版本迁移到新版本, 或者只通过旧版本适配器访问而不迁移.
 * @param dbVersion 数据库格式版本. 格式为: `{格式标识}_{最后使用的版本号}`, 如果为最新版适配器, 则不需要填写最后使用的版本号.
 */
private abstract class DbAdapter(val dbVersion: String) {

    /**
     * 获取 Bot 专有的 [DBContext].
     * @param botAccount Bot 账号信息.
     */
    abstract fun getBotDb(botAccount: BotAccount, create: Boolean = false): DB?

    /**
     * 通过 Bot 账号信息获取数据库文件.
     */
    abstract fun getBotDbFile(botAccount: BotAccount): File

    /**
     * 将旧版数据库迁移到当前版本.
     *
     * 实现时请注意不要直接修改原数据库, 以防升级过程出错导致无法回退到旧版本.
     */
    abstract fun migrateDb(botAccount: BotAccount, oldDbAdapter: DbAdapter): DB

    /**
     * 数据库版本是否匹配.
     */
    open fun dbVersionMatches(db: DB): Boolean {
        return getDbVersion(db) == dbVersion
    }

    fun getDbVersion(db: DB): String? {
        if (!db.exists(FIELD_DB_VERSION)) {
            return null
        }
        val dbVersionField = try {
            db.atomicString(FIELD_DB_VERSION).open()
        } catch (e: DBException.WrongConfiguration) {
            return null
        }
        return dbVersionField.get()
    }

    fun setDbVersion(db: DB, version: String) {
        db.atomicString(FIELD_DB_VERSION).createOrOpen().set(version)
    }

}

/**
 * 抽象文件数据库适配器.
 *
 * 只有文件有变化的适配器.
 */
private abstract class FileDbAdapter(
    dbVersion: String,
    private val fileProvider: (BotAccount) -> File
) : DbAdapter(dbVersion) {

    @Suppress("unused")
    constructor(dbVersion: String) : this(dbVersion,
        { throw NotImplementedError("When using this constructor, the \"getBotDbFile\" method must be implemented") })

    override fun getBotDb(botAccount: BotAccount, create: Boolean): DB? {
        val dbFile = getBotDbFile(botAccount)
        if (!dbFile.exists() && !create) {
            return null
        }
        return DBMaker.fileDB(dbFile)
            .closeOnJvmShutdownWeakReference()
            .checksumStoreEnable()
            .fileChannelEnable()
            .make()
    }

    override fun getBotDbFile(botAccount: BotAccount): File = fileProvider(botAccount)

    override fun migrateDb(botAccount: BotAccount, oldDbAdapter: DbAdapter): DB {
        val oldFile = oldDbAdapter.getBotDbFile(botAccount)
        val newFile = getBotDbFile(botAccount)
        try {
            @Suppress("UnstableApiUsage")
            Files.copy(oldFile, newFile)
        } catch (e: Exception) {
            if (newFile.exists()) {
                // 删除新文件以防止异常退出后直接读取新文件.
                newFile.delete()
            }
            throw e
        }
        oldFile.delete()
        return getBotDb(botAccount)!!.apply {
            setDbVersion(this, this@FileDbAdapter.dbVersion)
        }
    }
}

/**
 * 使用 Bot Token 中的 Account Id 命名数据库文件名.
 */
private object BotAccountIdDbAdapter : FileDbAdapter("BotAccountId", { botAccount ->
    File(AppPaths.DATA_DB.file, "${botAccount.id}.db")
})

/**
 * 使用 Bot Token, 经过 Sha256 加密后得到文件名.
 *
 * **已弃用**: 由于 Token 可以重新生成, 当 Token 改变后数据库文件名也会改变, 故弃用该方法.
 */
@Deprecated(message = "由于 BotToken 可变, 故不再使用该适配器.", level = DeprecationLevel.WARNING)
private object BotTokenDbAdapter : FileDbAdapter("BotToken_v0.1.0", { botAccount ->
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    val digestBytes = digest.digest(botAccount.token.toByteArray(StandardCharsets.UTF_8))
    File(AppPaths.DATA_DB.file, "${digestBytes.toHexString()}.db")
})