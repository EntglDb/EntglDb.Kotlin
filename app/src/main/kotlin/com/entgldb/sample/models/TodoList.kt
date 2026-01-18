package com.entgldb.sample.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TodoList(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val items: List<TodoItem> = emptyList()
)

@Serializable
data class TodoItem(
    val id: String = UUID.randomUUID().toString(),  // lowercase for merge strategy compatibility
    val task: String = "",
    val completed: Boolean = false,
    @kotlinx.serialization.SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis()
)
