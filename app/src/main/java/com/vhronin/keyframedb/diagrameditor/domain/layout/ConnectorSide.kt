package com.vhronin.keyframedb.diagrameditor.domain.layout

/**
 * С какой стороны таблицы линия связи выходит или входит. Связь крепится только к левому
 * или правому краю — к верхнему/нижнему нет, потому что она привязана к конкретной строке
 * атрибута, а строки лежат друг под другом.
 */
enum class ConnectorSide(val outwardX: Float) {
    LEFT(-1f),
    RIGHT(1f)
}