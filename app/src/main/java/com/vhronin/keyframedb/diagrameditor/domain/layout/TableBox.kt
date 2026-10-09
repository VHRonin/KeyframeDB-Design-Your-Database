package com.vhronin.keyframedb.diagrameditor.domain.layout

/** Прямоугольник таблицы на холсте в dp (в PDF — в pt, это то же число, 1dp = 1pt). */
data class TableBox(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerX: Float get() = x + width / 2f
}