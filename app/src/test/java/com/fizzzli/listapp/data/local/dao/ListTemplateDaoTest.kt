package com.fizzzli.listapp.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fizzzli.listapp.data.local.ListDatabase
import com.fizzzli.listapp.data.local.entity.ListTemplateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ListTemplateDaoTest {

    private lateinit var database: ListDatabase
    private lateinit var dao: ListTemplateDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ListDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.listTemplateDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertSystemTemplate_verifyExists() = runTest {
        val template = createSystemTemplate("live", "Live 演出")
        
        dao.insert(template)
        
        val result = dao.getTemplateById("live")
        assertNotNull(result)
        assertEquals("Live 演出", result?.name)
        assertTrue(result?.isSystem == true)
    }

    @Test
    fun getAllTemplates_emptyDatabase_returnsEmptyList() = runTest {
        val templates = dao.getAllTemplates().first()
        assertTrue(templates.isEmpty())
    }

    @Test
    fun getAllTemplates_withData_returnsAllTemplates() = runTest {
        val templates = listOf(
            createSystemTemplate("live", "Live 演出"),
            createSystemTemplate("restaurant", "餐厅"),
            createUserTemplate("custom", "自定义", "user123")
        )
        dao.insertAll(templates)
        
        val result = dao.getAllTemplates().first()
        assertEquals(3, result.size)
    }

    @Test
    fun getSystemTemplates_returnsOnlySystemTemplates() = runTest {
        val templates = listOf(
            createSystemTemplate("live", "Live 演出"),
            createUserTemplate("custom", "自定义", "user123")
        )
        dao.insertAll(templates)
        
        val result = dao.getSystemTemplates().first()
        assertEquals(1, result.size)
        assertTrue(result.all { it.isSystem })
    }

    @Test
    fun deleteTemplate_verifyRemoved() = runTest {
        val template = createSystemTemplate("live", "Live 演出")
        dao.insert(template)
        
        dao.delete(template)
        
        val result = dao.getTemplateById("live")
        assertNull(result)
    }

    @Test
    fun deleteById_verifyRemoved() = runTest {
        val template = createSystemTemplate("live", "Live 演出")
        dao.insert(template)
        
        dao.deleteById("live")
        
        val result = dao.getTemplateById("live")
        assertNull(result)
    }

    private fun createSystemTemplate(id: String, name: String) = ListTemplateEntity(
        id = id,
        name = name,
        icon = "🎵",
        fieldsJson = "[]",
        isSystem = true,
        createdAt = System.currentTimeMillis(),
        ownerId = null
    )

    private fun createUserTemplate(id: String, name: String, ownerId: String) = ListTemplateEntity(
        id = id,
        name = name,
        icon = "📋",
        fieldsJson = "[]",
        isSystem = false,
        createdAt = System.currentTimeMillis(),
        ownerId = ownerId
    )
}
