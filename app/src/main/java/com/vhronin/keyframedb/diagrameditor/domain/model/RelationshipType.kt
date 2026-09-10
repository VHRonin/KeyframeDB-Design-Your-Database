package com.vhronin.keyframedb.diagrameditor.domain.model

/**
 * Вид связи между таблицами — чисто описательная метка для отображения (подпись на холсте),
 * без валидации кардинальности на уровне данных (см. комментарий в Relationship).
 */
enum class RelationshipType(val shortLabel: String) {
    ONE_TO_ONE("1 – 1"),
    ONE_TO_MANY("1 – N"),
    MANY_TO_MANY("N – N")
}
