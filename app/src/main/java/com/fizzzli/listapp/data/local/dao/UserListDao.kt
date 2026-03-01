package com.fizzzli.listapp.data.local.dao

import androidx.room.*
import com.fizzzli.listapp.data.local.entity.UserListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserListDao {

    @Query("SELECT * FROM user_lists ORDER BY updatedAt DESC")
    fun getAllLists(): Flow<List<UserListEntity>>

    @Query("SELECT * FROM user_lists WHERE id = :id")
    suspend fun getListById(id: String): UserListEntity?

    @Query("SELECT * FROM user_lists WHERE ownerId = :ownerId ORDER BY updatedAt DESC")
    fun getListsByOwner(ownerId: String): Flow<List<UserListEntity>>

    @Query("SELECT * FROM user_lists WHERE isPublic = 1 ORDER BY updatedAt DESC")
    fun getPublicLists(): Flow<List<UserListEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: UserListEntity)

    @Update
    suspend fun update(list: UserListEntity)

    @Delete
    suspend fun delete(list: UserListEntity)

    @Query("DELETE FROM user_lists WHERE id = :id")
    suspend fun deleteById(id: String)
}
