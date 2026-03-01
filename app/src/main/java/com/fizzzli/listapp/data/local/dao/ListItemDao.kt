package com.fizzzli.listapp.data.local.dao

import androidx.room.*
import com.fizzzli.listapp.data.local.entity.ListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListItemDao {

    @Query("SELECT * FROM list_items WHERE listId = :listId ORDER BY createdAt DESC")
    fun getItemsByList(listId: String): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items WHERE listId = :listId AND status = :status ORDER BY createdAt DESC")
    fun getItemsByListAndStatus(listId: String, status: String): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items WHERE id = :id")
    suspend fun getItemById(id: String): ListItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ListItemEntity)

    @Update
    suspend fun update(item: ListItemEntity)

    @Delete
    suspend fun delete(item: ListItemEntity)

    @Query("DELETE FROM list_items WHERE listId = :listId")
    suspend fun deleteAllByList(listId: String)

    @Query("UPDATE list_items SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, completedAt: Long?)
}
