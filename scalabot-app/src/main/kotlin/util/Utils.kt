package net.lamgc.scalabot.util

import org.eclipse.aether.artifact.Artifact
import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter

internal fun ByteArray.toHaxString(): String = ByteUtils.bytesToHexString(this)

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
    }

    if (files == null) {
        return null
    }

    val result = if (addSelf) mutableSetOf(this) else mutableSetOf()
    for (file in files) {
        if (file.isFile) {
            result.add(file)
        } else {
            if (!onlyFile) {
                result.add(file)
            }
            val subFiles = file.deepListFiles(false, onlyFile, fileFilter, filenameFilter)
            if (subFiles != null) {
                result.addAll(subFiles)
            }
        }
    }
    return result.toTypedArray()
}

