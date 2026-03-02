package com.fizzzli.listapp.data.local.dao

import androidx.room.*
import com.fizzzli.listapp.data.local.entity.ListTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListTemplateDao {

    @Query("SELECT * FROM list_templates ORDER BY isSystem DESC, name ASC")
    fun getAllTemplates(): Flow<List<ListTemplateEntity>>

    @Query("SELECT * FROM list_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): ListTemplateEntity?

    @Query("SELECT * FROM list_templates WHERE isSystem = 1")
    fun getSystemTemplates(): Flow<List<ListTemplateEntity>>

    @Query("SELECT * FROM list_templates WHERE ownerId = :ownerId")
    fun getUserTemplates(ownerId: String): Flow<List<ListTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: ListTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<ListTemplateEntity>)

    @Delete
    suspend fun delete(template: ListTemplateEntity)

    @Query("DELETE FROM list_templates WHERE id = :id")
    suspend fun deleteById(id: String)
}
