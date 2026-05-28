@file:Suppress("unused")

package io.legado.app.utils

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.exception.NoStackTraceException
import splitties.init.appCtx
import splitties.systemservices.downloadManager
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger


data class FileDoc(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val uri: Uri
) {

    override fun toString(): String {
        return if (uri.isContentScheme()) uri.toString() else uri.path!!
    }

    val isContentScheme get() = uri.isContentScheme()

    fun readBytes(): ByteArray {
        return uri.readBytes(appCtx)
    }

    fun readText(): String {
        return uri.readText(appCtx)
    }

    fun asDocumentFile(): DocumentFile? {
        if (isContentScheme) {
            return if (isDir) {
                treeDocumentFileConstructor.newInstance(null, appCtx, uri) as DocumentFile
            } else {
                DocumentFile.fromSingleUri(appCtx, uri)
            }
        }
        return null
    }

    fun asFile(): File? {
        if (isContentScheme) {
            return null
        }
        return File(uri.path!!)
    }

    companion object {

        private val treeDocumentFileConstructor by lazy {
            Class.forName("androidx.documentfile.provider.TreeDocumentFile")
                .getDeclaredConstructor(
                    DocumentFile::class.java,
                    Context::class.java,
                    Uri::class.java
                ).apply {
                    isAccessible = true
                }
        }

        fun fromDir(path: String): FileDoc {
            return fromUri(path.toUri(), true)
        }

        fun fromFile(path: String): FileDoc {
            return fromUri(path.toUri(), false)
        }

        fun fromDir(uri: Uri): FileDoc {
            return fromUri(uri, true)
        }

        fun fromUri(uri: Uri, isDir: Boolean): FileDoc {
            if (uri.isContentScheme()) {
                val doc = if (isDir) {
                    DocumentFile.fromTreeUri(appCtx, uri)!!
                } else if (uri.host == "downloads") {
                    val query = DownloadManager.Query()
                    query.setFilterById(uri.lastPathSegment!!.toLong())
                    downloadManager.query(query).use {
                        if (it.moveToFirst()) {
                            val lUriColum = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val lUri = it.getString(lUriColum)
                            DocumentFile.fromSingleUri(appCtx, Uri.parse(lUri))!!
                        } else {
                            DocumentFile.fromSingleUri(appCtx, uri)!!
                        }
                    }
                } else {
                    DocumentFile.fromSingleUri(appCtx, uri)!!
                }
                return FileDoc(doc.name ?: "", isDir, doc.length(), doc.lastModified(), doc.uri)
            }
            val file = File(uri.path!!)
            return FileDoc(file.name, isDir, file.length(), file.lastModified(), uri)
        }

        fun fromDocumentFile(doc: DocumentFile): FileDoc {
            return FileDoc(
                name = doc.name ?: "",
                isDir = doc.isDirectory,
                size = doc.length(),
                lastModified = doc.lastModified(),
                uri = doc.uri
            )
        }

        fun fromFile(file: File): FileDoc {
            return FileDoc(
                name = file.name,
                isDir = file.isDirectory,
                size = file.length(),
                lastModified = file.lastModified(),
                uri = Uri.fromFile(file)
            )
        }

    }
}

/**
 * 过滤器
 */
typealias FileDocFilter = (file: FileDoc) -> Boolean



fun FileDoc.createFolderIfNotExist(
    vararg subDirs: String
): FileDoc {
    return if (uri.isContentScheme()) {
        val documentFile = asDocumentFile()!!
        val tmp = DocumentUtils.createFolderIfNotExist(documentFile, *subDirs)!!
        FileDoc.fromDocumentFile(tmp)
    } else {
        val path = FileUtils.getPath(uri.path!!, *subDirs)
        val tmp = FileUtils.createFolderIfNotExist(path)
        FileDoc.fromFile(tmp)
    }
}

fun FileDoc.openReadPfd(): Result<ParcelFileDescriptor> {
    return uri.toReadPfd(appCtx)
}

