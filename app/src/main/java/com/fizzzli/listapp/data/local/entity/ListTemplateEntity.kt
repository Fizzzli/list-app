package com.fizzzli.listapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "list_templates")
data class ListTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val fieldsJson: String,  // JSON string for flexible field definitions
    val isSystem: Boolean,
    val createdAt: Long,
    val ownerId: String?  // null = system template
)
