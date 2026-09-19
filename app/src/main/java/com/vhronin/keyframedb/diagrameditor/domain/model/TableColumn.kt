package com.vhronin.keyframedb.diagrameditor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TableColumn(
    val id: String,
    val name: String,
    val type: String, // просто текст: "uuid", "varchar" и т.п. — без валидации типов БД
    val isPrimaryKey: Boolean = false,
    val isForeignKey: Boolean = false
)

