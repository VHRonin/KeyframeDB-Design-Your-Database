package com.vhronin.keyframedb.diagrameditor.domain.layout

/**
 * Готовая линия связи: ломаная из горизонтальных и вертикальных отрезков от точки на краю
 * таблицы-источника до точки на краю таблицы-цели.
 *
 * Первый и последний отрезок — всегда прямой горизонтальный "хвостик" от края таблицы, поэтому
 * маркер кардинальности (см. drawCardinalityMarker) гарантированно ложится на прямой участок.
 */
data class ConnectorRoute(
    val points: List<RoutePoint>,
    val fromSide: ConnectorSide,
    val toSide: ConnectorSide,
    /** Центр подписи вида связи ("1 – N") — на отрезке линии, не закрывая таблицы. */
    val labelCenter: RoutePoint
)