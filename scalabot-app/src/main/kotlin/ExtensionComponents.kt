package net.lamgc.scalabot

import mu.KotlinLogging
import net.lamgc.scalabot.extension.BotExtensionFactory
import net.lamgc.scalabot.util.getPriority
import org.eclipse.aether.artifact.Artifact
import org.telegram.abilitybots.api.util.AbilityExtension
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.net.URLClassLoader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

internal class ExtensionLoader(
    private val bot: ScalaBot,
    private val extensionsDataFolder: File = AppPaths.DATA_EXTENSIONS.file,
    private val extensionsPath: File = AppPaths.EXTENSIONS.file
) {
    private val log = KotlinLogging.logger { }

    private val finders: Set<ExtensionPackageFinder> = setOf(
        FileNameFinder,
        MavenMetaInformationFinder
    )

    fun getExtensions(): Set<LoadedExtensionEntry> {
        val extensionEntries = mutableSetOf<LoadedExtensionEntry>()
        for (extensionArtifact in bot.extensions) {
            val extensionFilesMap = findExtensionPackage(extensionArtifact)
            val foundedNumber = allFoundedPackageNumber(extensionFilesMap)
            if (checkConflict(extensionFilesMap)) {
                printExtensionFileConflictError(extensionArtifact, extensionFilesMap)
                continue
            } else if (foundedNumber == 0) {
                log.warn { "[Bot ${bot.botUsername}] 找不到符合的扩展包文件: $extensionArtifact" }
                continue
            }

            val files = getExtensionFiles(filterHighPriorityResult(extensionFilesMap))
            extensionEntries.addAll(getExtensionFactories(extensionArtifact, files.first()))
        }
        return extensionEntries.toSet()
    }

    /**
     * 检查是否发生冲突.
     * @return 如果出现冲突, 返回 `true`.
     */
    private fun checkConflict(foundResult: Map<ExtensionPackageFinder, Set<FoundExtensionPackage>>): Boolean {
        if (foundResult.isEmpty()) {
            return false
        }

        val highPriorityFinders = filterHighPriorityResult(foundResult).keys
        if (highPriorityFinders.size > 1) {
            return true
        } else {
            val finder = highPriorityFinders.firstOrNull() ?: return false
            return foundResult[finder]!!.size > 1
        }
    }

    private fun filterHighPriorityResult(foundResult: Map<ExtensionPackageFinder, Set<FoundExtensionPackage>>)
            : Map<ExtensionPackageFinder, Set<FoundExtensionPackage>> {
        val finders: List<ExtensionPackageFinder> = foundResult.keys
            .filter { checkExtensionPackageFinder(it) && (foundResult[it]?.size ?: 0) != 0 }
            .sortedBy { it.getPriority() }

        val highPriority = finders.first().getPriority()
        return foundResult.filterKeys { it.getPriority() == highPriority }
    }

    private fun getExtensionFiles(packageMap: Map<ExtensionPackageFinder, Set<FoundExtensionPackage>>): Set<File> {
        val files = mutableSetOf<File>()
        for (set in packageMap.values) {
            for (foundedExtensionPackage in set) {
                files.add(foundedExtensionPackage.loadExtension())
            }
        }
        return files
    }

    private fun getExtensionFactories(extensionArtifact: Artifact, extensionFile: File): Set<LoadedExtensionEntry> {
        val extClassLoader =
            ExtensionClassLoaderCleaner.getOrCreateExtensionClassLoader(extensionArtifact, extensionFile)
        val factories = mutableSetOf<LoadedExtensionEntry>()
        for (factory in extClassLoader.serviceLoader) {
            val extension =
                factory.createExtensionInstance(bot, getExtensionDataFolder(extensionArtifact))
            factories.add(LoadedExtensionEntry(extensionArtifact, factory::class.java, extension))
        }
        return factories.toSet()
    }

    private fun allFoundedPackageNumber(filesMap: Map<ExtensionPackageFinder, Set<FoundExtensionPackage>>): Int {
        val result = mutableSetOf<URL>()
        for (files in filesMap.values) {
            for (file in files) {
                result.add(file.getRawUrl())
            }
        }
        return result.size
    }

    private fun findExtensionPackage(
        extensionArtifact: Artifact,
    ): Map<ExtensionPackageFinder, Set<FoundExtensionPackage>> {
        val result = mutableMapOf<ExtensionPackageFinder, Set<FoundExtensionPackage>>()
        val sortedFinders = finders.sortedBy { it.getPriority() }
        var highPriority = sortedFinders.first().getPriority()

        for (finder in sortedFinders) {
            if (finder.getPriority() > highPriority && result.isNotEmpty()) {
                break
            }
            highPriority = finder.getPriority()

            if (!checkExtensionPackageFinder(finder)) {
                continue
            }
            val artifacts = finder.findByArtifact(extensionArtifact, extensionsPath)
            if (artifacts.isNotEmpty()) {
                result[finder] = artifacts
            }
        }
        return result
    }

    private fun checkExtensionPackageFinder(finder: ExtensionPackageFinder): Boolean =
        finder::class.java.getDeclaredAnnotation(FinderRules::class.java) != null

    private fun printExtensionFileConflictError(
        extensionArtifact: Artifact,
        foundResult: Map<ExtensionPackageFinder, Set<FoundExtensionPackage>>
    ) {
        val errMessage = StringBuilder(
            """
            [Bot ${bot.botUsername}] 扩展包 $extensionArtifact 存在多个文件, 为防止安全问题, 已禁止加载该扩展包:
        """.trimIndent()
        ).append('\n')

        foundResult.forEach { (finder, files) ->
            errMessage.append("\t- 搜索器 `").append(finder::class.simpleName).append("`")
                .append("(Priority: ${finder.getPriority()})")
                .append(" 找到了以下扩展包: \n")
            for (file in files) {
                errMessage.append("\t\t* ")
                    .append(URLDecoder.decode(file.getRawUrl().toString(), StandardCharsets.UTF_8)).append('\n')
            }
        }
        log.error { errMessage }
    }

    private fun getExtensionDataFolder(extensionArtifact: Artifact): File {
        val dataFolder =
            File(extensionsDataFolder, "${extensionArtifact.groupId}/${extensionArtifact.artifactId}")
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        return dataFolder
    }

    data class LoadedExtensionEntry(
        val extensionArtifact: Artifact,
        val factoryClass: Class<out BotExtensionFactory>,
        val extension: AbilityExtension
    )

}

/**
 * 该类为保留措施, 尚未启用.
 */
internal object ExtensionClassLoaderCleaner {

    private val artifactMap = mutableMapOf<Artifact, ExtensionClassLoader>()
    private val usageCountMap = mutableMapOf<ExtensionClassLoader, AtomicInteger>()

    @Synchronized
    fun getOrCreateExtensionClassLoader(extensionArtifact: Artifact, extensionFile: File): ExtensionClassLoader {
        return if (!artifactMap.containsKey(extensionArtifact)) {
            val newClassLoader = ExtensionClassLoader(extensionFile)
            artifactMap[extensionArtifact] = newClassLoader
            usageCountMap[newClassLoader] = AtomicInteger(1)
            newClassLoader
        } else {
            artifactMap[extensionArtifact]!!
        }
    }

    @Synchronized
    fun releaseExtensionClassLoader(extensionArtifacts: Set<Artifact>) {
        for (extensionArtifact in extensionArtifacts) {
            if (!artifactMap.containsKey(extensionArtifact)) {
                throw IllegalStateException("No corresponding classloader exists.")
            }

            val classLoader = artifactMap[extensionArtifact]!!
            val usageCounter = usageCountMap[classLoader]!!
            if (usageCounter.decrementAndGet() == 0) {
                cleanExtensionClassLoader(extensionArtifact)
            }
        }
    }

    private fun cleanExtensionClassLoader(extensionArtifact: Artifact) {
        if (!artifactMap.containsKey(extensionArtifact)) {
            return
        }
        val classLoader = artifactMap.remove(extensionArtifact)!!
        try {
            classLoader.close()
        } finally {
            usageCountMap.remove(classLoader)
        }
    }

}

/**
 * 扩展包搜索器.
 *
 * 通过实现该接口, 可添加一种搜索扩展包的方式, 无论其来源.
 */
internal interface ExtensionPackageFinder {
    /**
     * 在指定目录中搜索指定构件坐标的扩展包文件(夹).
     * @param extensionArtifact 欲查找的扩展包构件坐标.
     * @param extensionsPath 建议的搜索路径, 如搜索器希望通过网络来获取也可以.
     * @return 返回按搜索器的方式可以找到的所有与构件坐标有关的扩展包路径.
     */
    fun findByArtifact(extensionArtifact: Artifact, extensionsPath: File): Set<FoundExtensionPackage>
}

/**
 * 已找到的扩展包信息.
 * 通过实现该接口, 以寻找远端文件的 [ExtensionPackageFinder]
 * 可以在适当的时候将扩展包下载到本地, 而无需在搜索阶段下载扩展包.
 */
internal interface FoundExtensionPackage {

    /**
     * 获取扩展包的构件坐标.
     * @return 返回扩展包的构件坐标.
     */
    fun getExtensionArtifact(): Artifact

    /**
     * 获取原始的扩展 Url.
     * @return 返回扩展包所在的 Url.
     */
    fun getRawUrl(): URL

    /**
     * 获取扩展包并返回扩展包在本地的 File 对象.
     *
     * 当调用本方法时, Finder 可以将扩展包下载到本地(如果扩展包在远端服务器的话).
     * @return 返回扩展包在本地存储时指向扩展包文件的 File 对象.
     */
    fun loadExtension(): File

    /**
     *
     */
    fun getExtensionPackageFinder(): ExtensionPackageFinder

}

/**
 * 已找到的扩展包文件.
 * @param artifact 扩展包构件坐标.
 * @param file 已找到的扩展包文件.
 */
internal class FileFoundExtensionPackage(
    private val artifact: Artifact,
    private val file: File,
    private val finder: ExtensionPackageFinder
) : FoundExtensionPackage {

    init {
        if (!file.exists()) {
            throw FileNotFoundException(file.canonicalPath)
        }
    }

    override fun getExtensionArtifact(): Artifact = artifact

    override fun getRawUrl(): URL = file.canonicalFile.toURI().toURL()

    override fun loadExtension(): File = file

    override fun getExtensionPackageFinder(): ExtensionPackageFinder = finder
}

/**
 * 扩展包专属的类加载器.
 *
 * 通过为每个扩展包提供专有的加载器, 可防止意外使用其他扩展的类(希望如此).
 * @param urls 扩展包资源 Url.
 */
internal class ExtensionClassLoader(vararg urls: URL) :
    URLClassLoader(urls) {

    /**
     * 指定扩展包 File 来创建 ClassLoader.
     * @param extensionFile 扩展包文件.
     */
    constructor(extensionFile: File) :
            this(URL(getUrlString(extensionFile)))

    val serviceLoader: ServiceLoader<BotExtensionFactory> = ServiceLoader.load(BotExtensionFactory::class.java, this)

    companion object {
        private fun getUrlString(extensionFile: File, defaultScheme: String = "file:///"): String {
            return when (extensionFile.extension.lowercase()) {
                "jar" -> "jar:file:///${extensionFile.canonicalPath}!/"
                else -> defaultScheme + extensionFile.canonicalPath
            }
        }
    }
}

/**
 * 搜索器规则.
 * @property priority 搜索器优先级. 优先级从 0 (最高)开始, 相同构件坐标下将使用优先级最高的搜索器所找到的文件.
 * 如无特殊需求, 建议使用 [FinderPriority] 中已定义好的优先级.
 */
annotation class FinderRules(
    val priority: Int
)

/**
 * **建议**的搜索器优先级常量.
 */
class FinderPriority private constructor() {

    companion object {
        /**
         * 本地扩展包.
         */
        const val LOCAL = 100

        /**
         * 远端扩展包.
         */
        const val REMOTE = 200

        /**
         * 替补的扩展包.
         */
        const val ALTERNATE = 500
    }

}
