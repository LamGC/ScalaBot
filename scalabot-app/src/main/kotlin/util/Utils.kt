package net.lamgc.scalabot.util

import mu.KotlinLogging
import net.lamgc.scalabot.ExtensionPackageFinder
import net.lamgc.scalabot.FinderRules
import org.eclipse.aether.artifact.Artifact
import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter

internal fun ByteArray.toHexString(): String = joinToString("") { it.toString(16) }

internal fun Artifact.equalsArtifact(that: Artifact): Boolean =
    this.groupId.equals(that.groupId) &&
            this.artifactId.equals(that.artifactId) &&
            this.version.equals(that.version) &&
            this.baseVersion.equals(that.baseVersion) &&
            this.isSnapshot == that.isSnapshot &&
            this.classifier.equals(that.classifier) &&
            this.extension.equals(that.extension) &&
            (if (this.file == null) that.file == null else this.file.equals(that.file)) &&
            this.properties.equals(that.properties)

internal fun File.deepListFiles(
    addSelf: Boolean = false,
    onlyFile: Boolean = false,
    fileFilter: FileFilter? = null,
    filenameFilter: FilenameFilter? = null
): Array<File>? {
    val files = if (fileFilter != null) {
        this.listFiles(fileFilter)
    } else if (filenameFilter != null) {
        this.listFiles(filenameFilter)
    } else {
        this.listFiles()
    } ?: return null

    val result = if (addSelf) mutableSetOf(this) else mutableSetOf()
    for (file in files) {
        if (file.isFile) {
            result.add(file)
        } else {
            if (!onlyFile) {
                result.add(file)
            }
            val subFiles = file.deepListFiles(false, onlyFile, fileFilter, filenameFilter) ?: continue
            result.addAll(subFiles)
        }
    }
    return result.toTypedArray()
}

/**
 * 从 Finder 的 [FinderRules] 注解中获取优先级.
 * @return 获取 Finder 的优先级.
 * @throws NoSuchFieldException 如果 Finder 没有添加 [FinderRules] 注解时抛出该异常.
 */
internal fun ExtensionPackageFinder.getPriority(): Int {
    val value = this::class.java.getDeclaredAnnotation(FinderRules::class.java)?.priority
        ?: throw NoSuchFieldException("Finder did not add `FinderRules` annotation")
    if (value < 0) {
        throw IllegalArgumentException("Priority cannot be lower than 0. (Class: ${this::class.java})")
    }
    return value
}

/**
 * 为 [AutoCloseable] 对象注册 Jvm Shutdown 钩子.
 * @return 返回对象本身, 方便进行链式调用.
 */
fun <T : AutoCloseable> T.registerShutdownHook(): T {
    UtilsInternal.autoCloseableSet.add(this)
    return this
}

private val log = KotlinLogging.logger { }

private object UtilsInternal {

    val autoCloseableSet = mutableSetOf<AutoCloseable>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread(this::doCloseResources, "Shutdown-AutoCloseable"))
    }

    fun doCloseResources() {
        log.debug { "Closing registered hook resources..." }
        autoCloseableSet.removeIf {
            try {
                it.close()
            } catch (e: Exception) {
                log.error(e) { "An exception occurred while closing the resource. (Resource: `$it`)" }
            }
            true
        }
        log.debug { "All registered hook resources have been closed." }
    }
}
