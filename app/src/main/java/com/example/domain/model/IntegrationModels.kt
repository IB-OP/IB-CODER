package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GitHubProjectConfig(
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val isPrivate: Boolean = true,
    val createIfMissing: Boolean = true,
    val includeApkWorkflow: Boolean = true,
    val autoSyncEnabled: Boolean = false,
    val autoFixWorkflowBugs: Boolean = false,
    val directGitHubMode: Boolean = false,
    val lastCommitSha: String = "",
    val lastRepoUrl: String = "",
    val lastActionsUrl: String = "",
    val lastPushedAt: Long = 0L
)

@Serializable
data class GitHubUserProfile(
    val login: String,
    val name: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val publicRepos: Int = 0,
    val totalPrivateRepos: Int = 0
)

@Serializable
data class AppAccountSession(
    val isSignedIn: Boolean = false,
    val username: String = "",
    val displayName: String = "",
    val authProvider: String = "",
    val requireSignInOnLaunch: Boolean = false,
    val signedInAt: Long = 0L
)

data class GitHubDeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

data class GitHubRepositorySummary(
    val id: Long,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val description: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val htmlUrl: String,
    val updatedAt: String,
    val language: String
)

data class GitHubBranchInfo(
    val name: String,
    val commitSha: String,
    val isProtected: Boolean
)

data class GitHubWorkflowInfo(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

data class GitHubWorkflowJobLog(
    val jobId: Long,
    val jobName: String,
    val conclusion: String?,
    val failedSteps: List<String>,
    val logExcerpt: String
)

data class GitHubPushResult(
    val owner: String,
    val repo: String,
    val branch: String,
    val commitSha: String,
    val repoUrl: String,
    val actionsUrl: String,
    val filesUploaded: Int,
    val workflowAdded: Boolean
)

data class GitHubWorkflowRunSummary(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String,
    val createdAt: String,
    val headBranch: String = "",
    val headSha: String = "",
    val event: String = "",
    val runNumber: Int = 0,
    val workflowId: Long = 0L
)

sealed class GitHubSyncStatus {
    data object Idle : GitHubSyncStatus()
    data class Syncing(val step: String) : GitHubSyncStatus()
    data class Synced(val commitSha: String, val timestamp: Long, val filesUploaded: Int) : GitHubSyncStatus()
    data class Error(val message: String) : GitHubSyncStatus()
}

data class WorkflowFailureAlert(
    val projectId: String?,
    val owner: String,
    val repo: String,
    val branch: String,
    val workflowName: String,
    val summary: String,
    val aiFixPrompt: String
)

@Serializable
enum class McpTransport(val displayName: String) {
    AUTO("Auto (Streamable HTTP / SSE)"),
    STREAMABLE_HTTP("Streamable HTTP"),
    LEGACY_SSE("Legacy HTTP + SSE")
}

@Serializable
data class McpToolInfo(
    val name: String,
    val description: String = "",
    val inputSchemaJson: String = """{"type":"object","properties":{}}"""
)

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val transport: McpTransport = McpTransport.AUTO,
    val authHeaderName: String = "Authorization",
    val enabled: Boolean = true,
    val discoveredTools: List<McpToolInfo> = emptyList(),
    val serverInfoName: String = "",
    val protocolVersion: String = "",
    val lastCheckedAt: Long = 0L,
    val lastError: String? = null
)

data class ResolvedMcpTool(
    val exposedName: String,
    val serverId: String,
    val serverName: String,
    val originalToolName: String,
    val description: String,
    val parametersSchemaJson: String
)
