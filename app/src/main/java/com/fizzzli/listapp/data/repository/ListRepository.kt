package com.fizzzli.listapp.data.repository

import com.fizzzli.listapp.data.local.dao.ListItemDao
import com.fizzzli.listapp.data.local.dao.ListTemplateDao
import com.fizzzli.listapp.data.local.dao.UserListDao
import com.fizzzli.listapp.data.local.entity.ListItemEntity
import com.fizzzli.listapp.data.local.entity.ListTemplateEntity
import com.fizzzli.listapp.data.local.entity.UserListEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListRepository @Inject constructor(
    private val listTemplateDao: ListTemplateDao,
    private val userListDao: UserListDao,
    private val listItemDao: ListItemDao
) {
    // Templates
    fun getAllTemplates(): Flow<List<ListTemplateEntity>> = listTemplateDao.getAllTemplates()
    fun getSystemTemplates(): Flow<List<ListTemplateEntity>> = listTemplateDao.getSystemTemplates()
    fun getUserTemplates(ownerId: String): Flow<List<ListTemplateEntity>> = listTemplateDao.getUserTemplates(ownerId)
    suspend fun getTemplateById(id: String): ListTemplateEntity? = listTemplateDao.getTemplateById(id)
    suspend fun insertTemplate(template: ListTemplateEntity) = listTemplateDao.insert(template)
    suspend fun insertTemplates(templates: List<ListTemplateEntity>) = listTemplateDao.insertAll(templates)
    suspend fun deleteTemplate(template: ListTemplateEntity) = listTemplateDao.delete(template)

    // User Lists
    fun getAllLists(): Flow<List<UserListEntity>> = userListDao.getAllLists()
    fun getListsByOwner(ownerId: String): Flow<List<UserListEntity>> = userListDao.getListsByOwner(ownerId)
    fun getPublicLists(): Flow<List<UserListEntity>> = userListDao.getPublicLists()
    suspend fun getListById(id: String): UserListEntity? = userListDao.getListById(id)
    suspend fun insertList(list: UserListEntity) = userListDao.insert(list)
    suspend fun updateList(list: UserListEntity) = userListDao.update(list)
    suspend fun deleteList(list: UserListEntity) = userListDao.delete(list)

    // List Items
    fun getItemsByList(listId: String): Flow<List<ListItemEntity>> = listItemDao.getItemsByList(listId)
    fun getItemsByListAndStatus(listId: String, status: String): Flow<List<ListItemEntity>> = listItemDao.getItemsByListAndStatus(listId, status)
    suspend fun getItemById(id: String): ListItemEntity? = listItemDao.getItemById(id)
    suspend fun insertItem(item: ListItemEntity) = listItemDao.insert(item)
    suspend fun updateItem(item: ListItemEntity) = listItemDao.update(item)
    suspend fun deleteItem(item: ListItemEntity) = listItemDao.delete(item)
    suspend fun deleteAllItemsByList(listId: String) = listItemDao.deleteAllByList(listId)
    suspend fun updateItemStatus(id: String, status: String, completedAt: Long?) = listItemDao.updateStatus(id, status, completedAt)
}
