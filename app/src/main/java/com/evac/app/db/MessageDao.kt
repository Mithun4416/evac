package com.evac.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE type = 'SOS' ORDER BY timestamp DESC")
    fun getSosMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE type IN ('BULLETIN','ACK') ORDER BY timestamp DESC")
    fun getBulletinsAndAcks(): Flow<List<MessageEntity>>

    @Query("SELECT id FROM messages")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM messages WHERE id NOT IN (:knownIds)")
    suspend fun getMessagesNotIn(knownIds: List<String>): List<MessageEntity>

    @Query("DELETE FROM messages WHERE timestamp < :cutoff")
    suspend fun deleteExpired(cutoff: Long)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?
}