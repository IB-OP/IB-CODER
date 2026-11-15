package com.example.data.repository

import com.example.data.local.db.ChatMessageDao
import com.example.data.local.db.ChatSessionDao
import com.example.data.local.db.ChatSessionEntity
import com.example.data.local.db.DbMappers.toDomain
import com.example.data.local.db.DbMappers.toEntity
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ChatRepository(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao
) {
    fun observeSessions(searchQuery: String = ""): Flow<List<ChatSession>> {
        val source = if (searchQuery.isBlank()) {
            sessionDao.observeAll()
        } else {
            sessionDao.search(searchQuery.trim())
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    fun observeMessages(sessionId: String): Flow<List<ChatMessageUi>> =
        messageDao.observeForSession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getSession(sessionId: String): ChatSession? =
        sessionDao.getById(sessionId)?.toDomain()

    suspend fun getMessages(sessionId: String): List<ChatMessageUi> =
        messageDao.getForSession(sessionId).map { it.toDomain() }

    suspend fun createSession(
        title: String = "New Chat",
        projectId: String?,
        providerId: String,
        modelId: String
    ): ChatSession {
        val now = System.currentTimeMillis()
        val entity = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            projectId = projectId,
            providerId = providerId,
            modelId = modelId,
            createdAt = now,
            updatedAt = now
        )
        sessionDao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun saveMessage(message: ChatMessageUi) {
        messageDao.upsert(message.toEntity())
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        val clean = title.trim().ifEmpty { "New Chat" }
        sessionDao.updateTitle(sessionId, clean, System.currentTimeMillis())
    }

    suspend fun updateSessionProject(sessionId: String, projectId: String?) {
        sessionDao.updateProject(sessionId, projectId, System.currentTimeMillis())
    }

    suspend fun updateSessionProviderAndModel(sessionId: String, providerId: String, modelId: String) {
        sessionDao.updateProviderAndModel(sessionId, providerId, modelId, System.currentTimeMillis())
    }

    suspend fun addTokenUsage(sessionId: String, prompt: Int, completion: Int, total: Int) {
        sessionDao.addTokenUsage(
            id = sessionId,
            promptDelta = prompt,
            completionDelta = completion,
            totalDelta = total,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteById(sessionId)
    }
}
