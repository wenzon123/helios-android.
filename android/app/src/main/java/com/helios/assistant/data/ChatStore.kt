package com.helios.assistant.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class ChatMessage(@PrimaryKey(autoGenerate = true) val id: Long = 0, val role: String, val content: String, val createdAt: Long = System.currentTimeMillis())

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages ORDER BY createdAt ASC") fun observeAll(): Flow<List<ChatMessage>>
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT 20") suspend fun recent(): List<ChatMessage>
    @Insert suspend fun insert(message: ChatMessage)
    @Query("DELETE FROM messages") suspend fun clear()
}

@Database(entities = [ChatMessage::class], version = 1, exportSchema = false)
abstract class HeliosDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    companion object {
        @Volatile private var INSTANCE: HeliosDatabase? = null
        fun get(context: Context): HeliosDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, HeliosDatabase::class.java, "helios.db").build().also { INSTANCE = it }
        }
    }
}