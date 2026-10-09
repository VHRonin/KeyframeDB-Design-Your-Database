package com.vhronin.keyframedb.diagrameditor.domain.layout

import kotlin.math.abs
import kotlin.math.min

/**
 * Обводит ломаную линию со скруглёнными углами. radius ограничен половиной соседних отрезков и
 * ни при каких условиях не больше зазора до таблиц ([ConnectorRouter.clearance]) — тогда
 * скругление в углу вокруг таблицы не может "срезать" её край.
 *
 * Принимает лямбды, а не Compose/Android Path, чтобы одна и та же логика работала и на холсте
 * (androidx.compose.ui.graphics.Path), и в PDF (android.graphics.Path) без зависимости от них.
 */
inline fun traceRoundedPolyline(
    points: List<RoutePoint>,
    radius: Float,
    moveTo: (Float, Float) -> Unit,
    lineTo: (Float, Float) -> Unit,
    quadTo: (cx: Float, cy: Float, x: Float, y: Float) -> Unit
) {
    if (points.isEmpty()) return
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size - 1) {
        val prev = points[i - 1]
        val cur = points[i]
        val next = points[i + 1]
        val inLen = abs(cur.x - prev.x) + abs(cur.y - prev.y)
        val outLen = abs(next.x - cur.x) + abs(next.y - cur.y)
        val r = min(radius, min(inLen, outLen) / 2f)
        if (r <= 0f) {
            lineTo(cur.x, cur.y)
            continue
        }
        // Отрезки строго горизонтальные/вертикальные, поэтому направление — просто знак.
        val inX = Math.signum(cur.x - prev.x)
        val inY = Math.signum(cur.y - prev.y)
        val outX = Math.signum(next.x - cur.x)
        val outY = Math.signum(next.y - cur.y)
        lineTo(cur.x - inX * r, cur.y - inY * r)
        quadTo(cur.x, cur.y, cur.x + outX * r, cur.y + outY * r)
    }
    if (points.size > 1) lineTo(points.last().x, points.last().y)
}