package com.vhronin.keyframedb.diagrameditor.domain.model

data class Diagram(
    val projectId: String,
    val projectName: String,
    val tables: List<TableEntity> = emptyList(),
    val relationships: List<Relationship> = emptyList()
)
