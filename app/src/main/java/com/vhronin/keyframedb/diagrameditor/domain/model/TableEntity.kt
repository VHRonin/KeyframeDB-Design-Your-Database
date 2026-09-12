package com.vhronin.keyframedb.diagrameditor.domain.model

data class TableEntity(
    val id: String,
    val name: String,
    val x: Float, // позиция на холсте в dp; храним как Float, а не Compose Offset,
    val y: Float, // чтобы domain-слой не зависел от Compose
    val color: TableColor = TableColor.INDIGO,
    val columns: List<TableColumn> = emptyList()
)