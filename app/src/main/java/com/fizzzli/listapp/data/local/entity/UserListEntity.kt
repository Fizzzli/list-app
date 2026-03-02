package com.fizzzli.listapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_lists",
    foreignKeys = [
        ForeignKey(
            entity = ListTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class UserListEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val title: String,
    val isPublic: Boolean,
    val forkedFrom: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerId: String
)
