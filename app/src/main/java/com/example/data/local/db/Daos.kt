package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatSessionEntity>>

    @Query(
        """
        SELECT DISTINCT s.* FROM chat_sessions s
        LEFT JOIN chat_messages m ON s.id = m.sessionId
        WHERE s.title LIKE '%' || :query || '%'
           OR m.content LIKE '%' || :query || '%'
        ORDER BY s.updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET projectId = :projectId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProject(id: String, projectId: String?, updatedAt: Long)

    @Query("UPDATE chat_sessions SET providerId = :providerId, modelId = :modelId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProviderAndModel(id: String, providerId: String, modelId: String, updatedAt: Long)

    @Query(
        """
        UPDATE chat_sessions
        SET promptTokens = promptTokens + :promptDelta,
            completionTokens = completionTokens + :completionDelta,
            totalTokens = totalTokens + :totalDelta,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun addTokenUsage(
        id: String,
        promptDelta: Int,
        completionDelta: Int,
        totalDelta: Int,
        updatedAt: Long
    )

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getForSession(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
