package com.example.data.network

import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.GitHubBranchInfo
import com.example.domain.model.GitHubDeviceCodeResponse
import com.example.domain.model.GitHubPushResult
import com.example.domain.model.GitHubRepositorySummary
import com.example.domain.model.GitHubUserProfile
import com.example.domain.model.GitHubWorkflowInfo
import com.example.domain.model.GitHubWorkflowJobLog
import com.example.domain.model.GitHubWorkflowRunSummary
import com.example.domain.model.WorkspaceProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.Base64

sealed class DeviceTokenPollResult {
    data class Success(val accessToken: String) : DeviceTokenPollResult()
    data class Pending(val slowDown: Boolean = false) : DeviceTokenPollResult()
    data class Failed(val message: String) : DeviceTokenPollResult()
}

class GitHubPushClient(
    private val okHttpClient: OkHttpClient,
    private val workspaceManager: WorkspaceManager,
    private val json: Json
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun verifyToken(token: String): Result<GitHubUserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "Enter a GitHub Personal Access Token first." }
            val userObj = getJson("https://api.github.com/user", cleanToken)
            GitHubUserProfile(
                login = userObj["login"]?.jsonPrimitive?.content.orEmpty()
                    .ifBlank { error("GitHub returned an empty username.") },
                name = userObj["name"]?.jsonPrimitive?.content.orEmpty(),
                email = userObj["email"]?.jsonPrimitive?.content.orEmpty(),
                avatarUrl = userObj["avatar_url"]?.jsonPrimitive?.content.orEmpty(),
                bio = userObj["bio"]?.jsonPrimitive?.content.orEmpty(),
                publicRepos = userObj["public_repos"]?.jsonPrimitive?.intOrNull ?: 0,
                totalPrivateRepos = userObj["total_private_repos"]?.jsonPrimitive?.intOrNull
                    ?: (userObj["owned_private_repos"]?.jsonPrimitive?.intOrNull ?: 0)
            )
        }
    }

    // --- GitHub OAuth Device Flow ---

    suspend fun startDeviceFlow(
        clientId: String,
        scope: String = "repo workflow delete_repo read:user"
    ): Result<GitHubDeviceCodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanId = clientId.trim()
            require(cleanId.isNotEmpty()) { "Enter your GitHub OAuth App Client ID." }
            val form = FormBody.Builder()
                .add("client_id", cleanId)
                .add("scope", scope)
                .build()
            val req = Request.Builder()
                .url("https://github.com/login/device/code")
                .header("Accept", "application/json")
                .header("User-Agent", "AI-Coder-Android")
                .post(form)
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("GitHub Device Flow error (${resp.code}): ${body.take(200)}")
                }
                val obj = json.parseToJsonElement(body).jsonObject
                val err = obj["error_description"]?.jsonPrimitive?.content
                    ?: obj["error"]?.jsonPrimitive?.content
                if (!err.isNullOrBlank()) error(err)

                GitHubDeviceCodeResponse(
                    deviceCode = obj["device_code"]?.jsonPrimitive?.content
                        ?: error("Missing device_code"),
                    userCode = obj["user_code"]?.jsonPrimitive?.content
                        ?: error("Missing user_code"),
                    verificationUri = obj["verification_uri"]?.jsonPrimitive?.content
                        ?: "https://github.com/login/device",
                    expiresIn = obj["expires_in"]?.jsonPrimitive?.intOrNull ?: 900,
                    interval = obj["interval"]?.jsonPrimitive?.intOrNull ?: 5
                )
            }
        }
    }

    suspend fun pollDeviceFlowToken(
        clientId: String,
        deviceCode: String
    ): DeviceTokenPollResult = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("client_id", clientId.trim())
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val req = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .header("User-Agent", "AI-Coder-Android")
                .post(form)
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext DeviceTokenPollResult.Failed("GitHub returned HTTP ${resp.code}.")
                }
                val obj = json.parseToJsonElement(body).jsonObject
                val accessToken = obj["access_token"]?.jsonPrimitive?.content
                if (!accessToken.isNullOrBlank()) {
                    return@withContext DeviceTokenPollResult.Success(accessToken)
                }
                when (val err = obj["error"]?.jsonPrimitive?.content.orEmpty()) {
                    "authorization_pending" -> DeviceTokenPollResult.Pending(slowDown = false)
                    "slow_down" -> DeviceTokenPollResult.Pending(slowDown = true)
                    "expired_token" -> DeviceTokenPollResult.Failed("Authorization code expired. Please request a new code.")
                    "access_denied" -> DeviceTokenPollResult.Failed("Authorization was denied in browser.")
                    else -> {
                        val desc = obj["error_description"]?.jsonPrimitive?.content ?: err
                        DeviceTokenPollResult.Failed(desc.ifBlank { "OAuth authorization failed." })
                    }
                }
            }
        } catch (e: Exception) {
            DeviceTokenPollResult.Failed(e.message ?: "Network error while polling GitHub.")
        }
    }

    // --- Full Account Repositories & Branches ---

    suspend fun listRepositories(token: String): Result<List<GitHubRepositorySummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanToken = token.trim()
                require(cleanToken.isNotEmpty()) { "Sign in to GitHub first." }
                val url = "https://api.github.com/user/repos?sort=updated&per_page=100&affiliation=owner,collaborator,organization_member"
                val (code, body) = executeRaw("GET", url, cleanToken, null)
                if (code !in 200..299) error(formatGitHubError(code, body))
                val arr = json.parseToJsonElement(body).jsonArray
                arr.mapNotNull { elem ->
                    val obj = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val ownerObj = obj["owner"]?.jsonObject
                    GitHubRepositorySummary(
                        id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                        name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                        fullName = obj["full_name"]?.jsonPrimitive?.content.orEmpty(),
                        ownerLogin = ownerObj?.get("login")?.jsonPrimitive?.content.orEmpty(),
                        description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                        isPrivate = obj["private"]?.jsonPrimitive?.booleanOrNull ?: false,
                        defaultBranch = obj["default_branch"]?.jsonPrimitive?.content ?: "main",
                        htmlUrl = obj["html_url"]?.jsonPrimitive?.content.orEmpty(),
                        updatedAt = obj["updated_at"]?.jsonPrimitive?.content.orEmpty(),
                        language = obj["language"]?.jsonPrimitive?.content.orEmpty()
                    )
                }
            }
        }

    suspend fun createRepository(
        token: String,
        name: String,
        description: String,
        isPrivate: Boolean,
        autoInit: Boolean = true
    ): Result<GitHubRepositorySummary> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            val cleanName = name.trim()
            require(cleanToken.isNotEmpty()) { "Sign in to GitHub first." }
            require(cleanName.isNotEmpty()) { "Repository name is required." }
            val payload = buildJsonObject {
                put("name", cleanName)
                put("description", description.trim())
                put("private", isPrivate)
                put("auto_init", autoInit)
            }.toString()
            val obj = postJson("https://api.github.com/user/repos", cleanToken, payload)
            parseRepoSummary(obj)
        }
    }

    suspend fun updateRepository(
        token: String,
        owner: String,
        repo: String,
        newName: String,
        description: String,
        isPrivate: Boolean,
        defaultBranch: String
    ): Result<GitHubRepositorySummary> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "Sign in to GitHub first." }
            val payload = buildJsonObject {
                if (newName.isNotBlank() && newName.trim() != repo) put("name", newName.trim())
                put("description", description.trim())
                put("private", isPrivate)
                if (defaultBranch.isNotBlank()) put("default_branch", defaultBranch.trim())
            }.toString()
            val url = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}"
            val (code, body) = executeRaw("PATCH", url, cleanToken, payload)
            if (code !in 200..299) error(formatGitHubError(code, body))
            parseRepoSummary(json.parseToJsonElement(body).jsonObject)
        }
    }

    suspend fun deleteRepository(
        token: String,
        owner: String,
        repo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "Sign in to GitHub first." }
            val url = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}"
            val (code, body) = executeRaw("DELETE", url, cleanToken, null)
            if (code !in 200..299) error(formatGitHubError(code, body))
        }
    }

    suspend fun listBranches(
        token: String,
        owner: String,
        repo: String
    ): Result<List<GitHubBranchInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            val url = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/branches?per_page=100"
            val (code, body) = executeRaw("GET", url, cleanToken, null)
            if (code !in 200..299) error(formatGitHubError(code, body))
            val arr = json.parseToJsonElement(body).jsonArray
            arr.mapNotNull { elem ->
                val obj = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
                GitHubBranchInfo(
                    name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    commitSha = obj["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content.orEmpty(),
                    isProtected = obj["protected"]?.jsonPrimitive?.booleanOrNull ?: false
                )
            }
        }
    }

    suspend fun createBranch(
        token: String,
        owner: String,
        repo: String,
        newBranch: String,
        fromSha: String
    ): Result<GitHubBranchInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBranch = newBranch.trim()
            require(cleanBranch.isNotEmpty()) { "Branch name cannot be empty." }
            val payload = buildJsonObject {
                put("ref", "refs/heads/$cleanBranch")
                put("sha", fromSha)
            }.toString()
            postJson(
                "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/git/refs",
                token.trim(),
                payload
            )
            GitHubBranchInfo(name = cleanBranch, commitSha = fromSha, isProtected = false)
        }
    }

    suspend fun cloneOrPullRepository(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        existingProjectId: String? = null
    ): Result<WorkspaceProject> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "Sign in to GitHub first." }
            val ref = branch.trim().ifBlank { "main" }
            val url = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/zipball/${encPath(ref)}"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer $cleanToken")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "AI-Coder-Android")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.code == 409) {
                    // Empty Git repository with no commits yet
                    return@runCatching if (!existingProjectId.isNullOrBlank()) {
                        workspaceManager.getProject(existingProjectId)
                            ?: workspaceManager.createProject(repo)
                    } else {
                        workspaceManager.createProject(repo)
                    }
                }
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty()
                    error(formatGitHubError(resp.code, errBody))
                }
                val stream = resp.body?.byteStream()
                    ?: error("GitHub returned an empty archive stream.")
                stream.use { input ->
                    workspaceManager.importGitHubZipball(
                        projectName = repo,
                        inputStream = input,
                        existingProjectId = existingProjectId
                    )
                }
            }
        }
    }

    // --- GitHub Actions Control & Logs ---

    suspend fun listWorkflows(
        token: String,
        owner: String,
        repo: String
    ): Result<List<GitHubWorkflowInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/actions/workflows?per_page=50"
            val root = getJson(url, token.trim())
            val arr = root["workflows"]?.jsonArray.orEmpty()
            arr.mapNotNull { elem ->
                val obj = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
                GitHubWorkflowInfo(
                    id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                    name = obj["name"]?.jsonPrimitive?.content ?: "Workflow",
                    path = obj["path"]?.jsonPrimitive?.content.orEmpty(),
                    state = obj["state"]?.jsonPrimitive?.content ?: "active"
                )
            }
        }
    }

    suspend fun dispatchWorkflow(
        token: String,
        owner: String,
        repo: String,
        workflowId: Long,
        ref: String,
        buildPlayBundle: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/actions/workflows/$workflowId/dispatches"
            val payload = buildJsonObject {
                put("ref", ref.trim().ifBlank { "main" })
                if (buildPlayBundle) {
                    put("inputs", buildJsonObject {
                        put("build_play_bundle", true)
                    })
                }
            }.toString()
            val (code, body) = executeRaw("POST", url, token.trim(), payload)
            if (code !in 200..299) {
                val baseErr = formatGitHubError(code, body)
                error("Workflow dispatch failed on '$ref': $baseErr")
            }
        }
    }

    suspend fun listWorkflowRuns(
        token: String,
        owner: String,
        repo: String,
        perPage: Int = 15
    ): Result<List<GitHubWorkflowRunSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "GitHub token is required." }
            val url = "https://api.github.com/repos/${enc(owner.trim())}/${enc(repo.trim())}/actions/runs?per_page=$perPage"
            val root = getJson(url, cleanToken)
            val runs = root["workflow_runs"]?.jsonArray.orEmpty()
            runs.mapNotNull { elem ->
                val latest = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
                GitHubWorkflowRunSummary(
                    id = latest["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    name = latest["name"]?.jsonPrimitive?.content ?: "Android Build",
                    status = latest["status"]?.jsonPrimitive?.content ?: "unknown",
                    conclusion = latest["conclusion"]?.jsonPrimitive?.content,
                    htmlUrl = latest["html_url"]?.jsonPrimitive?.content
                        ?: "https://github.com/${owner.trim()}/${repo.trim()}/actions",
                    createdAt = latest["created_at"]?.jsonPrimitive?.content.orEmpty(),
                    headBranch = latest["head_branch"]?.jsonPrimitive?.content.orEmpty(),
                    headSha = latest["head_sha"]?.jsonPrimitive?.content.orEmpty(),
                    event = latest["event"]?.jsonPrimitive?.content.orEmpty(),
                    runNumber = latest["run_number"]?.jsonPrimitive?.intOrNull ?: 0,
                    workflowId = latest["workflow_id"]?.jsonPrimitive?.longOrNull ?: 0L
                )
            }
        }
    }

    suspend fun fetchLatestWorkflowRun(
        token: String,
        owner: String,
        repo: String
    ): Result<GitHubWorkflowRunSummary?> = listWorkflowRuns(token, owner, repo, perPage = 1)
        .map { it.firstOrNull() }

    suspend fun rerunWorkflow(
        token: String,
        owner: String,
        repo: String,
        runId: Long,
        failedOnly: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val suffix = if (failedOnly) "rerun-failed-jobs" else "rerun"
            val url = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/actions/runs/$runId/$suffix"
            val (code, body) = executeRaw("POST", url, token.trim(), "{}")
            if (code !in 200..299) error(formatGitHubError(code, body))
        }
    }

    suspend fun cancelWorkflowRun(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/actions/runs/$runId/cancel"
            val (code, body) = executeRaw("POST", url, token.trim(), "{}")
            if (code !in 200..299) error(formatGitHubError(code, body))
        }
    }

    suspend fun fetchFailedWorkflowLogs(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<List<GitHubWorkflowJobLog>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            val jobsUrl = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/actions/runs/$runId/jobs?per_page=50"
            val jobsRoot = getJson(jobsUrl, cleanToken)
            val jobsArr = jobsRoot["jobs"]?.jsonArray.orEmpty()

            val targetJobs = jobsArr.mapNotNull { it.jsonObject }.filter { job ->
                val c = job["conclusion"]?.jsonPrimitive?.content
                c == "failure" || c == "startup_failure" || c == "timed_out"
            }.ifEmpty {
                jobsArr.mapNotNull { it.jsonObject }.take(2)
            }

            targetJobs.map { jobObj ->
                val jobId = jobObj["id"]?.jsonPrimitive?.longOrNull ?: 0L
                val jobName = jobObj["name"]?.jsonPrimitive?.content ?: "Job"
                val conclusion = jobObj["conclusion"]?.jsonPrimitive?.content
                val failedSteps = jobObj["steps"]?.jsonArray.orEmpty()
                    .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                    .filter { st ->
                        val sc = st["conclusion"]?.jsonPrimitive?.content
                        sc == "failure" || sc == "timed_out"
                    }
                    .mapNotNull { it["name"]?.jsonPrimitive?.content }

                val rawLogs = fetchJobLogText(cleanToken, owner, repo, jobId)
                val excerpt = extractUsefulLogExcerpt(rawLogs)

                GitHubWorkflowJobLog(
                    jobId = jobId,
                    jobName = jobName,
                    conclusion = conclusion,
                    failedSteps = failedSteps,
                    logExcerpt = excerpt
                )
            }
        }
    }

    private fun fetchJobLogText(
        token: String,
        owner: String,
        repo: String,
        jobId: Long
    ): String {
        if (jobId <= 0L) return ""
        val url = "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/actions/jobs/$jobId/logs"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "AI-Coder-Android")
            .get()
            .build()

        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                resp.body?.string().orEmpty()
            }
        }.getOrDefault("")
    }

    private fun extractUsefulLogExcerpt(raw: String): String {
        if (raw.isBlank()) return ""
        val lines = raw.lines()
        val errorLines = lines.filter { line ->
            line.contains("##[error]", ignoreCase = true) ||
                line.contains("error:", ignoreCase = true) ||
                line.contains("FAILURE:", ignoreCase = true) ||
                line.contains("Exception", ignoreCase = false) ||
                line.contains("Unresolved reference", ignoreCase = true) ||
                line.contains("What went wrong:", ignoreCase = true)
        }
        val selected = buildString {
            if (errorLines.isNotEmpty()) {
                appendLine("=== Detected Error Lines ===")
                appendLine(errorLines.takeLast(45).joinToString("\n"))
                appendLine()
            }
            appendLine("=== Log Tail ===")
            append(lines.takeLast(60).joinToString("\n"))
        }
        return selected.takeLast(5500)
    }

    // --- Atomic Push Project to GitHub ---

    suspend fun pushProject(
        projectId: String,
        token: String,
        ownerInput: String,
        repoInput: String,
        branchInput: String,
        commitMessageInput: String,
        isPrivate: Boolean,
        createIfMissing: Boolean,
        includeApkWorkflow: Boolean,
        onProgress: (String) -> Unit = {}
    ): Result<GitHubPushResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "GitHub Personal Access Token is required." }

            val cleanRepo = repoInput.trim()
                .replace(Regex("\\s+"), "-")
                .replace(Regex("[^A-Za-z0-9._-]"), "-")
                .trim('-')
            require(cleanRepo.isNotEmpty()) { "Repository name is required." }

            val cleanBranch = branchInput.trim().ifBlank { "main" }
            val commitMsg = commitMessageInput.trim().ifBlank { "Push project from IB CODER" }

            onProgress("Verifying GitHub account...")
            val userObj = getJson("https://api.github.com/user", cleanToken)
            val authedLogin = userObj["login"]?.jsonPrimitive?.content.orEmpty()
                .ifBlank { error("Could not resolve authenticated GitHub user.") }
            val owner = ownerInput.trim().ifBlank { authedLogin }

            workspaceManager.getProject(projectId)
                ?: error("Active workspace project not found.")

            // Optionally stage GitHub Actions APK build workflow into the workspace
            var workflowAdded = false
            if (includeApkWorkflow) {
                val buildApkStat = workspaceManager.statPath(projectId, ".github/workflows/build-apk.yml")
                val androidStat = workspaceManager.statPath(projectId, ".github/workflows/android.yml")
                if (!buildApkStat.exists && !androidStat.exists) {
                    workspaceManager.writeFile(
                        projectId,
                        ".github/workflows/build-apk.yml",
                        APK_BUILD_WORKFLOW_TEMPLATE
                    )
                    workflowAdded = true
                }
            }

            onProgress("Checking repository $owner/$cleanRepo...")
            val repoUrlApi = "https://api.github.com/repos/${enc(owner)}/${enc(cleanRepo)}"
            val (repoCode, repoBody) = executeRaw("GET", repoUrlApi, cleanToken, null)
            val repoJson: JsonObject = if (repoCode == 404) {
                if (!createIfMissing) {
                    error("Repository '$owner/$cleanRepo' not found. Enable 'Create repository if missing' or verify permissions.")
                }
                onProgress("Creating repository $owner/$cleanRepo...")
                val createPayload = buildJsonObject {
                    put("name", cleanRepo)
                    put("private", isPrivate)
                    put("auto_init", true)
                    put("description", "Android project created with IB CODER")
                }.toString()
                val createEndpoint = if (owner.equals(authedLogin, ignoreCase = true)) {
                    "https://api.github.com/user/repos"
                } else {
                    "https://api.github.com/orgs/${enc(owner)}/repos"
                }
                postJson(createEndpoint, cleanToken, createPayload)
            } else if (repoCode in 200..299) {
                json.parseToJsonElement(repoBody).jsonObject
            } else {
                error(formatGitHubError(repoCode, repoBody))
            }

            val defaultBranch = repoJson["default_branch"]?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() } ?: cleanBranch

            onProgress("Resolving branch '$cleanBranch'...")
            var branchExists = false
            var baseCommitSha: String? = null

            val targetRefUrl = "https://api.github.com/repos/${enc(owner)}/${enc(cleanRepo)}/git/ref/heads/${encPath(cleanBranch)}"
            val (refCode, refBody) = executeRaw("GET", targetRefUrl, cleanToken, null)
            when {
                refCode in 200..299 -> {
                    branchExists = true
                    baseCommitSha = json.parseToJsonElement(refBody).jsonObject["object"]
                        ?.jsonObject?.get("sha")?.jsonPrimitive?.content
                }
                refCode == 404 && !cleanBranch.equals(defaultBranch, ignoreCase = true) -> {
                    val defaultRefUrl = "https://api.github.com/repos/${enc(owner)}/${enc(cleanRepo)}/git/ref/heads/${encPath(defaultBranch)}"
                    val (defCode, defBody) = executeRaw("GET", defaultRefUrl, cleanToken, null)
                    if (defCode in 200..299) {
                        baseCommitSha = json.parseToJsonElement(defBody).jsonObject["object"]
                            ?.jsonObject?.get("sha")?.jsonPrimitive?.content
                    }
                }
            }

            if (baseCommitSha.isNullOrBlank()) {
                onProgress("Initializing empty repository...")
                val initPayload = buildJsonObject {
                    put("message", "Initialize repository")
                    put("branch", cleanBranch)
                    put(
                        "content",
                        Base64.getEncoder().encodeToString(
                            "# $cleanRepo\n\nPushed from IB CODER.\n".toByteArray(Charsets.UTF_8)
                        )
                    )
                }.toString()
                val (initCode, initBody) = executeRaw(
                    "PUT",
                    "https://api.github.com/repos/${enc(owner)}/${enc(cleanRepo)}/contents/README.md",
                    cleanToken,
                    initPayload
                )
                if (initCode !in 200..299) {
                    error(formatGitHubError(initCode, initBody))
                }
                val initObj = json.parseToJsonElement(initBody).jsonObject
                baseCommitSha = initObj["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
                    ?: error("Repository initialized, but commit SHA was not returned.")
                branchExists = true
            }

            val filesToUpload = workspaceManager.collectProjectFilesForSync(
                projectId = projectId,
                ignoredDirs = IGNORED_DIRS,
                maxBlobBytes = MAX_BLOB_BYTES
            )

            if (filesToUpload.isEmpty()) {
                error("Workspace has no files to push.")
            }

            val total = filesToUpload.size
            val treeEntries = buildJsonArray {
                filesToUpload.forEachIndexed { index, entry ->
                    val relPath = entry.relativePath.replace('\\', '/').trimStart('/')
                    if (relPath.isNotBlank()) {
                        onProgress("Uploading files (${index + 1}/$total): $relPath")
                        val b64 = Base64.getEncoder().encodeToString(entry.bytes)
                        val blobPayload = buildJsonObject {
                            put("content", b64)
                            put("encoding", "base64")
                        }.toString()
                        val blobResp = postJson(
                            "https://api.github.com/repos/${enc(owner)}/${enc(cleanRepo)}/git/blobs",
                            cleanToken,
                            blobPayload
                        )
                        val blobSha = blobResp["sha"]?.jsonPrimitive?.content
                            ?: error("GitHub did not return blob SHA for '$relPath'.")
                        val mode = if (entry.isExecutable) "100755" else "100644"
                        add(
                            buildJsonObject {
                                put("path", relPath)
                                put("mode", mode)
                                put("type", "blob")
                                put("sha", blobSha)
                            }
                        )
                    }
                }
            }

            onProgress("Creating Git tree...")
            val treePayload = buildJsonObject {
                put("tree", treeEntries)
            }.toString()
            val treeResp = postJson(
                "https://api.github.com/repos/${enc(owner)}/${enc(cleanRepo)}/git/trees",
                cleanToken,
                treePayload
            )
            val newTreeSha = treeResp["sha"]?.jsonPrimitive?.content
                ?: error("GitHub did not return tree SHA.")

            onProgress("Creating commit...")
            val commitPayload = buildJsonObject {
                put("message", commitMsg)
                put("tree", newTreeSha)
                put("parents", buildJsonArray { add(JsonPrimitive(baseCommitSha)) })
            }.toString()
            val commitResp = postJson(
                "https://api.github.com/repos/${enc(owner)}/${enc(cleanRepo)}/git/commits",
                cleanToken,
                commitPayload
            )
            val newCommitSha = commitResp["sha"]?.jsonPrimitive?.content
                ?: error("GitHub did not return commit SHA.")

            onProgress("Updating branch '$cleanBranch'...")
            if (branchExists) {
                val patchPayload = buildJsonObject {
                    put("sha", newCommitSha)
                    put("force", true)
                }.toString()
                val (patchCode, patchBody) = executeRaw(
                    "PATCH",
                    "https://api.github.com/repos/${enc(owner)}/${enc(cleanRepo)}/git/refs/heads/${encPath(cleanBranch)}",
                    cleanToken,
                    patchPayload
                )
                if (patchCode !in 200..299) {
                    error(formatGitHubError(patchCode, patchBody))
                }
            } else {
                val createRefPayload = buildJsonObject {
                    put("ref", "refs/heads/$cleanBranch")
                    put("sha", newCommitSha)
                }.toString()
                postJson(
                    "https://api.github.com/repos/${enc(owner)}/${enc(cleanRepo)}/git/refs",
                    cleanToken,
                    createRefPayload
                )
            }

            val htmlUrl = repoJson["html_url"]?.jsonPrimitive?.content
                ?: "https://github.com/$owner/$cleanRepo"

            GitHubPushResult(
                owner = owner,
                repo = cleanRepo,
                branch = cleanBranch,
                commitSha = newCommitSha,
                repoUrl = htmlUrl,
                actionsUrl = "$htmlUrl/actions",
                filesUploaded = filesToUpload.size,
                workflowAdded = workflowAdded
            )
        }
    }

    private fun parseRepoSummary(obj: JsonObject): GitHubRepositorySummary {
        val ownerObj = obj["owner"]?.jsonObject
        return GitHubRepositorySummary(
            id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
            fullName = obj["full_name"]?.jsonPrimitive?.content.orEmpty(),
            ownerLogin = ownerObj?.get("login")?.jsonPrimitive?.content.orEmpty(),
            description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
            isPrivate = obj["private"]?.jsonPrimitive?.booleanOrNull ?: false,
            defaultBranch = obj["default_branch"]?.jsonPrimitive?.content ?: "main",
            htmlUrl = obj["html_url"]?.jsonPrimitive?.content.orEmpty(),
            updatedAt = obj["updated_at"]?.jsonPrimitive?.content.orEmpty(),
            language = obj["language"]?.jsonPrimitive?.content.orEmpty()
        )
    }

    private fun getJson(url: String, token: String): JsonObject {
        val (code, body) = executeRaw("GET", url, token, null)
        if (code !in 200..299) error(formatGitHubError(code, body))
        return json.parseToJsonElement(body).jsonObject
    }

    private fun postJson(url: String, token: String, payload: String): JsonObject {
        val (code, body) = executeRaw("POST", url, token, payload)
        if (code !in 200..299) error(formatGitHubError(code, body))
        return json.parseToJsonElement(body).jsonObject
    }

    private fun executeRaw(
        method: String,
        url: String,
        token: String,
        jsonPayload: String?
    ): Pair<Int, String> {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "AI-Coder-Android")

        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete(jsonPayload?.toRequestBody(jsonMediaType))
            "POST" -> builder.post((jsonPayload ?: "{}").toRequestBody(jsonMediaType))
            "PUT" -> builder.put((jsonPayload ?: "{}").toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch((jsonPayload ?: "{}").toRequestBody(jsonMediaType))
            else -> builder.method(method, jsonPayload?.toRequestBody(jsonMediaType))
        }

        okHttpClient.newCall(builder.build()).execute().use { response ->
            return response.code to (response.body?.string().orEmpty())
        }
    }

    private fun formatGitHubError(code: Int, body: String): String {
        val parsed = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["message"]?.jsonPrimitive?.content
        }.getOrNull()

        return when (code) {
            401 -> "GitHub authentication failed (401). Sign in again or verify your token."
            403 -> "GitHub permission or rate limit error (403): ${parsed ?: "Ensure token has 'repo' and 'workflow' scopes."}"
            404 -> "GitHub resource not found (404): ${parsed ?: "Check owner, repository name, and token scopes ('repo', 'workflow')."}"
            422 -> "GitHub rejected request (422): ${parsed ?: body.take(240)}"
            else -> "GitHub API error ($code): ${parsed ?: body.take(240)}"
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun encPath(branch: String): String =
        branch.split('/').joinToString("/") { enc(it) }

    companion object {
        private const val MAX_BLOB_BYTES = 20 * 1024 * 1024
        private val IGNORED_DIRS = setOf(".git", ".gradle", "build", ".idea")

        val APK_BUILD_WORKFLOW_TEMPLATE = """
name: Build Debug APK & Play Store Bundle

on:
  push:
  workflow_dispatch:
    inputs:
      build_play_bundle:
        description: 'Also build signed Play Store .aab (requires signing secrets)'
        required: false
        default: false
        type: boolean

permissions:
  contents: write

jobs:
  build-apk:
    if: "github.event_name == 'workflow_dispatch' || !contains(github.event.head_commit.message, '[skip ci]')"
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v4
        with:
          packages: 'platform-tools'

      - name: Install Android SDK Platforms
        run: |
          yes | sdkmanager --licenses > /dev/null || true
          sdkmanager "platforms;android-36" "platforms;android-35" "build-tools;36.0.0" "build-tools;35.0.0"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.11.1'

      - name: Ensure Gradle wrapper is executable
        run: |
          if [ ! -f gradlew ] || [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
            gradle wrapper --gradle-version 8.11.1
          fi
          chmod +x gradlew

      - name: Ensure debug keystore exists
        run: |
          if [ ! -f debug.keystore ]; then
            keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
          fi

      - name: Build Debug APK
        run: ./gradlew assembleDebug --no-daemon

      - name: Collect Debug APK (src/app-test.apk)
        run: |
          APK_PATH=${'$'}(find . -path "*/build/outputs/apk/debug/*.apk" -type f | head -n 1)
          if [ -z "${'$'}APK_PATH" ]; then
            echo "No debug APK generated."
            exit 1
          fi
          mkdir -p src release
          cp "${'$'}APK_PATH" src/app-test.apk
          cp "${'$'}APK_PATH" release/app-debug.apk

      - name: Upload Debug APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: src/app-test.apk
          if-no-files-found: error

      - name: Commit Debug APK to src/
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add -f src/app-test.apk
          if ! git diff --cached --quiet; then
            git commit -m "Build debug APK: src/app-test.apk [skip ci]"
            git push || true
          fi

  build-play-bundle:
    if: "(github.event_name == 'workflow_dispatch' && inputs.build_play_bundle == true) || vars.ENABLE_PLAY_BUNDLE == 'true' || (github.event_name == 'push' && !contains(github.event.head_commit.message, '[skip ci]'))"
    needs: build-apk
    runs-on: ubuntu-latest

    steps:
      - name: Check Play Store signing secrets
        id: check_secrets
        env:
          KEYSTORE_BASE64: ${'$'}{{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${'$'}{{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${'$'}{{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${'$'}{{ secrets.KEY_PASSWORD }}
          FORCE_REQUIRED: ${'$'}{{ (github.event_name == 'workflow_dispatch' && inputs.build_play_bundle == true) || vars.ENABLE_PLAY_BUNDLE == 'true' }}
        run: |
          missing=0
          for var_name in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
            if [ -z "${'$'}{!var_name}" ]; then
              missing=1
              if [ "${'$'}FORCE_REQUIRED" = "true" ]; then
                echo "::error::Missing required GitHub repository secret: ${'$'}var_name"
              fi
            fi
          done
          if [ "${'$'}missing" -ne 0 ]; then
            if [ "${'$'}FORCE_REQUIRED" = "true" ]; then
              echo "Add KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD under GitHub -> Repository Settings -> Secrets and variables -> Actions."
              exit 1
            else
              echo "::notice::Skipping src/app-release.aab until Play Store signing secrets are added."
              echo "ready=false" >> "${'$'}GITHUB_OUTPUT"
            fi
          else
            echo "ready=true" >> "${'$'}GITHUB_OUTPUT"
          fi

      - name: Checkout repository
        if: steps.check_secrets.outputs.ready == 'true'
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Set up JDK 17
        if: steps.check_secrets.outputs.ready == 'true'
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Set up Android SDK
        if: steps.check_secrets.outputs.ready == 'true'
        uses: android-actions/setup-android@v4
        with:
          packages: 'platform-tools'

      - name: Install Android SDK Platforms
        if: steps.check_secrets.outputs.ready == 'true'
        run: |
          yes | sdkmanager --licenses > /dev/null || true
          sdkmanager "platforms;android-36" "platforms;android-35" "build-tools;36.0.0" "build-tools;35.0.0"

      - name: Set up Gradle
        if: steps.check_secrets.outputs.ready == 'true'
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.11.1'

      - name: Decode upload keystore
        if: steps.check_secrets.outputs.ready == 'true'
        env:
          KEYSTORE_BASE64: ${'$'}{{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo "${'$'}KEYSTORE_BASE64" | tr -d '\n\r ' | base64 --decode > "${'$'}RUNNER_TEMP/upload-keystore.jks"
          test -s "${'$'}RUNNER_TEMP/upload-keystore.jks"

      - name: Build signed Play Store AAB
        if: steps.check_secrets.outputs.ready == 'true'
        env:
          KEYSTORE_PATH: ${'$'}{{ runner.temp }}/upload-keystore.jks
          STORE_PASSWORD: ${'$'}{{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${'$'}{{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${'$'}{{ secrets.KEY_PASSWORD }}
        run: |
          chmod +x gradlew || true
          ./gradlew bundleRelease --no-daemon \
            -Pandroid.injected.signing.store.file="${'$'}KEYSTORE_PATH" \
            -Pandroid.injected.signing.store.password="${'$'}STORE_PASSWORD" \
            -Pandroid.injected.signing.key.alias="${'$'}KEY_ALIAS" \
            -Pandroid.injected.signing.key.password="${'$'}KEY_PASSWORD"

      - name: Collect Play Store AAB (src/app-release.aab)
        if: steps.check_secrets.outputs.ready == 'true'
        run: |
          AAB_PATH=${'$'}(find . -path "*/build/outputs/bundle/release/*.aab" -type f | head -n 1)
          if [ -z "${'$'}AAB_PATH" ]; then
            echo "No release AAB generated."
            exit 1
          fi
          mkdir -p src
          cp "${'$'}AAB_PATH" src/app-release.aab

      - name: Upload Play Store AAB artifact
        if: steps.check_secrets.outputs.ready == 'true'
        uses: actions/upload-artifact@v4
        with:
          name: play-bundle
          path: src/app-release.aab
          if-no-files-found: error

      - name: Commit Play Store AAB to src/
        if: steps.check_secrets.outputs.ready == 'true'
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git pull --rebase || true
          git add -f src/app-release.aab
          if ! git diff --cached --quiet; then
            git commit -m "Build Play Store bundle: src/app-release.aab [skip ci]"
            git push || true
          fi

      - name: Remove temporary keystore
        if: always() && steps.check_secrets.outputs.ready == 'true'
        run: rm -f "${'$'}RUNNER_TEMP/upload-keystore.jks"
        """.trimIndent() + "\n"
    }
}
