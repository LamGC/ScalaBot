package net.lamgc.scalabot

import mu.KotlinLogging
import net.lamgc.scalabot.extension.BotExtensionFactory
import net.lamgc.scalabot.util.deepListFiles
import net.lamgc.scalabot.util.equalsArtifact
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.jdom2.Document
import org.jdom2.filter.Filters
import org.jdom2.input.SAXBuilder
import org.jdom2.xpath.XPathFactory
import org.telegram.abilitybots.api.util.AbilityExtension
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarInputStream

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
            val extensionFilesMap = findExtensionPackageFile(extensionArtifact)
            val foundedNumber = allFoundedPackageNumber(extensionFilesMap)
            if (foundedNumber > 1) {
                printExtensionFileConflictError(extensionArtifact, extensionFilesMap)
                continue
            } else if (foundedNumber == 0) {
                log.warn { "[Bot ${bot.botUsername}] 找不到符合的扩展包文件: $extensionArtifact" }
                continue
            }
            val files = loadFoundExtensionPackage(extensionFilesMap)
            extensionEntries.addAll(getExtensionFactories(extensionArtifact, files.first()))
        }
        return extensionEntries.toSet()
    }

    private fun loadFoundExtensionPackage(packageMap: Map<ExtensionPackageFinder, Set<FoundExtensionPackage>>): Set<File> {
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

    private fun findExtensionPackageFile(
        extensionArtifact: Artifact,
    ): Map<ExtensionPackageFinder, Set<FoundExtensionPackage>> {
        val result = mutableMapOf<ExtensionPackageFinder, Set<FoundExtensionPackage>>()
        for (finder in finders) {
            val artifacts = finder.findByArtifact(extensionArtifact, extensionsPath)
            if (artifacts.isNotEmpty()) {
                result[finder] = artifacts
            }
        }
        return result
    }

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
            errMessage.append("\t- 搜索器 `").append(finder::class.simpleName).append("` 找到了以下扩展包: \n")
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

}

/**
 * 已找到的扩展包文件.
 * @param artifact 扩展包构件坐标.
 * @param file 已找到的扩展包文件.
 */
internal class FileFoundExtensionPackage(private val artifact: Artifact, private val file: File) :
    FoundExtensionPackage {

    init {
        if (!file.exists()) {
            throw FileNotFoundException(file.canonicalPath)
        }
    }

    override fun getExtensionArtifact(): Artifact = artifact

    override fun getRawUrl(): URL = file.canonicalFile.toURI().toURL()

    override fun loadExtension(): File = file
}

/**
 * 基于文件名的搜索器.
 *
 * 将搜索文件名(不带扩展包名)结尾为 `${groupId}_${artifactId}_${version}` 的文件.
 * 比如说 `(Example Extension) org.example_scalabot-example_v1.0.0-SNAPSHOT.jar` 是可以的
 */
internal object FileNameFinder : ExtensionPackageFinder {

    override fun findByArtifact(extensionArtifact: Artifact, extensionsPath: File): Set<FoundExtensionPackage> {
        val focusName = getExtensionFilename(extensionArtifact)
        val files = extensionsPath.listFiles { file ->
            file.nameWithoutExtension.endsWith(focusName)
        } ?: return emptySet()

        val extensionPackage = mutableSetOf<FoundExtensionPackage>()
        for (file in files) {
            extensionPackage.add(FileFoundExtensionPackage(extensionArtifact, file))
        }
        return if (extensionPackage.isEmpty()) emptySet() else extensionPackage
    }

    private fun getExtensionFilename(extensionArtifact: Artifact) =
        "${extensionArtifact.groupId}_${extensionArtifact.artifactId}_${extensionArtifact.version}"

}

/**
 * 通过检查 Maven 在发布构件时打包进去的元信息(包括 POM 文件)来获取构件坐标.
 */
internal object MavenMetaInformationFinder : ExtensionPackageFinder {

    private const val MAVEN_META_XML = "pom.xml"
    private const val MAVEN_META_PROPERTIES = "pom.properties"

    override fun findByArtifact(extensionArtifact: Artifact, extensionsPath: File): Set<FoundExtensionPackage> {
        val files = extensionsPath.listFiles() ?: return emptySet()
        val result = mutableSetOf<FoundExtensionPackage>()
        for (file in files) {
            if (file.isFile) {
                val foundArtifact = when (file.extension) {
                    "jar", "zip" -> {
                        getArtifactCoordinateFromArtifactJar(file)
                    }
                    // 尚不清楚 jmod 的具体结构细节, 担心在 Maven 正式支持 jmod 之后会出现变数, 故暂不支持.
                    "jmod" -> null
                    else -> null
                }
                if (foundArtifact != null && extensionArtifact.equalsArtifact(foundArtifact)) {
                    result.add(FileFoundExtensionPackage(extensionArtifact, file))
                }
            } else if (file.isDirectory) {
                val foundArtifact = getArtifactCoordinateFromArtifactDirectory(file)
                if (foundArtifact != null && extensionArtifact.equalsArtifact(foundArtifact)) {
                    result.add(FileFoundExtensionPackage(extensionArtifact, file))
                }
            }
        }
        return if (result.isEmpty()) emptySet() else result
    }

    private fun getArtifactCoordinateFromArtifactDirectory(dir: File): Artifact? {
        if (!dir.isDirectory) {
            return null
        }

        val mavenMetaRoot = File(dir, "META-INF/maven/")
        if (!mavenMetaRoot.exists() || !mavenMetaRoot.isDirectory) {
            return null
        }

        val files = mavenMetaRoot.deepListFiles(filenameFilter = { _, name ->
            name != null && (name.contentEquals(MAVEN_META_XML) || name.contentEquals(MAVEN_META_PROPERTIES))
        })

        val metaFile = files?.firstOrNull() ?: return null
        return when (metaFile.extension.lowercase()) {
            "xml" -> metaFile.inputStream().use { getArtifactFromPomXml(it) }
            "properties" -> metaFile.inputStream().use { getArtifactFromPomProperties(it) }
            else -> null
        }
    }

    private fun getArtifactCoordinateFromArtifactJar(file: File): Artifact? {
        if (!file.isFile) {
            return null
        }
        file.inputStream().use {
            val jarInputStream = JarInputStream(it)
            var entry: JarEntry?
            while (true) {
                entry = jarInputStream.nextJarEntry
                if (entry == null) {
                    break
                }

                if (entry.name.startsWith("META-INF/maven")) {
                    val artifact = if (entry.name.endsWith(MAVEN_META_XML)) {
                        getArtifactFromPomXml(jarInputStream)
                    } else if (entry.name.endsWith(MAVEN_META_PROPERTIES)) {
                        getArtifactFromPomProperties(jarInputStream)
                    } else {
                        continue
                    }
                    return artifact
                }
            }
        }
        return null
    }

    // language=XPATH
    private const val XPATH_POM_ARTIFACT = "/project/artifactId"

    // language=XPATH
    private const val XPATH_POM_GROUP = "/project/groupId"

    // language=XPATH
    private const val XPATH_POM_PARENT_GROUP = "/project/parent/groupId"

    // language=XPATH
    private const val XPATH_POM_VERSION = "/project/version"

    // language=XPATH
    private const val XPATH_POM_PARENT_VERSION = "/project/parent/version"

    // Packaging 也等同于 Extension(Artifact 里的)
    // language=XPATH
    private const val XPATH_POM_PACKAGING = "/project/packaging"

    private val xmlReader = SAXBuilder()
    private val xPathFactory = XPathFactory.instance()

    private fun getArtifactFromPomXml(input: InputStream): DefaultArtifact? {
        val document = xmlReader.build(input) ?: return null

        val artifactName = querySelectorContent(document, XPATH_POM_ARTIFACT) ?: return null
        val groupId =
            querySelectorContent(document, XPATH_POM_GROUP) ?: querySelectorContent(document, XPATH_POM_PARENT_GROUP)
            ?: return null
        val version = querySelectorContent(document, XPATH_POM_VERSION) ?: querySelectorContent(
            document,
            XPATH_POM_PARENT_VERSION
        ) ?: return null
        val extensionName = querySelectorContent(document, XPATH_POM_PACKAGING)

        return DefaultArtifact(groupId, artifactName, extensionName, version)
    }


    private fun querySelectorContent(doc: Document, xPath: String): String? =
        xPathFactory.compile(xPath, Filters.element()).evaluateFirst(doc).text

    private const val PROP_KEY_GROUP = "groupId"
    private const val PROP_KEY_ARTIFACT = "artifactId"
    private const val PROP_KEY_VERSION = "version"

    private fun getArtifactFromPomProperties(input: InputStream): DefaultArtifact? {
        val prop = Properties()
        prop.load(input)
        if (isEmptyOrNull(prop, PROP_KEY_GROUP) || isEmptyOrNull(prop, PROP_KEY_ARTIFACT) || isEmptyOrNull(
                prop,
                PROP_KEY_VERSION
            )
        ) {
            return null
        }
        return DefaultArtifact(
            prop.getProperty(PROP_KEY_GROUP),
            prop.getProperty(PROP_KEY_ARTIFACT),
            null,
            prop.getProperty(PROP_KEY_VERSION)
        )
    }

    private fun isEmptyOrNull(prop: Properties, key: String): Boolean =
        !prop.containsKey(key) || prop.getProperty(key).trim().isEmpty()

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
