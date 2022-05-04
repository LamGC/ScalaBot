package net.lamgc.scalabot

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class BotAccountTest {

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

internal class AppPathsTest {

    @Test
    fun `Data root path priority`() {
        System.setProperty("bot.path.data", "A")

        assertEquals("A", AppPaths.DATA_ROOT.file.path, "`DATA_ROOT`没有优先返回 Property 的值.")
        System.getProperties().remove("bot.path.data")
        if (System.getenv("BOT_DATA_PATH") != null) {
            assertEquals(
                System.getenv("BOT_DATA_PATH"), AppPaths.DATA_ROOT.file.path,
                "`DATA_ROOT`没有返回 env 的值."
            )
        } else {
            assertEquals(
                System.getProperty("user.dir"), AppPaths.DATA_ROOT.file.path,
                "`DATA_ROOT`没有返回 `user.dir` 的值."
            )
            val userDir = System.getProperty("user.dir")
            System.getProperties().remove("user.dir")
            assertEquals(".", AppPaths.DATA_ROOT.file.path, "`DATA_ROOT`没有返回 `.`(当前目录).")
            System.setProperty("user.dir", userDir)
            assertNotNull(System.getProperty("user.dir"), "环境还原失败!")
        }
    }

    @Test
    fun `default initializer`(@TempDir testDir: File) {
        val defaultInitializerMethod = Class.forName("net.lamgc.scalabot.AppConfigsKt")
            .getDeclaredMethod("defaultInitializer", AppPaths::class.java)
            .apply { isAccessible = true }

        val dirPath = "${testDir.canonicalPath}/directory/"
        val dirFile = File(dirPath)
        mockk<AppPaths> {
            every { file }.returns(File(dirPath))
            every { path }.returns(dirPath)
            every { initial() }.answers {
                defaultInitializerMethod.invoke(null, this@mockk)
            }
        }.initial()
        assertTrue(dirFile.exists() && dirFile.isDirectory, "默认初始器未正常初始化【文件夹】.")

        File(testDir, "test.txt").apply {
            mockk<AppPaths> {
                every { file }.returns(this@apply)
                every { path }.returns(this@apply.canonicalPath)
                every { initial() }.answers {
                    defaultInitializerMethod.invoke(null, this@mockk)
                }
            }.initial()
            assertTrue(this@apply.exists() && this@apply.isFile, "默认初始器未正常初始化【文件】.")
        }

        val alreadyExistsFile = File("${testDir.canonicalPath}/alreadyExists.txt").apply {
            if (!exists()) {
                createNewFile()
            }
        }
        assertTrue(alreadyExistsFile.exists(), "文件状态与预期不符.")
        mockk<File> {
            every { exists() }.returns(true)
            every { canonicalPath }.answers { alreadyExistsFile.canonicalPath }
            every { createNewFile() }.answers { alreadyExistsFile.createNewFile() }
            every { mkdirs() }.answers { alreadyExistsFile.mkdirs() }
            every { mkdir() }.answers { alreadyExistsFile.mkdir() }
        }.apply {
            mockk<AppPaths> {
                every { file }.returns(this@apply)
                every { path }.returns(this@apply.canonicalPath)
                every { initial() }.answers {
                    defaultInitializerMethod.invoke(null, this@mockk)
                }
            }.initial()
            verify(exactly = 0) { createNewFile() }
            verify(exactly = 0) { mkdir() }
            verify(exactly = 0) { mkdirs() }
        }

        defaultInitializerMethod.isAccessible = false
    }

}

