package com.vhronin.keyframedb.diagrameditor.domain.model

data class Relationship(
    val id: String,
    val fromTableId: String,
    val toTableId: String,
    val type: RelationshipType = RelationshipType.ONE_TO_MANY
)
