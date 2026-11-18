package com.example.data.remote

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.preferences.CloudVaultMutation
import com.example.data.preferences.SettingsDataStore
import com.example.domain.model.AppAccountSession
import com.example.domain.model.CustomProviderDefinition
import com.example.domain.model.GitHubProjectConfig
import com.example.domain.model.McpServerConfig
import com.example.domain.model.McpTransport
import com.example.domain.model.ProviderConfig
import com.example.domain.model.SessionMode
import com.example.domain.model.SessionPromptConfig
import com.example.domain.model.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

sealed class CloudVaultSyncStatus {
    data object SignedOut : CloudVaultSyncStatus()
    data class Syncing(val detail: String) : CloudVaultSyncStatus()
    data class Synced(
        val lastSyncedAt: Long,
        val syncedKeysCount: Int,
        val customProvidersCount: Int,
        val mcpCount: Int,
        val hasGitHubToken: Boolean
    ) : CloudVaultSyncStatus()
    data class Error(val message: String) : CloudVaultSyncStatus()
}

class CloudAccountVaultRepository(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val json: Json
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val auth: FirebaseAuth? by lazy {
        runCatching { Firebase.auth }.getOrNull()
    }

    private val firestore: FirebaseFirestore? by lazy {
        runCatching {
            val dbId = context.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(dbId)
        }.getOrNull()
    }

    private val _syncStatus = MutableStateFlow<CloudVaultSyncStatus>(CloudVaultSyncStatus.SignedOut)
    val syncStatus: StateFlow<CloudVaultSyncStatus> = _syncStatus.asStateFlow()

    private val activeListeners = mutableListOf<ListenerRegistration>()
    private var mutationObserverJob: Job? = null
    private var activeUid: String? = null

    private val _authState = MutableStateFlow(auth?.currentUser)
    val authState: StateFlow<FirebaseUser?> = _authState.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { fa ->
        _authState.value = fa.currentUser
    }

    init {
        auth?.addAuthStateListener(authStateListener)
    }

    fun authStateFlow(): Flow<FirebaseUser?> = authState

    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not initialized."))
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Google sign-in returned no user."))
            _authState.value = user
            settingsDataStore.setGuestMode(false)
            updateLocalSessionFromFirebaseUser(user)
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startSyncForUser(user: FirebaseUser): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore is unavailable."))
        if (activeUid == user.uid && mutationObserverJob?.isActive == true) {
            return Result.success(Unit)
        }
        stopListenersOnly()
        activeUid = user.uid
        updateLocalSessionFromFirebaseUser(user)

        return try {
            _syncStatus.value = CloudVaultSyncStatus.Syncing("Restoring your account vault...")
            pullAndMergeWithCloud(db, user)
            attachRealtimeListeners(db, user)
            startMutationObserver(db, user)
            refreshSyncedStatusSummary()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.READ, "users/${user.uid}")
            _syncStatus.value = CloudVaultSyncStatus.Error(msg)
            Result.failure(e)
        }
    }

    suspend fun syncNow(): Result<Unit> {
        val user = auth?.currentUser ?: return Result.failure(IllegalStateException("Not signed in."))
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore is unavailable."))
        return try {
            _syncStatus.value = CloudVaultSyncStatus.Syncing("Syncing API keys, tokens & settings...")
            pushAllLocalToCloud(db, user)
            pullAndMergeWithCloud(db, user)
            refreshSyncedStatusSummary()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.WRITE, "users/${user.uid}")
            _syncStatus.value = CloudVaultSyncStatus.Error(msg)
            Result.failure(e)
        }
    }

    suspend fun signOutAccount(
        credentialManager: CredentialManager,
        clearLocalVaultSecrets: Boolean = true
    ): Result<Unit> = withContext(NonCancellable) {
        try {
            stopListenersOnly()
            activeUid = null
            _syncStatus.value = CloudVaultSyncStatus.SignedOut
            runCatching {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
            auth?.signOut()
            _authState.value = null
            settingsDataStore.setGuestMode(false)
            if (clearLocalVaultSecrets) {
                settingsDataStore.clearAccountVaultSecretsForSignOut()
            } else {
                settingsDataStore.signOutApp(clearGitHubToken = false)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateLocalSessionFromFirebaseUser(user: FirebaseUser) {
        val existing = settingsDataStore.appAccountSessionFlow.first()
        val label = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "Developer"
        settingsDataStore.saveAppAccountSession(
            existing.copy(
                isSignedIn = true,
                username = user.email ?: label,
                displayName = label,
                authProvider = "Google",
                signedInAt = System.currentTimeMillis()
            )
        )
    }

    private fun stopListenersOnly() {
        mutationObserverJob?.cancel()
        mutationObserverJob = null
        activeListeners.forEach { reg -> runCatching { reg.remove() } }
        activeListeners.clear()
    }

    private suspend fun pullAndMergeWithCloud(db: FirebaseFirestore, user: FirebaseUser) {
        val userDocRef = db.collection("users").document(user.uid)
        val userSnap = userDocRef.get().await()

        if (!userSnap.exists()) {
            // New account vault on cloud — seed whatever user already configured locally
            pushUserProfileAndGlobalSettings(db, user)
        } else {
            val data = userSnap.data.orEmpty()
            val cloudTheme = (data["themeMode"] as? String)?.let { raw ->
                enumValues<ThemeMode>().firstOrNull { it.name == raw }
            }
            val cloudProvider = (data["defaultProviderId"] as? String)?.takeIf { it.isNotBlank() }
            val cloudModel = data["defaultModelId"] as? String
            val cloudPrompt = (data["systemPrompt"] as? String)?.takeIf { it.isNotBlank() }
            val cloudGhToken = (data["githubToken"] as? String)?.takeIf { it.isNotBlank() }
            val cloudGhOwner = (data["githubDefaultOwner"] as? String)?.takeIf { it.isNotBlank() }
            val cloudGhProf = (data["githubUserProfileJson"] as? String)?.takeIf { it.isNotBlank() && it != "{}" }

            settingsDataStore.applyCloudGlobalSettings(
                themeMode = cloudTheme,
                defaultProviderId = cloudProvider,
                defaultModelId = cloudModel,
                systemPrompt = cloudPrompt,
                githubToken = cloudGhToken,
                githubDefaultOwner = cloudGhOwner,
                githubUserProfileJson = cloudGhProf
            )

            // If cloud didn't have a GitHub token yet, push local token if present
            if (cloudGhToken == null && settingsDataStore.getGitHubToken().isNotBlank()) {
                pushUserProfileAndGlobalSettings(db, user)
            }
        }

        // 2. Restore Provider Keys & Configs
        val keySnaps = userDocRef.collection("providerKeys").get().await()
        val cloudConfigs = mutableMapOf<String, ProviderConfig>()
        val cloudApiKeys = mutableMapOf<String, String>()
        for (doc in keySnaps.documents) {
            val parsed = parseProviderDoc(doc.id, doc.data.orEmpty())
            cloudConfigs[parsed.first.providerId] = parsed.first
            if (parsed.second.isNotBlank()) {
                cloudApiKeys[parsed.first.providerId] = parsed.second
            }
        }
        settingsDataStore.applyCloudProviderEntries(cloudConfigs, cloudApiKeys)

        val localConfigs = settingsDataStore.getAllProviderConfigs()
        val localKeys = settingsDataStore.getAllStoredApiKeys()
        val localProviderIdsToSeed = (localConfigs.keys + localKeys.keys) - cloudConfigs.keys
        for (pid in localProviderIdsToSeed) {
            pushSingleProviderVault(db, user.uid, pid)
        }

        // 3. Restore Custom Providers
        val customSnaps = userDocRef.collection("customProviders").get().await()
        val cloudCustom = customSnaps.documents.mapNotNull { parseCustomProviderDoc(it.id, it.data.orEmpty()) }
        if (cloudCustom.isNotEmpty()) {
            settingsDataStore.applyCloudCustomProviders(cloudCustom)
        }
        val cloudCustomIds = cloudCustom.map { it.id }.toSet()
        for (localDef in settingsDataStore.getCustomProviders()) {
            if (localDef.id !in cloudCustomIds) {
                pushCustomProvider(db, user.uid, localDef)
            }
        }

        // 4. Restore MCP Servers & Tokens
        val mcpSnaps = userDocRef.collection("mcpServers").get().await()
        val cloudServers = mutableListOf<McpServerConfig>()
        val cloudMcpTokens = mutableMapOf<String, String>()
        for (doc in mcpSnaps.documents) {
            val (srv, tok) = parseMcpDoc(doc.id, doc.data.orEmpty()) ?: continue
            cloudServers += srv
            if (tok.isNotBlank()) cloudMcpTokens[srv.id] = tok
        }
        if (cloudServers.isNotEmpty() || cloudMcpTokens.isNotEmpty()) {
            settingsDataStore.applyCloudMcpServers(cloudServers, cloudMcpTokens)
        }
        val cloudMcpIds = cloudServers.map { it.id }.toSet()
        for (localSrv in settingsDataStore.getMcpServers()) {
            if (localSrv.id !in cloudMcpIds) {
                pushMcpServer(db, user.uid, localSrv.id)
            }
        }

        // 5. Restore GitHub Project Configs
        val ghSnaps = userDocRef.collection("githubProjects").get().await()
        val cloudGhProjects = mutableMapOf<String, GitHubProjectConfig>()
        for (doc in ghSnaps.documents) {
            val parsed = parseGitHubProjectDoc(doc.data.orEmpty()) ?: continue
            cloudGhProjects[doc.id] = parsed
        }
        settingsDataStore.applyCloudGitHubProjects(cloudGhProjects)
        for ((projId, _) in settingsDataStore.getGitHubProjectConfigs()) {
            if (projId !in cloudGhProjects) {
                pushGitHubProject(db, user.uid, projId)
            }
        }

        // 6. Restore Session Prompt Configs
        val sessSnaps = userDocRef.collection("sessionConfigs").get().await()
        val cloudSessions = mutableMapOf<String, SessionPromptConfig>()
        for (doc in sessSnaps.documents) {
            val parsed = parseSessionConfigDoc(doc.data.orEmpty()) ?: continue
            cloudSessions[doc.id] = parsed
        }
        settingsDataStore.applyCloudSessionPromptConfigs(cloudSessions)
        for ((sessId, _) in settingsDataStore.getAllSessionPromptConfigs()) {
            if (sessId !in cloudSessions) {
                pushSessionConfig(db, user.uid, sessId)
            }
        }
    }

    private suspend fun pushAllLocalToCloud(db: FirebaseFirestore, user: FirebaseUser) {
        pushUserProfileAndGlobalSettings(db, user)

        val localConfigs = settingsDataStore.getAllProviderConfigs()
        val localKeys = settingsDataStore.getAllStoredApiKeys()
        for (pid in (localConfigs.keys + localKeys.keys)) {
            pushSingleProviderVault(db, user.uid, pid)
        }

        for (def in settingsDataStore.getCustomProviders()) {
            pushCustomProvider(db, user.uid, def)
        }

        for (srv in settingsDataStore.getMcpServers()) {
            pushMcpServer(db, user.uid, srv.id)
        }

        for ((projId, _) in settingsDataStore.getGitHubProjectConfigs()) {
            pushGitHubProject(db, user.uid, projId)
        }

        for ((sessId, _) in settingsDataStore.getAllSessionPromptConfigs()) {
            pushSessionConfig(db, user.uid, sessId)
        }
    }

    private fun attachRealtimeListeners(db: FirebaseFirestore, user: FirebaseUser) {
        val userRef = db.collection("users").document(user.uid)

        activeListeners += userRef.addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists() || snap.metadata.hasPendingWrites()) return@addSnapshotListener
            val data = snap.data.orEmpty()
            repoScope.launch {
                settingsDataStore.applyCloudGlobalSettings(
                    themeMode = (data["themeMode"] as? String)?.let { raw ->
                        enumValues<ThemeMode>().firstOrNull { it.name == raw }
                    },
                    defaultProviderId = (data["defaultProviderId"] as? String)?.takeIf { it.isNotBlank() },
                    defaultModelId = data["defaultModelId"] as? String,
                    systemPrompt = (data["systemPrompt"] as? String)?.takeIf { it.isNotBlank() },
                    githubToken = (data["githubToken"] as? String)?.takeIf { it.isNotBlank() },
                    githubDefaultOwner = (data["githubDefaultOwner"] as? String)?.takeIf { it.isNotBlank() },
                    githubUserProfileJson = (data["githubUserProfileJson"] as? String)?.takeIf { it.isNotBlank() && it != "{}" }
                )
                refreshSyncedStatusSummary()
            }
        }

        activeListeners += userRef.collection("providerKeys").addSnapshotListener { snaps, err ->
            if (err != null || snaps == null || snaps.metadata.hasPendingWrites()) return@addSnapshotListener
            repoScope.launch {
                val configs = mutableMapOf<String, ProviderConfig>()
                val keys = mutableMapOf<String, String>()
                for (doc in snaps.documents) {
                    val (cfg, apiKey) = parseProviderDoc(doc.id, doc.data.orEmpty())
                    configs[cfg.providerId] = cfg
                    if (apiKey.isNotBlank()) keys[cfg.providerId] = apiKey
                }
                settingsDataStore.applyCloudProviderEntries(configs, keys)
                refreshSyncedStatusSummary()
            }
        }

        activeListeners += userRef.collection("customProviders").addSnapshotListener { snaps, err ->
            if (err != null || snaps == null || snaps.metadata.hasPendingWrites()) return@addSnapshotListener
            repoScope.launch {
                val defs = snaps.documents.mapNotNull { parseCustomProviderDoc(it.id, it.data.orEmpty()) }
                if (defs.isNotEmpty()) {
                    settingsDataStore.applyCloudCustomProviders(defs)
                    refreshSyncedStatusSummary()
                }
            }
        }

        activeListeners += userRef.collection("mcpServers").addSnapshotListener { snaps, err ->
            if (err != null || snaps == null || snaps.metadata.hasPendingWrites()) return@addSnapshotListener
            repoScope.launch {
                val list = mutableListOf<McpServerConfig>()
                val tokens = mutableMapOf<String, String>()
                for (doc in snaps.documents) {
                    val (srv, tok) = parseMcpDoc(doc.id, doc.data.orEmpty()) ?: continue
                    list += srv
                    if (tok.isNotBlank()) tokens[srv.id] = tok
                }
                if (list.isNotEmpty() || tokens.isNotEmpty()) {
                    settingsDataStore.applyCloudMcpServers(list, tokens)
                    refreshSyncedStatusSummary()
                }
            }
        }
    }

    private fun startMutationObserver(db: FirebaseFirestore, user: FirebaseUser) {
        mutationObserverJob?.cancel()
        mutationObserverJob = repoScope.launch {
            settingsDataStore.cloudVaultMutations.collect { event ->
                val currentUser = auth?.currentUser
                if (currentUser == null || currentUser.uid != user.uid) return@collect
                try {
                    when (event) {
                        CloudVaultMutation.GlobalSettingsOrGitHubAccount ->
                            pushUserProfileAndGlobalSettings(db, currentUser)
                        is CloudVaultMutation.ProviderChanged ->
                            pushSingleProviderVault(db, currentUser.uid, event.providerId)
                        is CloudVaultMutation.CustomProviderUpserted ->
                            pushCustomProvider(db, currentUser.uid, event.definition)
                        is CloudVaultMutation.CustomProviderDeleted -> {
                            db.collection("users").document(currentUser.uid)
                                .collection("customProviders").document(event.providerId)
                                .delete().await()
                            db.collection("users").document(currentUser.uid)
                                .collection("providerKeys").document(event.providerId)
                                .delete().await()
                        }
                        is CloudVaultMutation.McpServerUpserted ->
                            pushMcpServer(db, currentUser.uid, event.serverId)
                        is CloudVaultMutation.McpServerDeleted ->
                            db.collection("users").document(currentUser.uid)
                                .collection("mcpServers").document(event.serverId)
                                .delete().await()
                        is CloudVaultMutation.GitHubProjectChanged ->
                            pushGitHubProject(db, currentUser.uid, event.projectId)
                        is CloudVaultMutation.SessionConfigUpserted ->
                            pushSessionConfig(db, currentUser.uid, event.sessionId)
                        is CloudVaultMutation.SessionConfigDeleted ->
                            db.collection("users").document(currentUser.uid)
                                .collection("sessionConfigs").document(event.sessionId)
                                .delete().await()
                    }
                    refreshSyncedStatusSummary()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = handleFirestoreError(e, OperationType.WRITE, "users/${currentUser.uid}")
                    _syncStatus.value = CloudVaultSyncStatus.Error(msg)
                }
            }
        }
    }

    private suspend fun pushUserProfileAndGlobalSettings(db: FirebaseFirestore, user: FirebaseUser) {
        val settings = settingsDataStore.getSettings()
        val ghToken = settingsDataStore.getGitHubToken()
        val ghOwner = settingsDataStore.githubDefaultOwnerFlow.first()
        val ghProf = settingsDataStore.githubUserProfileFlow.first()
        val ghProfJson = ghProf?.let { json.encodeToString(it) } ?: "{}"

        val payload = mapOf(
            "uid" to user.uid,
            "email" to (user.email ?: "").take(320),
            "displayName" to (user.displayName?.takeIf { it.isNotBlank() } ?: user.email ?: "Developer").take(200),
            "photoUrl" to (user.photoUrl?.toString() ?: "").take(1000),
            "themeMode" to settings.themeMode.name,
            "defaultProviderId" to settings.defaultProviderId.take(120),
            "defaultModelId" to settings.defaultModelId.take(240),
            "systemPrompt" to settings.systemPrompt.take(24000),
            "githubToken" to ghToken.take(1024),
            "githubDefaultOwner" to ghOwner.take(120),
            "githubUserProfileJson" to ghProfJson.take(8000),
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(user.uid)
            .set(payload, SetOptions.merge())
            .await()
    }

    private suspend fun pushSingleProviderVault(db: FirebaseFirestore, uid: String, providerId: String) {
        val cfg = settingsDataStore.getProviderConfig(providerId)
            ?: ProviderConfig(providerId = providerId, baseUrl = "")
        val key = settingsDataStore.getApiKey(providerId)
        val payload = mapOf(
            "providerId" to providerId.take(120),
            "apiKey" to key.take(2048),
            "baseUrl" to cfg.baseUrl.take(512),
            "selectedModel" to cfg.selectedModel.take(240),
            "enabled" to cfg.enabled,
            "accountId" to cfg.accountId.take(160),
            "deploymentName" to cfg.deploymentName.take(160),
            "fetchedModels" to cfg.fetchedModels.take(500),
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid)
            .collection("providerKeys").document(providerId)
            .set(payload)
            .await()
    }

    private suspend fun pushCustomProvider(db: FirebaseFirestore, uid: String, def: CustomProviderDefinition) {
        val payload = mapOf(
            "id" to def.id.take(120),
            "displayName" to def.displayName.ifBlank { def.id }.take(120),
            "defaultBaseUrl" to def.defaultBaseUrl.take(512),
            "signupUrl" to (def.signupUrl ?: "").take(512),
            "defaultModel" to def.defaultModel.take(240),
            "requiresApiKey" to def.requiresApiKey,
            "description" to def.description.take(1000),
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid)
            .collection("customProviders").document(def.id)
            .set(payload)
            .await()
    }

    private suspend fun pushMcpServer(db: FirebaseFirestore, uid: String, serverId: String) {
        val srv = settingsDataStore.getMcpServers().firstOrNull { it.id == serverId } ?: return
        val tok = settingsDataStore.getMcpAuthToken(serverId)
        val payload = mapOf(
            "id" to srv.id.take(120),
            "name" to srv.name.take(160),
            "endpointUrl" to srv.endpointUrl.take(512),
            "transport" to srv.transport.name,
            "authHeaderName" to srv.authHeaderName.take(120),
            "enabled" to srv.enabled,
            "authToken" to tok.take(2048),
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid)
            .collection("mcpServers").document(serverId)
            .set(payload)
            .await()
    }

    private suspend fun pushGitHubProject(db: FirebaseFirestore, uid: String, projectId: String) {
        val cfg = settingsDataStore.getGitHubProjectConfig(projectId) ?: return
        val payload = mapOf(
            "projectId" to projectId.take(120),
            "owner" to cfg.owner.take(120),
            "repo" to cfg.repo.take(200),
            "branch" to cfg.branch.ifBlank { "main" }.take(160),
            "isPrivate" to cfg.isPrivate,
            "createIfMissing" to cfg.createIfMissing,
            "includeApkWorkflow" to cfg.includeApkWorkflow,
            "autoSyncEnabled" to cfg.autoSyncEnabled,
            "autoFixWorkflowBugs" to cfg.autoFixWorkflowBugs,
            "directGitHubMode" to cfg.directGitHubMode,
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid)
            .collection("githubProjects").document(projectId)
            .set(payload)
            .await()
    }

    private suspend fun pushSessionConfig(db: FirebaseFirestore, uid: String, sessionId: String) {
        val cfg = settingsDataStore.getSessionPromptConfig(sessionId) ?: return
        val payload = mapOf(
            "sessionId" to sessionId.take(120),
            "mode" to cfg.mode.name,
            "presetId" to (cfg.presetId ?: "").take(120),
            "presetTitle" to (cfg.presetTitle ?: "").take(160),
            "systemPrompt" to cfg.systemPrompt.take(24000),
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid)
            .collection("sessionConfigs").document(sessionId)
            .set(payload)
            .await()
    }

    private suspend fun refreshSyncedStatusSummary() {
        val keysCount = settingsDataStore.getAllStoredApiKeys().size
        val customCount = settingsDataStore.getCustomProviders().size
        val mcpCount = settingsDataStore.getMcpServers().size
        val hasGh = settingsDataStore.getGitHubToken().isNotBlank()
        _syncStatus.value = CloudVaultSyncStatus.Synced(
            lastSyncedAt = System.currentTimeMillis(),
            syncedKeysCount = keysCount,
            customProvidersCount = customCount,
            mcpCount = mcpCount,
            hasGitHubToken = hasGh
        )
    }

    private fun parseProviderDoc(docId: String, data: Map<String, Any>): Pair<ProviderConfig, String> {
        val pid = (data["providerId"] as? String)?.ifBlank { docId } ?: docId
        val fetched = (data["fetchedModels"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val cfg = ProviderConfig(
            providerId = pid,
            baseUrl = data["baseUrl"] as? String ?: "",
            selectedModel = data["selectedModel"] as? String ?: "",
            enabled = data["enabled"] as? Boolean ?: false,
            accountId = data["accountId"] as? String ?: "",
            deploymentName = data["deploymentName"] as? String ?: "",
            fetchedModels = fetched
        )
        val key = data["apiKey"] as? String ?: ""
        return cfg to key
    }

    private fun parseCustomProviderDoc(docId: String, data: Map<String, Any>): CustomProviderDefinition? {
        val id = (data["id"] as? String)?.ifBlank { docId } ?: docId
        val name = (data["displayName"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val url = (data["defaultBaseUrl"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val signup = (data["signupUrl"] as? String)?.takeIf { it.isNotBlank() }
        return CustomProviderDefinition(
            id = id,
            displayName = name,
            defaultBaseUrl = url,
            signupUrl = signup,
            defaultModel = data["defaultModel"] as? String ?: "",
            requiresApiKey = data["requiresApiKey"] as? Boolean ?: true,
            description = data["description"] as? String ?: "Custom synced provider"
        )
    }

    private fun parseMcpDoc(docId: String, data: Map<String, Any>): Pair<McpServerConfig, String>? {
        val id = (data["id"] as? String)?.ifBlank { docId } ?: docId
        val name = (data["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val url = (data["endpointUrl"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val transportRaw = data["transport"] as? String
        val transport = enumValues<McpTransport>().firstOrNull { it.name == transportRaw } ?: McpTransport.AUTO
        val cfg = McpServerConfig(
            id = id,
            name = name,
            endpointUrl = url,
            transport = transport,
            authHeaderName = (data["authHeaderName"] as? String)?.ifBlank { "Authorization" } ?: "Authorization",
            enabled = data["enabled"] as? Boolean ?: true
        )
        val token = data["authToken"] as? String ?: ""
        return cfg to token
    }

    private fun parseGitHubProjectDoc(data: Map<String, Any>): GitHubProjectConfig? {
        val owner = data["owner"] as? String ?: return null
        val repo = data["repo"] as? String ?: return null
        return GitHubProjectConfig(
            owner = owner,
            repo = repo,
            branch = (data["branch"] as? String)?.ifBlank { "main" } ?: "main",
            isPrivate = data["isPrivate"] as? Boolean ?: true,
            createIfMissing = data["createIfMissing"] as? Boolean ?: true,
            includeApkWorkflow = data["includeApkWorkflow"] as? Boolean ?: true,
            autoSyncEnabled = data["autoSyncEnabled"] as? Boolean ?: false,
            autoFixWorkflowBugs = data["autoFixWorkflowBugs"] as? Boolean ?: false,
            directGitHubMode = data["directGitHubMode"] as? Boolean ?: false
        )
    }

    private fun parseSessionConfigDoc(data: Map<String, Any>): SessionPromptConfig? {
        val prompt = (data["systemPrompt"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val modeRaw = data["mode"] as? String
        val mode = enumValues<SessionMode>().firstOrNull { it.name == modeRaw } ?: SessionMode.SPECIAL
        return SessionPromptConfig(
            mode = mode,
            presetId = (data["presetId"] as? String)?.ifBlank { "normal" } ?: "normal",
            presetTitle = (data["presetTitle"] as? String)?.ifBlank { "Normal Q&A" } ?: "Normal Q&A",
            systemPrompt = prompt
        )
    }
}
