package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QaDao {
    @Query("SELECT * FROM qa_items ORDER BY timestamp DESC")
    fun getAll(): Flow<List<QaItemEntity>>

    @Query("SELECT * FROM qa_items WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<QaItemEntity>>

    @Query("SELECT * FROM qa_items WHERE question LIKE '%' || :query || '%' OR answer LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<QaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QaItemEntity): Long

    @Update
    suspend fun update(item: QaItemEntity)

    @Delete
    suspend fun delete(item: QaItemEntity)

    @Query("DELETE FROM qa_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM qa_items")
    suspend fun clearAll()
}
