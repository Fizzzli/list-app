package com.fizzzli.listapp.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fizzzli.listapp.data.local.ListDatabase
import com.fizzzli.listapp.data.local.entity.ListTemplateEntity
import com.fizzzli.listapp.data.local.entity.UserListEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserListDaoTest {

    private lateinit var database: ListDatabase
    private lateinit var templateDao: ListTemplateDao
    private lateinit var dao: UserListDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ListDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        templateDao = database.listTemplateDao()
        dao = database.userListDao()
        
        // Insert a template first (foreign key constraint)
        templateDao.insert(
            ListTemplateEntity(
                id = "live",
                name = "Live 演出",
                icon = "🎵",
                fieldsJson = "[]",
                isSystem = true,
                createdAt = System.currentTimeMillis(),
                ownerId = null
            )
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun createUserList_verifyExists() = runTest {
        val list = createUserList("my-live-list", "2026 Live 计划")
        
        dao.insert(list)
        
        val result = dao.getListById("my-live-list")
        assertNotNull(result)
        assertEquals("2026 Live 计划", result?.title)
    }

    @Test
    fun getListsByOwner_returnsOnlyUserLists() = runTest {
        val lists = listOf(
            createUserList("list1", "我的列表 1", "user1"),
            createUserList("list2", "我的列表 2", "user1"),
            createUserList("list3", "别人的列表", "user2")
        )
        lists.forEach { dao.insert(it) }
        
        val result = dao.getListsByOwner("user1").first()
        assertEquals(2, result.size)
        assertTrue(result.all { it.ownerId == "user1" })
    }

    @Test
    fun updateList_verifyChanges() = runTest {
        val list = createUserList("list1", "原标题")
        dao.insert(list)
        
        val updated = list.copy(title = "新标题", isPublic = true)
        dao.update(updated)
        
        val result = dao.getListById("list1")
        assertEquals("新标题", result?.title)
        assertTrue(result?.isPublic == true)
    }

    @Test
    fun deleteList_verifyRemoved() = runTest {
        val list = createUserList("list1", "测试列表")
        dao.insert(list)
        
        dao.delete(list)
        
        val result = dao.getListById("list1")
        assertNull(result)
    }

    private fun createUserList(id: String, title: String, ownerId: String = "test-user") = UserListEntity(
        id = id,
        templateId = "live",
        title = title,
        isPublic = false,
        forkedFrom = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        ownerId = ownerId
    )
}
