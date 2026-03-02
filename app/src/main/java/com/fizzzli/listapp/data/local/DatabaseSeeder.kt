package com.fizzzli.listapp.data.local

import android.content.Context
import com.fizzzli.listapp.data.local.entity.ListTemplateEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ListDatabase
) {
    
    suspend fun seed() {
        val existingTemplates = database.listTemplateDao().getAllTemplates().first()
        if (existingTemplates.isNotEmpty()) {
            return // Already seeded
        }
        
        val systemTemplates = listOf(
            ListTemplateEntity(
                id = "live",
                name = "Live 演出",
                icon = "🎵",
                fieldsJson = """[{"key":"artist","label":"艺人","type":"TEXT","required":true},{"key":"venue","label":"场地","type":"TEXT","required":false},{"key":"ticket","label":"票务状态","type":"SELECT","required":false,"options":["待抢票","已购票","已完成"]}]""",
                isSystem = true,
                createdAt = System.currentTimeMillis(),
                ownerId = null
            ),
            ListTemplateEntity(
                id = "restaurant",
                name = "餐厅",
                icon = "🍜",
                fieldsJson = """[{"key":"cuisine","label":"菜系","type":"TEXT","required":false},{"key":"address","label":"地址","type":"TEXT","required":false},{"key":"status","label":"状态","type":"SELECT","required":false,"options":["想去","去过"]}]""",
                isSystem = true,
                createdAt = System.currentTimeMillis(),
                ownerId = null
            ),
            ListTemplateEntity(
                id = "books",
                name = "书单",
                icon = "📚",
                fieldsJson = """[{"key":"author","label":"作者","type":"TEXT","required":false},{"key":"status","label":"状态","type":"SELECT","required":false,"options":["想读","在读","已读"]},{"key":"rating","label":"评分","type":"NUMBER","required":false}]""",
                isSystem = true,
                createdAt = System.currentTimeMillis(),
                ownerId = null
            ),
            ListTemplateEntity(
                id = "movies",
                name = "影单",
                icon = "🎬",
                fieldsJson = """[{"key":"director","label":"导演","type":"TEXT","required":false},{"key":"platform","label":"平台","type":"TEXT","required":false},{"key":"status","label":"状态","type":"SELECT","required":false,"options":["想看","已看"]},{"key":"rating","label":"评分","type":"NUMBER","required":false}]""",
                isSystem = true,
                createdAt = System.currentTimeMillis(),
                ownerId = null
            ),
            ListTemplateEntity(
                id = "travel",
                name = "旅行清单",
                icon = "✈️",
                fieldsJson = """[{"key":"destination","label":"目的地","type":"TEXT","required":true},{"key":"date","label":"日期","type":"DATE","required":false},{"key":"items","label":"打包物品","type":"TEXT","required":false}]""",
                isSystem = true,
                createdAt = System.currentTimeMillis(),
                ownerId = null
            )
        )
        
        database.listTemplateDao().insertAll(systemTemplates)
    }
}
