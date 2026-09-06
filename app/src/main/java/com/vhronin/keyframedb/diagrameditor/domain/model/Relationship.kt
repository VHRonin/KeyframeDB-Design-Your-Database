package com.vhronin.keyframedb.diagrameditor.domain.model

data class Relationship(
    val id: String,
    val fromTableId: String,
    val toTableId: String,
    val label: String = "" // например "1 – N"; чисто текстовая подпись, без логики кардинальности
)
