package com.example.data.workspace

import android.content.Context
import com.example.data.local.db.DbMappers.toDomain
import com.example.data.local.db.ProjectDao
import com.example.data.local.db.ProjectEntity
import com.example.domain.model.FileNode
import com.example.domain.model.WorkspaceProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class WorkspaceManager(
    context: Context,
    private val projectDao: ProjectDao
) {
    private val workspacesRoot: File = File(context.filesDir, "workspaces").apply {
        if (!exists()) mkdirs()
    }

    fun observeProjects(): Flow<List<WorkspaceProject>> =
        projectDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getProject(projectId: String): WorkspaceProject? =
        projectDao.getById(projectId)?.toDomain()

    suspend fun createProject(name: String): WorkspaceProject = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifEmpty { "Untitled Project" }
        val id = UUID.randomUUID().toString()
        val projectDir = File(workspacesRoot, id)
        if (!projectDir.exists()) projectDir.mkdirs()

        val now = System.currentTimeMillis()
        val entity = ProjectEntity(
            id = id,
            name = cleanName,
            rootPath = projectDir.absolutePath,
            createdAt = now,
            updatedAt = now
        )
        projectDao.upsert(entity)
        entity.toDomain()
    }

    suspend fun renameProject(projectId: String, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            projectDao.rename(projectId, trimmed, System.currentTimeMillis())
        }
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val entity = projectDao.getById(projectId)
        if (entity != null) {
            File(entity.rootPath).deleteRecursively()
        }
        projectDao.deleteById(projectId)
    }

    suspend fun importProjectFromZip(name: String, inputStream: InputStream): WorkspaceProject =
        withContext(Dispatchers.IO) {
            val project = createProject(name)
            val rootDir = File(project.rootPath)
            val rootCanonical = rootDir.canonicalPath

            ZipInputStream(inputStream.buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.replace('\\', '/').trimStart('/')
                    if (entryName.isNotEmpty()) {
                        val target = File(rootDir, entryName)
                        val targetCanonical = target.canonicalPath
                        if (!targetCanonical.startsWith(rootCanonical + File.separator) &&
                            targetCanonical != rootCanonical
                        ) {
                            throw SecurityException("Blocked unsafe zip path: $entryName")
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { out ->
                                zis.copyTo(out)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            projectDao.touch(project.id, System.currentTimeMillis())
            project
        }

    suspend fun exportProjectToZip(projectId: String, outputStream: OutputStream) =
        withContext(Dispatchers.IO) {
            val project = projectDao.getById(projectId)
                ?: throw IllegalArgumentException("Project not found: $projectId")
            val rootDir = File(project.rootPath)
            val rootUri = rootDir.toURI()

            ZipOutputStream(outputStream.buffered()).use { zos ->
                if (!rootDir.exists()) return@use
                val allFiles = rootDir.walkTopDown()
                    .filter { it != rootDir }
                    .sortedBy { it.absolutePath }

                for (file in allFiles) {
                    val relPath = rootUri.relativize(file.toURI()).path
                        .replace('\\', '/')
                    if (file.isDirectory) {
                        val dirPath = if (relPath.endsWith("/")) relPath else "$relPath/"
                        zos.putNextEntry(ZipEntry(dirPath))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(relPath))
                        file.inputStream().use { input -> input.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }

    suspend fun resolveSafeFile(projectId: String, relativePath: String): File {
        val project = projectDao.getById(projectId)
            ?: throw IllegalArgumentException("No active project selected.")
        val rootDir = File(project.rootPath)
        if (!rootDir.exists()) rootDir.mkdirs()

        val cleaned = relativePath.trim()
            .replace('\\', '/')
            .trimStart('/')
            .ifEmpty { "." }

        val target = if (cleaned == ".") rootDir else File(rootDir, cleaned)
        val rootCanonical = rootDir.canonicalPath
        val targetCanonical = target.canonicalPath

        if (targetCanonical != rootCanonical &&
            !targetCanonical.startsWith(rootCanonical + File.separator)
        ) {
            throw SecurityException("Access denied: path '$relativePath' escapes the project root.")
        }
        return target
    }

    suspend fun buildFileTree(projectId: String): List<FileNode> = withContext(Dispatchers.IO) {
        val project = projectDao.getById(projectId) ?: return@withContext emptyList()
        val rootDir = File(project.rootPath)
        if (!rootDir.exists()) return@withContext emptyList()

        val nodes = mutableListOf<FileNode>()
        fun traverse(dir: File, prefix: String, depth: Int) {
            val children = dir.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                .orEmpty()
            for (child in children) {
                val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                nodes += FileNode(
                    name = child.name,
                    relativePath = rel,
                    isDirectory = child.isDirectory,
                    sizeBytes = if (child.isFile) child.length() else 0L,
                    depth = depth
                )
                if (child.isDirectory) {
                    traverse(child, rel, depth + 1)
                }
            }
        }
        traverse(rootDir, "", 0)
        nodes
    }

    suspend fun listDirectory(projectId: String, relativePath: String): String =
        withContext(Dispatchers.IO) {
            val target = resolveSafeFile(projectId, relativePath)
            if (!target.exists()) {
                throw IllegalArgumentException("Path does not exist: $relativePath")
            }
            if (!target.isDirectory) {
                throw IllegalArgumentException("Path is a file, not a directory: $relativePath")
            }
            val entries = target.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                .orEmpty()
            if (entries.isEmpty()) {
                "(empty directory)"
            } else {
                entries.joinToString("\n") { file ->
                    if (file.isDirectory) "[DIR]  ${file.name}/"
                    else "[FILE] ${file.name} (${file.length()} B)"
                }
            }
        }

    suspend fun readFile(
        projectId: String,
        relativePath: String,
        startLine: Int? = null,
        endLine: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val target = resolveSafeFile(projectId, relativePath)
        if (!target.exists()) throw IllegalArgumentException("File not found: $relativePath")
        if (!target.isFile) throw IllegalArgumentException("Not a regular file: $relativePath")
        if (target.length() > MAX_READ_BYTES) {
            throw IllegalArgumentException("File exceeds ${MAX_READ_BYTES / 1024} KB read limit.")
        }

        val lines = target.readLines(Charsets.UTF_8)
        if (lines.isEmpty()) return@withContext "(empty file)"

        val from = (startLine ?: 1).coerceAtLeast(1)
        val to = (endLine ?: lines.size).coerceAtMost(lines.size)
        if (from > to) return@withContext "(requested range $from..$to is empty)"

        buildString {
            for (idx in from..to) {
                append(idx.toString().padStart(4, ' '))
                append(" | ")
                appendLine(lines[idx - 1])
            }
        }.trimEnd()
    }

    suspend fun readRawFile(projectId: String, relativePath: String): String =
        withContext(Dispatchers.IO) {
            val target = resolveSafeFile(projectId, relativePath)
            if (!target.exists() || !target.isFile) {
                throw IllegalArgumentException("File not found: $relativePath")
            }
            if (target.length() > MAX_READ_BYTES) {
                throw IllegalArgumentException("File too large to display (${target.length()} bytes).")
            }
            target.readText(Charsets.UTF_8)
        }

    suspend fun writeFile(projectId: String, relativePath: String, content: String): String =
        withContext(Dispatchers.IO) {
            val target = resolveSafeFile(projectId, relativePath)
            target.parentFile?.mkdirs()
            val existed = target.exists()
            target.writeText(content, Charsets.UTF_8)
            projectDao.touch(projectId, System.currentTimeMillis())
            val lineCount = content.lines().size
            if (existed) {
                "Updated '$relativePath' ($lineCount lines, ${content.length} chars)."
            } else {
                "Created '$relativePath' ($lineCount lines, ${content.length} chars)."
            }
        }

    suspend fun editFile(
        projectId: String,
        relativePath: String,
        find: String,
        replace: String,
        replaceAll: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        if (find.isEmpty()) throw IllegalArgumentException("'find' string must not be empty.")
        val target = resolveSafeFile(projectId, relativePath)
        if (!target.exists() || !target.isFile) {
            throw IllegalArgumentException("File not found: $relativePath")
        }

        val original = target.readText(Charsets.UTF_8)
        var count = 0
        var searchIndex = 0
        while (true) {
            val found = original.indexOf(find, searchIndex)
            if (found < 0) break
            count++
            searchIndex = found + find.length
        }

        if (count == 0) {
            throw IllegalArgumentException(
                "Target text not found in '$relativePath'. Read the file first to verify exact contents."
            )
        }
        if (count > 1 && !replaceAll) {
            throw IllegalArgumentException(
                "Found $count occurrences in '$relativePath'. Provide a more specific 'find' block or set replace_all=true."
            )
        }

        val updated = if (replaceAll) {
            original.replace(find, replace)
        } else {
            val firstIdx = original.indexOf(find)
            original.substring(0, firstIdx) + replace + original.substring(firstIdx + find.length)
        }

        target.writeText(updated, Charsets.UTF_8)
        projectDao.touch(projectId, System.currentTimeMillis())
        val replacedCount = if (replaceAll) count else 1
        "Edited '$relativePath': replaced $replacedCount occurrence(s)."
    }

    suspend fun createFolder(projectId: String, relativePath: String): String =
        withContext(Dispatchers.IO) {
            val target = resolveSafeFile(projectId, relativePath)
            if (target.exists()) {
                if (target.isDirectory) return@withContext "Folder already exists: '$relativePath'"
                throw IllegalArgumentException("A file already exists at '$relativePath'.")
            }
            if (!target.mkdirs()) {
                throw IllegalStateException("Failed to create folder: '$relativePath'")
            }
            projectDao.touch(projectId, System.currentTimeMillis())
            "Created folder '$relativePath'."
        }

    suspend fun deletePath(projectId: String, relativePath: String): String =
        withContext(Dispatchers.IO) {
            val target = resolveSafeFile(projectId, relativePath)
            val root = resolveSafeFile(projectId, ".")
            if (target.canonicalPath == root.canonicalPath) {
                throw IllegalArgumentException("Cannot delete the project root directory.")
            }
            if (!target.exists()) {
                throw IllegalArgumentException("Path does not exist: '$relativePath'")
            }
            val wasDir = target.isDirectory
            val deleted = if (wasDir) target.deleteRecursively() else target.delete()
            if (!deleted) {
                throw IllegalStateException("Failed to delete '$relativePath'")
            }
            projectDao.touch(projectId, System.currentTimeMillis())
            if (wasDir) "Deleted folder '$relativePath'." else "Deleted file '$relativePath'."
        }

    suspend fun searchFiles(
        projectId: String,
        query: String,
        relativePath: String = ".",
        isRegex: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        if (query.isEmpty()) throw IllegalArgumentException("Search query must not be empty.")
        val base = resolveSafeFile(projectId, relativePath)
        if (!base.exists()) throw IllegalArgumentException("Search path does not exist: $relativePath")

        val project = projectDao.getById(projectId)
            ?: throw IllegalArgumentException("Project not found")
        val rootUri = File(project.rootPath).toURI()
        val regex = if (isRegex) Regex(query) else null

        val matches = mutableListOf<String>()
        val files = if (base.isFile) sequenceOf(base) else base.walkTopDown().filter { it.isFile }

        for (file in files) {
            if (file.length() > MAX_SEARCH_FILE_BYTES || isLikelyBinary(file)) continue
            val rel = rootUri.relativize(file.toURI()).path.replace('\\', '/')
            val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrNull() ?: continue
            lines.forEachIndexed { idx, line ->
                val hit = if (regex != null) {
                    regex.containsMatchIn(line)
                } else {
                    line.contains(query, ignoreCase = true)
                }
                if (hit) {
                    matches += "$rel:${idx + 1}: ${line.trim().take(180)}"
                    if (matches.size >= MAX_SEARCH_RESULTS) return@withContext buildString {
                        appendLine(matches.joinToString("\n"))
                        append("(truncated at $MAX_SEARCH_RESULTS matches)")
                    }
                }
            }
        }

        if (matches.isEmpty()) "No matches found for '$query'." else matches.joinToString("\n")
    }

    private fun isLikelyBinary(file: File): Boolean {
        val ext = file.extension.lowercase()
        if (ext in BINARY_EXTENSIONS) return true
        return try {
            file.inputStream().use { input ->
                val buf = ByteArray(512)
                val read = input.read(buf)
                for (i in 0 until read) {
                    if (buf[i] == 0.toByte()) return true
                }
                false
            }
        } catch (_: Exception) {
            true
        }
    }

    companion object {
        private const val MAX_READ_BYTES = 512 * 1024
        private const val MAX_SEARCH_FILE_BYTES = 512 * 1024
        private const val MAX_SEARCH_RESULTS = 100
        private val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "jar", "apk", "aab", "zip", "so", "dex", "class", "pdf"
        )
    }
}
