package com.fizzzli.listapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fizzzli.listapp.data.local.dao.ListItemDao
import com.fizzzli.listapp.data.local.dao.ListTemplateDao
import com.fizzzli.listapp.data.local.dao.UserListDao
import com.fizzzli.listapp.data.local.entity.ListItemEntity
import com.fizzzli.listapp.data.local.entity.ListTemplateEntity
import com.fizzzli.listapp.data.local.entity.UserListEntity

@Database(
    entities = [
        ListTemplateEntity::class,
        UserListEntity::class,
        ListItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ListDatabase : RoomDatabase() {
    abstract fun listTemplateDao(): ListTemplateDao
    abstract fun userListDao(): UserListDao
    abstract fun listItemDao(): ListItemDao
}
