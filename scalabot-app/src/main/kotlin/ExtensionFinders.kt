package net.lamgc.scalabot

import com.google.common.base.Throwables
import mu.KotlinLogging
import net.lamgc.scalabot.util.deepListFiles
import net.lamgc.scalabot.util.equalsArtifact
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.*
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResult
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.filter.ScopeDependencyFilter
import org.jdom2.Document
import org.jdom2.filter.Filters
import org.jdom2.input.SAXBuilder
import org.jdom2.xpath.XPathFactory
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarInputStream


/**
 * 基于文件名的搜索器.
 *
 * 将搜索文件名(不带扩展包名)结尾为 `${groupId}_${artifactId}_${version}` 的文件.
 * 比如说 `(Example Extension) org.example_scalabot-example_v1.0.0-SNAPSHOT.jar` 是可以的
 *
 * 使用这种方式, 要求扩展包将依赖打包进自己的 jar, 可能会出现分发包体积较大的情况.
 */
@FinderRules(priority = FinderPriority.LOCAL)
internal object FileNameFinder : ExtensionPackageFinder {

    private val log = KotlinLogging.logger { }

    private val allowExtensionNames = setOf("jar", "jmod", "zip")

    override fun findByArtifact(extensionArtifact: Artifact, extensionsPath: File): Set<FoundExtensionPackage> {
        val focusName = getExtensionFilename(extensionArtifact)
        log.debug { "扩展 $extensionArtifact 匹配规则: $focusName" }
        val files = extensionsPath.listFiles { file ->
            file.nameWithoutExtension.endsWith(focusName) &&
                    (allowExtensionNames.contains(file.extension.lowercase()) || file.isDirectory)
        } ?: return emptySet()

        val extensionPackage = mutableSetOf<FoundExtensionPackage>()
        for (file in files) {
            extensionPackage.add(FileFoundExtensionPackage(extensionArtifact, file, this))
        }
        return if (extensionPackage.isEmpty()) emptySet() else extensionPackage
    }

    private fun getExtensionFilename(extensionArtifact: Artifact) =
        "${extensionArtifact.groupId}_${extensionArtifact.artifactId}_${extensionArtifact.version}"

}

/**
 * 通过检查 Maven 在发布构件时打包进去的元信息(包括 POM 文件)来获取构件坐标.
 */
@FinderRules(priority = FinderPriority.LOCAL)
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
                    result.add(FileFoundExtensionPackage(extensionArtifact, file, this))
                }
            } else if (file.isDirectory) {
                val foundArtifact = getArtifactCoordinateFromArtifactDirectory(file)
                if (foundArtifact != null && extensionArtifact.equalsArtifact(foundArtifact)) {
                    result.add(FileFoundExtensionPackage(extensionArtifact, file, this))
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
 * 从指定的 Maven 仓库下载扩展包.
 *
 * 注: 也会下载扩展包的依赖包.
 *
 * 建议扩展包不要将依赖项打包到本体, 搜索器会自动下载依赖项并自动缓存, 这对于后续更新来讲十分有用. (也会给其他扩展包共享依赖)
 * 目前每个扩展包的依赖都是分开加载的, 我会听取社区意见, 来决定是否有必要让依赖项共享同一个 [ClassLoader].
 *
 * 推荐使用这种方式来安装扩展.
 */
@FinderRules(priority = FinderPriority.REMOTE)
internal class MavenRepositoryExtensionFinder(
    private val localRepository: LocalRepository = LocalRepository("${System.getProperty("user.home")}/.m2/repository"),
    private val proxy: Proxy? = null,
    private val remoteRepositories: List<RemoteRepository> = listOf(getMavenCentralRepository(proxy)),
) : ExtensionPackageFinder {

    private val repositorySystem = createRepositorySystem()

    private val repoSystemSession = createRepositorySystemSession()

    private fun createRepositorySystem() = MavenRepositorySystemUtils.newServiceLocator().apply {
        addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
        addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
    }.getService(RepositorySystem::class.java)

    private fun createRepositorySystemSession() = MavenRepositorySystemUtils.newSession().apply {
        localRepositoryManager = repositorySystem.newLocalRepositoryManager(
            this,
            this@MavenRepositoryExtensionFinder.localRepository
        )
    }

    override fun findByArtifact(extensionArtifact: Artifact, extensionsPath: File): Set<FoundExtensionPackage> {
        val extensionArtifactResult = repositorySystem.resolveArtifact(
            repoSystemSession,
            ArtifactRequest(extensionArtifact, remoteRepositories, null)
        )
        val extResolvedArtifact = extensionArtifactResult.artifact
        if (!extensionArtifactResult.isResolved) {
            if (extensionArtifactResult.isMissing) {
                log.warn { "在指定的仓库中找不到构件: ${extensionArtifactResult.artifact}" }
            } else {
                printArtifactResultExceptions(extensionArtifactResult.exceptions)
            }
            return emptySet()
        }

        val request = DependencyRequest(
            CollectRequest(Dependency(extResolvedArtifact, null), remoteRepositories),
            ScopeDependencyFilter(setOf("runtime", "compile", "provided"), null)
        )
        val dependencyResult = repositorySystem.resolveDependencies(repoSystemSession, request)
        val dependencies = checkAndCollectDependencyArtifacts(extensionArtifact, dependencyResult.artifactResults)
            ?: return emptySet()

        return setOf(MavenExtensionPackage(this, extResolvedArtifact, extensionArtifactResult.repository, dependencies))
    }

    private fun checkAndCollectDependencyArtifacts(
        extensionArtifact: Artifact,
        dependencyResult: List<ArtifactResult>
    ): Set<Artifact>? {
        val resolveFailedArtifacts = mutableSetOf<ArtifactResult>()
        for (artifactResult in dependencyResult) {
            if (!artifactResult.isResolved) {
                resolveFailedArtifacts.add(artifactResult)
            }
        }

        if (resolveFailedArtifacts.isNotEmpty()) {
            log.error {
                StringBuilder("扩展包 `$extensionArtifact` 下列依赖项获取失败: \n").apply {
                    resolveFailedArtifacts.forEach {
                        append("\t- 依赖项 `${it.artifact}` ")
                        if (it.isMissing) {
                            append(" 无法从指定仓库中找到.")
                        } else {
                            append("\n")
                            for (e in it.exceptions) {
                                append("\t\t- ${e::class.java.name}: ${e.message}\n")
                            }
                        }
                    }
                }.toString()
            }
            return null
        }

        return dependencyResult.map {
            log.debug { "依赖项 ${it.artifact} 文件路径: ${it.artifact.file}" }
            it.artifact!!
        }.toSet()
    }

    private fun printArtifactResultExceptions(exceptions: List<Exception>) {
        log.warn {
            val builder = StringBuilder("构件可能已找到, 但由于以下原因导致获取失败: \n")
            exceptions.forEachIndexed { index, exception ->
                builder.append("[$index] ").append(Throwables.getStackTraceAsString(exception)).append('\n')
            }
            return@warn builder.toString()
        }
    }

    @Suppress("unused")
    companion object {
        @JvmStatic
        private val log = KotlinLogging.logger { }

        /**
         * 将 [URL] 转换成 [RemoteRepository] 对象.
         * @param url 远端仓库地址.
         * @param type 仓库布局类型, 如果是 Maven 2 的仓库布局, 则为 `default`, 如果是 Maven 1 的旧版仓库布局, 则为 `legacy`.
         * @param proxy 是否使用代理访问仓库, 默认为 `null`.
         */
        fun resolveRepositoryByUrl(
            url: URL,
            repoId: String? = null,
            type: String = "default",
            proxy: Proxy?,
            authentication: Authentication? = null
        ): RemoteRepository {
            val builder = RemoteRepository.Builder(repoId, type, url.toString())
            if (proxy != null) {
                builder.setProxy(proxy)
            }
            builder.setReleasePolicy(
                RepositoryPolicy(
                    true,
                    RepositoryPolicy.UPDATE_POLICY_DAILY,
                    RepositoryPolicy.CHECKSUM_POLICY_FAIL
                )
            )
            builder.setSnapshotPolicy(
                RepositoryPolicy(
                    true,
                    RepositoryPolicy.UPDATE_POLICY_ALWAYS,
                    RepositoryPolicy.CHECKSUM_POLICY_FAIL
                )
            )
            if (authentication != null) {
                builder.setAuthentication(authentication)
            }
            return builder.build()
        }

        /**
         * Maven 中央仓库 Url.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        const val MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2/"

        /**
         * 获取 Maven 中央仓库的 [RemoteRepository] 对象.
         */
        fun getMavenCentralRepository(proxy: Proxy? = null): RemoteRepository {
            val builder = RemoteRepository.Builder("central", "default", MAVEN_CENTRAL_URL)
            if (proxy != null) {
                builder.setProxy(proxy)
            }
            return builder.build()
        }

    }

    class MavenExtensionPackage(
        private val finder: ExtensionPackageFinder,
        private val artifact: Artifact,
        private val fromRepository: ArtifactRepository,
        val dependencies: Set<Artifact>
    ) : FoundExtensionPackage {
        override fun getExtensionArtifact(): Artifact = artifact

        override fun getRawUrl(): URL {
            return if (fromRepository is RemoteRepository) {
                val urlStr = if (fromRepository.url.endsWith("/")) {
                    fromRepository.url + getArtifactPath(artifact)
                } else {
                    fromRepository.url + "/" + getArtifactPath(artifact)
                }
                URL(urlStr)
            } else {
                getPackageFile().toURI().toURL()
            }
        }

        override fun getPackageFile(): File = artifact.file

        override fun getExtensionPackageFinder() = finder

        /**
         * 获取 Artifact 在 Maven 仓库中的路径.
         *
         * 遵循 [Repository Layout - Final](https://cwiki.apache.org/confluence/display/MAVENOLD/Repository+Layout+-+Final)
         */
        private fun getArtifactPath(artifact: Artifact): String {
            val groups = artifact.groupId.split('.')
            return StringBuilder("/").apply {
                for (group in groups) {
                    append(group).append('/')
                }
                append("${artifact.artifactId}/${artifact.version}/")
                append("${artifact.artifactId}-${artifact.version}")
                if (artifact.classifier.isNotEmpty()) {
                    append("-${artifact.classifier}")
                }
                append(".${artifact.extension}")
            }.toString()
        }

    }

    override fun getClassLoaderFactory(): ExtensionClassLoaderFactory {
        return object : ExtensionClassLoaderFactory {
            override fun createClassLoader(foundExtensionPackage: FoundExtensionPackage): ExtensionClassLoader {
                if (foundExtensionPackage !is MavenExtensionPackage) {
                    throw IllegalArgumentException("Unsupported FoundExtensionPackage type: $foundExtensionPackage")
                }

                val urls = mutableSetOf<URL>()
                for (dependency in foundExtensionPackage.dependencies) {
                    val dependencyFile = dependency.file ?: continue
                    urls.add(dependencyFile.toURI().toURL())
                }

                // 将依赖的 ClassLoader 与 ExtensionPackage 的 ClassLoader 分开
                // 这么做可以防范依赖中隐藏的 SPI 注册, 避免安全隐患.

                val dependenciesUrlArray = urls.toTypedArray()
                val dependenciesClassLoader = URLClassLoader(dependenciesUrlArray)

                return ExtensionClassLoader(
                    arrayOf(foundExtensionPackage.getPackageFile().toURI().toURL()),
                    dependenciesClassLoader
                )
            }
        }
    }

}

