package net.lamgc.scalabot.util

import io.mockk.*
import net.lamgc.scalabot.ExtensionPackageFinder
import net.lamgc.scalabot.FinderPriority
import net.lamgc.scalabot.FinderRules
import net.lamgc.scalabot.FoundExtensionPackage
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import kotlin.test.*

internal class UtilsKtTest {

    @Test
    fun `Extension Function - Artifact_equalsArtifact`() {
        val equalGAV = "org.example:demo:1.0.0-SNAPSHOT"
        assertTrue(DefaultArtifact(equalGAV).equalsArtifact(DefaultArtifact(equalGAV)))
        assertFalse(
            DefaultArtifact("org.example:demo:1.0.0")
                .equalsArtifact(DefaultArtifact("com.example:demo-2:1.0.0-SNAPSHOT"))
        )
    }

    @Test
    fun `bytes to hex`() {
        assertEquals("48656c6c6f20576f726c64", "Hello World".toByteArray(StandardCharsets.UTF_8).toHexString())
    }

    @Test
    fun `ExtensionPackageFinder - getPriority`() {
        open class BaseTestFinder : ExtensionPackageFinder {
            override fun findByArtifact(extensionArtifact: Artifact, extensionsPath: File): Set<FoundExtensionPackage> {
                throw IllegalStateException("Calling this class is not allowed.")
            }
        }

        @FinderRules(FinderPriority.ALTERNATE)
        class StandardTestFinder : BaseTestFinder()
        assertEquals(
            FinderPriority.ALTERNATE, StandardTestFinder().getPriority(),
            "获取到的优先值与预期不符"
        )

        @FinderRules(-1)
        class OutOfRangePriorityFinder : BaseTestFinder()
        assertThrows<IllegalArgumentException>("getPriority 方法没有对超出范围的优先值抛出异常.") {
            OutOfRangePriorityFinder().getPriority()
        }

        class NoAnnotationFinder : BaseTestFinder()
        assertThrows<NoSuchFieldException> {
            NoAnnotationFinder().getPriority()
        }

    }

    @Test
    fun `AutoCloseable shutdown hook`() {
        val utilsInternalClass = Class.forName("net.lamgc.scalabot.util.UtilsInternal")
        val utilsInternalObject = utilsInternalClass.getDeclaredField("INSTANCE").get(null)
            ?: fail("无法获取 UtilsInternal 对象.")
        val doCloseResourcesMethod = utilsInternalClass.getDeclaredMethod("doCloseResources")
            .apply {
                isAccessible = true
            }

        // 正常的运行过程.
        val mockResource = mockk<AutoCloseable> {
            justRun { close() }
        }.registerShutdownHook()
        doCloseResourcesMethod.invoke(utilsInternalObject)
        verify { mockResource.close() }

        // 异常捕获检查.
        val exceptionMockResource = mockk<AutoCloseable> {
            every { close() } throws RuntimeException("Expected exception.")
        }.registerShutdownHook()
        assertDoesNotThrow("在关闭资源时出现未捕获异常.") {
            doCloseResourcesMethod.invoke(utilsInternalObject)
        }
        verify { exceptionMockResource.close() }

        // 错误抛出检查.
        val errorMockResource = mockk<AutoCloseable> {
            every { close() } throws Error("Expected error.")
        }.registerShutdownHook()
        assertThrows<Error>("关闭资源时捕获了不该捕获的 Error.") {
            try {
                doCloseResourcesMethod.invoke(utilsInternalObject)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
        verify { errorMockResource.close() }

        @Suppress("UNCHECKED_CAST")
        val resourceSet = utilsInternalClass.getDeclaredMethod("getAutoCloseableSet").invoke(utilsInternalObject)
                as MutableSet<AutoCloseable>
        resourceSet.clear()

        val closeRef = mockk<AutoCloseable> {
            justRun { close() }
        }
        resourceSet.add(closeRef)
        assertTrue(resourceSet.contains(closeRef), "测试用资源虚引用添加失败.")
        doCloseResourcesMethod.invoke(utilsInternalObject)
        assertFalse(resourceSet.contains(closeRef), "资源虚引用未从列表中删除.")

        resourceSet.clear()
    }

    @Test
    fun `Artifact equals`() {
        val artifact = DefaultArtifact("org.example:artifact:jar:0.0.1")
        assertFalse(artifact.isSnapshot, "Release artifact is snapshot.")
        assertTrue(artifact.equalsArtifact(artifact))
        assertTrue(artifact.setFile(File(".")).equalsArtifact(artifact.setFile(File("."))))
        val snapshotArtifact = DefaultArtifact("org.example:artifact:jar:0.0.1-SNAPSHOT")
        val snapshotTimestampArtifact = DefaultArtifact("org.example:artifact:jar:0.0.1-20220605.130047-1")
        assertTrue(snapshotArtifact.isSnapshot, "SnapshotArtifact not snapshot.")
        assertNotEquals(artifact.isSnapshot, snapshotArtifact.isSnapshot)
        assertNotEquals(artifact.baseVersion, snapshotArtifact.baseVersion)
        assertFalse(artifact.equalsArtifact(snapshotArtifact))
        assertFalse(snapshotArtifact.equalsArtifact(snapshotTimestampArtifact))
        assertFalse(artifact.equalsArtifact(DefaultArtifact("org.example:artifact:0.0.2")))
        assertFalse(artifact.equalsArtifact(DefaultArtifact("org.example.test:artifact:0.0.1")))
        assertFalse(artifact.equalsArtifact(DefaultArtifact("org.example:artifact-a:0.0.1")))
        assertFalse(artifact.equalsArtifact(DefaultArtifact("org.example:artifact:war:0.0.1")))
        assertFalse(artifact.equalsArtifact(DefaultArtifact("org.example:artifact:war:javadoc:0.0.1")))
        assertFalse(artifact.equalsArtifact(DefaultArtifact("org.example:artifact:rar:source:0.0.1")))

        assertFalse(
            artifact.equalsArtifact(
                DefaultArtifact("org.example:artifact:jar:0.0.1")
                    .setFile(File("./xxx01.jar"))
            )
        )

        val artifactWithExtension = DefaultArtifact("org.example:artifact:jar:0.0.1")
        assertFalse(artifactWithExtension.equalsArtifact(DefaultArtifact("org.example:artifact:war:0.0.1")))

        assertTrue(artifact.equalsArtifact(artifact.setProperties(mapOf(Pair("a", "b"))), checkProperties = false))
        assertFalse(artifact.equalsArtifact(artifact.setProperties(mapOf(Pair("a", "b"))), checkProperties = true))
        assertTrue(
            artifact.setProperties(mapOf(Pair("a", "b")))
                .equalsArtifact(artifact.setProperties(mapOf(Pair("a", "b"))), checkProperties = true)
        )
    }

    @Test
    fun `deepListFile Test - Basics`() {
        assertNull(mockk<File> {
            every { listFiles() } returns null
        }.deepListFiles())
        assertNull(mockk<File> {
            every { listFiles(ofType(FileFilter::class)) } returns null
        }.deepListFiles(fileFilter = { true }))
        assertNull(mockk<File> {
            every { listFiles(ofType(FilenameFilter::class)) } returns null
        }.deepListFiles(filenameFilter = { _, _ -> true }))


        val listFileMock = mockk<File> {
            every { listFiles() } returns arrayOf()
            every { listFiles(ofType(FileFilter::class)) } returns arrayOf()
            every { listFiles(ofType(FilenameFilter::class)) } returns arrayOf()
        }
        assertNotNull(listFileMock.deepListFiles())
        verify(exactly = 1) { listFileMock.listFiles() }
        verify(exactly = 0) { listFileMock.listFiles(ofType(FilenameFilter::class)) }
        verify(exactly = 0) { listFileMock.listFiles(ofType(FileFilter::class)) }
        clearMocks(listFileMock, answers = false)

        assertNotNull(listFileMock.deepListFiles(filenameFilter = { _, _ -> true }))
        verify(exactly = 0) { listFileMock.listFiles() }
        verify(exactly = 1) { listFileMock.listFiles(ofType(FilenameFilter::class)) }
        verify(exactly = 0) { listFileMock.listFiles(ofType(FileFilter::class)) }
        clearMocks(listFileMock, answers = false)

        assertNotNull(listFileMock.deepListFiles(fileFilter = { true }))
        verify(exactly = 0) { listFileMock.listFiles() }
        verify(exactly = 0) { listFileMock.listFiles(ofType(FilenameFilter::class)) }
        verify(exactly = 1) { listFileMock.listFiles(ofType(FileFilter::class)) }
        clearMocks(listFileMock, answers = false)

        assertNotNull(listFileMock.deepListFiles(fileFilter = { true }, filenameFilter = { _, _ -> true }))
        verify(exactly = 0) { listFileMock.listFiles() }
        verify(exactly = 1) { listFileMock.listFiles(ofType(FileFilter::class)) }
        verify(exactly = 0) { listFileMock.listFiles(ofType(FilenameFilter::class)) }
        clearMocks(listFileMock, answers = false)

        val addSelfResult = listFileMock.deepListFiles(addSelf = true)
        assertNotNull(addSelfResult)
        assertEquals(1, addSelfResult.size)
        assertTrue(addSelfResult.contains(listFileMock))
        verify(exactly = 1) { listFileMock.listFiles() }
        verify(exactly = 0) { listFileMock.listFiles(ofType(FilenameFilter::class)) }
        verify(exactly = 0) { listFileMock.listFiles(ofType(FileFilter::class)) }

        val addSelfWithoutDirMock = createDirectory(
            "root", arrayOf(
                createDirectory(
                    "dir01", arrayOf(
                        createFile("test01")
                    )
                ),
                createDirectory(
                    "dir02", arrayOf(
                        createFile("test02")
                    )
                ),
                createDirectory(
                    "dir03", arrayOf(
                        createFile("test03")
                    )
                )
            )
        )
        val addSelfWithoutDirResult = addSelfWithoutDirMock.deepListFiles(addSelf = true, onlyFile = true)
        assertNotNull(addSelfWithoutDirResult)
        assertFalse(addSelfWithoutDirResult.isEmpty())
        assertEquals(1, addSelfWithoutDirResult.filter { it.isDirectory }.size)
        assertEquals(addSelfWithoutDirMock, addSelfWithoutDirResult.find { it.isDirectory })
    }

    @Test
    fun `deepListFile Test - Complex`() {
        val mock = createDirectory(
            "root", arrayOf(
                createFile("test"),
                createFile("test02"),
                createDirectory("dir01"),
                createDirectory("dir02")
            )
        )

        val withDirResult = mock.deepListFiles(onlyFile = false)
        assertNotNull(withDirResult)
        assertEquals(4, withDirResult.size)
        assertEquals(2, withDirResult.filter { it.isFile }.size)
        assertEquals(2, withDirResult.filter { it.isDirectory }.size)

        val withoutDirResult = mock.deepListFiles(onlyFile = true)
        assertNotNull(withoutDirResult)
        assertEquals(2, withoutDirResult.filter { it.isFile }.size)
        assertNull(withoutDirResult.find { it.isDirectory })

        val subDirFailedMock = createDirectory(
            "root", arrayOf(
                mockk(name = "dir::cannotReadableDirectory") {
                    every { isFile } returns false
                    every { isDirectory } returns true
                    every { name } returns "cannotReadableDirectory"
                    every { listFiles() } returns null
                    every { listFiles(ofType(FileFilter::class)) } returns null
                    every { listFiles(ofType(FilenameFilter::class)) } returns null
                },
                createDirectory(
                    "dir2", arrayOf(
                        createFile("test")
                    )
                )
            )
        )

        val subDirFailedWithDirResult = subDirFailedMock.deepListFiles(onlyFile = false)
        assertNotNull(subDirFailedWithDirResult)
        assertEquals(3, subDirFailedWithDirResult.size)
        assertNotNull(subDirFailedWithDirResult.find { it.isDirectory && it.name == "cannotReadableDirectory" })
        assertNotNull(subDirFailedWithDirResult.find { it.isDirectory && it.name == "dir2" })
        assertNotNull(subDirFailedWithDirResult.find { it.isFile && it.name == "test" })

        val subDirFailedWithoutDirResult = subDirFailedMock.deepListFiles(onlyFile = true)
        assertNotNull(subDirFailedWithoutDirResult)
        assertEquals(1, subDirFailedWithoutDirResult.size)
        assertEquals(0, subDirFailedWithoutDirResult.filter { it.isDirectory }.size)
        assertNotNull(subDirFailedWithoutDirResult.find { it.isFile && it.name == "test" })
        assertNull(subDirFailedWithoutDirResult.find { it.isDirectory && it.name == "cannotReadableDirectory" })
        assertNull(subDirFailedWithoutDirResult.find { it.isDirectory && it.name == "dir2" })
    }

    private fun createFile(path: String): File {
        val file = File(path)
        return mockk(name = "file::$path") {
            every { isFile } returns true
            every { isDirectory } returns false
            every { name } returns file.name
            every { listFiles() } returns null
            every { listFiles(ofType(FileFilter::class)) } returns null
            every { listFiles(ofType(FilenameFilter::class)) } returns null
        }
    }

    private fun createDirectory(path: String, subFiles: Array<File> = arrayOf()): File {
        val file = File(path)
        return mockk(name = "dir::$path") {
            every { isFile } returns false
            every { isDirectory } returns true
            every { name } returns file.name
            every { listFiles() } returns subFiles
            every { listFiles(ofType(FileFilter::class)) } answers {
                subFiles.filter { (firstArg() as FileFilter).accept(it) }.toTypedArray()
            }
            every { listFiles(ofType(FilenameFilter::class)) } answers {
                subFiles.filter { (firstArg() as FilenameFilter).accept(file.parentFile, file.name) }.toTypedArray()
            }
        }
    }

}