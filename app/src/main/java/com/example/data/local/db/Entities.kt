package com.example.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rootPath: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val projectId: String?,
    val providerId: String,
    val modelId: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val reasoning: String = "",
    val toolCallsJson: String = "[]",
    val providerId: String? = null,
    val modelId: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long
)
