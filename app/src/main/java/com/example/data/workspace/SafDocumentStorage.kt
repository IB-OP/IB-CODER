package com.example.data.workspace

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.example.domain.model.FileNode
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class WorkspacePathStat(
    val exists: Boolean,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val isRoot: Boolean = false
)

data class SyncFileEntry(
    val relativePath: String,
    val bytes: ByteArray,
    val isExecutable: Boolean
)

/**
 * Storage Access Framework (SAF) document tree helper backed directly by
 * [DocumentsContract] — zero broad storage permissions required.
 */
class SafDocumentStorage(context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    private data class DocEntry(
        val docId: String,
        val uri: Uri,
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long
    )

    fun resolveTreeFolderDisplayName(treeUri: Uri): String {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val queried = runCatching {
            resolver.query(
                rootDocUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()

        if (!queried.isNullOrBlank()) return queried
        val fromId = rootDocId.substringAfterLast(':').substringAfterLast('/').trim()
        if (fromId.isNotEmpty()) return fromId
        return "Device Folder"
    }

    fun normalizeSegments(relativePath: String): List<String> {
        val raw = relativePath.trim().replace('\\', '/')
        if (raw.contains('\u0000')) {
            throw SecurityException("Access denied: invalid null byte in path '$relativePath'.")
        }
        val segments = raw.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) {
            throw SecurityException("Access denied: path '$relativePath' escapes the project root.")
        }
        return segments
    }

    fun statPath(treeUri: Uri, relativePath: String): WorkspacePathStat {
        val segments = normalizeSegments(relativePath)
        if (segments.isEmpty()) {
            return WorkspacePathStat(exists = true, isDirectory = true, sizeBytes = 0L, isRoot = true)
        }
        val found = findEntry(treeUri, segments) ?: return WorkspacePathStat(exists = false, isDirectory = false)
        return WorkspacePathStat(
            exists = true,
            isDirectory = found.isDirectory,
            sizeBytes = found.sizeBytes,
            isRoot = false
        )
    }

    fun buildFileTree(treeUri: Uri): List<FileNode> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val nodes = mutableListOf<FileNode>()
        fun traverse(parentDocId: String, prefix: String, depth: Int) {
            val children = listChildren(treeUri, parentDocId)
            for (child in children) {
                val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                nodes += FileNode(
                    name = child.name,
                    relativePath = rel,
                    isDirectory = child.isDirectory,
                    sizeBytes = if (child.isDirectory) 0L else child.sizeBytes,
                    depth = depth
                )
                if (child.isDirectory) {
                    traverse(child.docId, rel, depth + 1)
                }
            }
        }
        traverse(rootDocId, "", 0)
        return nodes
    }

    fun listDirectory(treeUri: Uri, relativePath: String): String {
        val segments = normalizeSegments(relativePath)
        val targetDocId = if (segments.isEmpty()) {
            DocumentsContract.getTreeDocumentId(treeUri)
        } else {
            val found = findEntry(treeUri, segments)
                ?: throw IllegalArgumentException("Path does not exist: $relativePath")
            if (!found.isDirectory) {
                throw IllegalArgumentException("Path is a file, not a directory: $relativePath")
            }
            found.docId
        }
        val entries = listChildren(treeUri, targetDocId)
        return if (entries.isEmpty()) {
            "(empty directory)"
        } else {
            entries.joinToString("\n") { entry ->
                if (entry.isDirectory) "[DIR]  ${entry.name}/"
                else "[FILE] ${entry.name} (${entry.sizeBytes} B)"
            }
        }
    }

    fun readText(treeUri: Uri, relativePath: String, maxBytes: Int): String {
        val segments = normalizeSegments(relativePath)
        if (segments.isEmpty()) throw IllegalArgumentException("Not a regular file: $relativePath")
        val found = findEntry(treeUri, segments)
            ?: throw IllegalArgumentException("File not found: $relativePath")
        if (found.isDirectory) throw IllegalArgumentException("Not a regular file: $relativePath")
        if (found.sizeBytes > maxBytes) {
            throw IllegalArgumentException("File exceeds ${maxBytes / 1024} KB read limit.")
        }
        val bytes = readUriBytesBounded(found.uri, maxBytes)
        return bytes.toString(Charsets.UTF_8)
    }

    fun writeText(treeUri: Uri, relativePath: String, content: String): Boolean =
        writeBytes(treeUri, relativePath, content.toByteArray(Charsets.UTF_8))

    fun writeBytes(treeUri: Uri, relativePath: String, bytes: ByteArray): Boolean {
        val segments = normalizeSegments(relativePath)
        if (segments.isEmpty()) {
            throw IllegalArgumentException("File path must not be empty.")
        }
        val parentDocId = ensureDirectory(treeUri, segments.dropLast(1))
        val fileName = segments.last()
        val existing = listChildren(treeUri, parentDocId).firstOrNull { it.name == fileName }
        val existed = existing != null
        val targetUri = if (existing != null) {
            if (existing.isDirectory) {
                throw IllegalArgumentException("A directory already exists at '$relativePath'.")
            }
            existing.uri
        } else {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            DocumentsContract.createDocument(resolver, parentUri, guessMimeType(fileName), fileName)
                ?: throw IllegalStateException("Failed to create '$relativePath' in selected folder.")
        }

        val stream = runCatching { resolver.openOutputStream(targetUri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(targetUri, "w")
            ?: throw IllegalStateException("Could not open '$relativePath' for writing.")
        stream.use { out ->
            out.write(bytes)
            out.flush()
        }
        return existed
    }

    fun createFolder(treeUri: Uri, relativePath: String): Boolean {
        val segments = normalizeSegments(relativePath)
        if (segments.isEmpty()) return true
        val stat = statPath(treeUri, relativePath)
        if (stat.exists) {
            if (stat.isDirectory) return true
            throw IllegalArgumentException("A file already exists at '$relativePath'.")
        }
        ensureDirectory(treeUri, segments)
        return false
    }

    fun deletePath(treeUri: Uri, relativePath: String): Boolean {
        val segments = normalizeSegments(relativePath)
        if (segments.isEmpty()) {
            throw IllegalArgumentException("Cannot delete the project root directory.")
        }
        val found = findEntry(treeUri, segments)
            ?: throw IllegalArgumentException("Path does not exist: '$relativePath'")
        val deleted = DocumentsContract.deleteDocument(resolver, found.uri)
        if (!deleted) {
            throw IllegalStateException("Failed to delete '$relativePath'.")
        }
        return found.isDirectory
    }

    fun clearTopLevelExceptGit(treeUri: Uri) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = listChildren(treeUri, rootDocId)
        for (child in children) {
            if (child.name != ".git") {
                runCatching { DocumentsContract.deleteDocument(resolver, child.uri) }
            }
        }
    }

    fun searchFiles(
        treeUri: Uri,
        query: String,
        relativePath: String,
        isRegex: Boolean,
        maxFileBytes: Int,
        maxResults: Int,
        binaryExtensions: Set<String>
    ): String {
        val segments = normalizeSegments(relativePath)
        val startDocId: String
        val startPrefix: String
        var singleFileEntry: DocEntry? = null

        if (segments.isEmpty()) {
            startDocId = DocumentsContract.getTreeDocumentId(treeUri)
            startPrefix = ""
        } else {
            val found = findEntry(treeUri, segments)
                ?: throw IllegalArgumentException("Search path does not exist: $relativePath")
            if (found.isDirectory) {
                startDocId = found.docId
                startPrefix = segments.joinToString("/")
            } else {
                startDocId = found.docId
                startPrefix = segments.joinToString("/")
                singleFileEntry = found
            }
        }

        val regex = if (isRegex) Regex(query) else null
        val matches = mutableListOf<String>()

        fun scanFile(entry: DocEntry, relPath: String): Boolean {
            if (entry.sizeBytes > maxFileBytes) return false
            val ext = entry.name.substringAfterLast('.', "").lowercase()
            if (ext in binaryExtensions) return false
            val bytes = runCatching { readUriBytesBounded(entry.uri, maxFileBytes) }.getOrNull() ?: return false
            val checkLen = minOf(bytes.size, 512)
            for (i in 0 until checkLen) {
                if (bytes[i] == 0.toByte()) return false
            }
            val lines = bytes.toString(Charsets.UTF_8).lines()
            for ((idx, line) in lines.withIndex()) {
                val hit = if (regex != null) {
                    regex.containsMatchIn(line)
                } else {
                    line.contains(query, ignoreCase = true)
                }
                if (hit) {
                    matches += "$relPath:${idx + 1}: ${line.trim().take(180)}"
                    if (matches.size >= maxResults) return true
                }
            }
            return false
        }

        if (singleFileEntry != null) {
            scanFile(singleFileEntry, startPrefix)
        } else {
            fun walk(dirDocId: String, prefix: String): Boolean {
                val children = listChildren(treeUri, dirDocId)
                for (child in children) {
                    val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                    if (child.isDirectory) {
                        if (walk(child.docId, rel)) return true
                    } else {
                        if (scanFile(child, rel)) return true
                    }
                }
                return false
            }
            walk(startDocId, startPrefix)
        }

        return when {
            matches.isEmpty() -> "No matches found for '$query'."
            matches.size >= maxResults -> buildString {
                appendLine(matches.joinToString("\n"))
                append("(truncated at $maxResults matches)")
            }
            else -> matches.joinToString("\n")
        }
    }

    fun exportToZip(treeUri: Uri, outputStream: OutputStream) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        ZipOutputStream(outputStream.buffered()).use { zos ->
            fun walk(dirDocId: String, prefix: String) {
                val children = listChildren(treeUri, dirDocId)
                for (child in children) {
                    val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                    if (child.isDirectory) {
                        zos.putNextEntry(ZipEntry("$rel/"))
                        zos.closeEntry()
                        walk(child.docId, rel)
                    } else {
                        zos.putNextEntry(ZipEntry(rel))
                        resolver.openInputStream(child.uri)?.use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            walk(rootDocId, "")
        }
    }

    fun collectFilesForSync(
        treeUri: Uri,
        ignoredDirs: Set<String>,
        maxBlobBytes: Int
    ): List<SyncFileEntry> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val entries = mutableListOf<SyncFileEntry>()
        fun walk(dirDocId: String, prefix: String) {
            val children = listChildren(treeUri, dirDocId)
            for (child in children) {
                if (child.isDirectory) {
                    if (child.name !in ignoredDirs) {
                        val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                        walk(child.docId, rel)
                    }
                } else {
                    val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                    if (child.sizeBytes > maxBlobBytes) {
                        error("File '$rel' exceeds ${maxBlobBytes / (1024 * 1024)} MB GitHub API upload limit.")
                    }
                    val bytes = readUriBytesBounded(child.uri, maxBlobBytes)
                    val isExec = child.name == "gradlew" || child.name.endsWith(".sh")
                    entries += SyncFileEntry(
                        relativePath = rel,
                        bytes = bytes,
                        isExecutable = isExec
                    )
                }
            }
        }
        walk(rootDocId, "")
        return entries.sortedBy { it.relativePath }
    }

    private fun ensureDirectory(treeUri: Uri, segments: List<String>): String {
        var currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        for (segment in segments) {
            val existing = listChildren(treeUri, currentDocId).firstOrNull { it.name == segment }
            currentDocId = if (existing != null) {
                if (!existing.isDirectory) {
                    throw IllegalArgumentException("Path conflict: '$segment' is an existing file.")
                }
                existing.docId
            } else {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
                val createdUri = DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment
                ) ?: throw IllegalStateException("Failed to create folder '$segment'.")
                DocumentsContract.getDocumentId(createdUri)
            }
        }
        return currentDocId
    }

    private fun findEntry(treeUri: Uri, segments: List<String>): DocEntry? {
        var currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        var currentEntry: DocEntry? = null
        for ((index, segment) in segments.withIndex()) {
            val match = listChildren(treeUri, currentDocId).firstOrNull { it.name == segment }
                ?: return null
            if (index < segments.lastIndex && !match.isDirectory) {
                return null
            }
            currentEntry = match
            currentDocId = match.docId
        }
        return currentEntry
    }

    private fun listChildren(treeUri: Uri, parentDocId: String): List<DocEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val result = mutableListOf<DocEntry>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val docId = if (idIdx >= 0) cursor.getString(idIdx) else null
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else ""
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                if (!docId.isNullOrBlank() && !name.isNullOrBlank()) {
                    result += DocEntry(
                        docId = docId,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                        name = name,
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        sizeBytes = size
                    )
                }
            }
        }
        return result.sortedWith(compareBy<DocEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun readUriBytesBounded(uri: Uri, maxBytes: Int): ByteArray {
        val input = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file stream.")
        return input.use { stream ->
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    throw IllegalArgumentException("File exceeds ${maxBytes / 1024} KB limit.")
                }
                baos.write(buf, 0, read)
            }
            baos.toByteArray()
        }
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return when (ext) {
            "kt", "kts", "java", "xml", "json", "md", "yml", "yaml", "toml",
            "gradle", "properties", "sh", "txt", "gitignore" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
