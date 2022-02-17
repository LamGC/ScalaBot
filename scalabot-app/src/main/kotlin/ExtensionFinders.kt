package net.lamgc.scalabot

import net.lamgc.scalabot.util.deepListFiles
import net.lamgc.scalabot.util.equalsArtifact
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.jdom2.Document
import org.jdom2.filter.Filters
import org.jdom2.input.SAXBuilder
import org.jdom2.xpath.XPathFactory
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarInputStream


/**
 * 基于文件名的搜索器.
 *
 * 将搜索文件名(不带扩展包名)结尾为 `${groupId}_${artifactId}_${version}` 的文件.
 * 比如说 `(Example Extension) org.example_scalabot-example_v1.0.0-SNAPSHOT.jar` 是可以的
 */
@FinderRules(priority = FinderPriority.LOCAL)
internal object FileNameFinder : ExtensionPackageFinder {

    override fun findByArtifact(extensionArtifact: Artifact, extensionsPath: File): Set<FoundExtensionPackage> {
        val focusName = getExtensionFilename(extensionArtifact)
        val files = extensionsPath.listFiles { file ->
            file.nameWithoutExtension.endsWith(focusName)
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
