package com.vhronin.keyframedb.diagrameditor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Relationship(
    val id: String,
    val fromTableId: String,
    val fromColumnId: String,
    val toTableId: String,
    val toColumnId: String,
    val type: RelationshipType = RelationshipType.ONE_TO_MANY
)
