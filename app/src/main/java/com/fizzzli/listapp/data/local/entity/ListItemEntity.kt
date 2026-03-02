package com.fizzzli.listapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(
            entity = UserListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId")]
)
data class ListItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val status: String,  // "PENDING", "IN_PROGRESS", "COMPLETED"
    val fieldsJson: String,  // JSON string for flexible field values
    val createdAt: Long,
    val completedAt: Long?
)
