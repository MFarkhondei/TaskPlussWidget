package com.taskpluss.widget.model

data class TaskItem(
    val id: String,
    val title: String,
    val status: String,          // "todo" | "doing" | "done"
    val priority: Int,            // higher = more important
    val date: String,
    val created: String,
    val group: String,
    val notes: String = ""
)

data class GroupItem(
    val key: String,
    val name: String,
    val color: String = "#5B6B7A"
)

data class WidgetCache(
    val tasks: List<TaskItem> = emptyList(),
    val groups: Map<String, GroupItem> = emptyMap(),
    val selectedGroupKey: String = "all",
    val updatedAt: String = "",
    val offline: Boolean = false
)
