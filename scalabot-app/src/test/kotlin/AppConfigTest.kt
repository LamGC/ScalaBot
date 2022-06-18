package net.lamgc.scalabot

import com.github.stefanbirkner.systemlambda.SystemLambda
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
    fun `id getter`() {
        val accountId = abs(Random().nextInt()).toLong()
        assertEquals(accountId, BotAccount("Test", "${accountId}:AAHErDroUTznQsOd_oZPJ6cQEj4Z5mGHO10", 0).id)
    }

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
        System.setProperty("bot.path.data", "fromSystemProperties")

        assertEquals("fromSystemProperties", AppPaths.DATA_ROOT.file.path, "`DATA_ROOT`没有优先返回 Property 的值.")
        System.getProperties().remove("bot.path.data")

        val expectEnvValue = "fromEnvironmentVariable"
        SystemLambda.withEnvironmentVariable("BOT_DATA_PATH", expectEnvValue).execute {
            assertEquals(
                expectEnvValue, AppPaths.DATA_ROOT.file.path,
                "`DATA_ROOT`没有优先返回 env 的值."
            )
        }

        SystemLambda.withEnvironmentVariable("BOT_DATA_PATH", null).execute {
            assertEquals(
                System.getProperty("user.dir"), AppPaths.DATA_ROOT.file.path,
                "`DATA_ROOT`没有返回 System.properties `user.dir` 的值."
            )
            val userDir = System.getProperty("user.dir")
            System.getProperties().remove("user.dir")
            assertEquals(".", AppPaths.DATA_ROOT.file.path, "`DATA_ROOT`没有返回替补值 `.`(当前目录).")
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

        mockk<File> {
            every { exists() }.returns(false)
            every { canonicalPath }.answers { alreadyExistsFile.canonicalPath }
            every { createNewFile() }.answers { false }
            every { mkdirs() }.answers { false }
            every { mkdir() }.answers { false }
        }.apply {
            mockk<AppPaths> {
                every { file }.returns(this@apply)
                every { path }.returns(this@apply.canonicalPath)
                every { initial() }.answers {
                    defaultInitializerMethod.invoke(null, this@mockk)
                }
            }.initial()
            verify(exactly = 1) { createNewFile() }
            verify(exactly = 0) { mkdir() }
            verify(exactly = 0) { mkdirs() }
        }

        defaultInitializerMethod.isAccessible = false
    }

}

