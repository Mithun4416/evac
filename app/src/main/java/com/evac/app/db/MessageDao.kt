package com.evac.app.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    suspend fun getAll(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE type = 'SOS' ORDER BY timestamp DESC")
    fun getSosMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE type IN ('BULLETIN','ACK') ORDER BY timestamp DESC")
    fun getBulletinsAndAcks(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllLive(): LiveData<List<MessageEntity>>

    @Query("SELECT id FROM messages")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE type = :type ORDER BY timestamp DESC")
    suspend fun getByType(type: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE type = :type ORDER BY timestamp DESC")
    fun getByTypeLive(type: String): LiveData<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE type = 'SOS' ORDER BY timestamp DESC")
    fun getSosLive(): LiveData<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE synced_to_firebase = 0")
    suspend fun getUnsynced(): List<MessageEntity>

    @Query("UPDATE messages SET synced_to_firebase = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM messages WHERE timestamp < :cutoffMs")
    suspend fun deleteExpired(cutoffMs: Long)

    @Query("DELETE FROM messages WHERE timestamp < :cutoffMs")
    suspend fun deleteExpiredMessages(cutoffMs: Long): Int
}
