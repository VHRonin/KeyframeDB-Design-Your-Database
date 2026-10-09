package com.vhronin.keyframedb.diagrameditor.domain.layout

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Прокладывает связи между таблицами так, чтобы линии не заходили под таблицы.
 *
 * Domain-слой намеренно ничего не знает про Compose и android.graphics — маршрутизация чистая
 * геометрия на Float, поэтому ровно один и тот же код считает линии и для холста
 * (ConnectorLines в DiagramEditorScreen), и для PDF-экспорта (PdfDiagramExporter): линии на
 * экране и в PDF не могут разъехаться.
 *
 * Алгоритм: стороны выбираются по взаимному положению таблиц (см. [sidesFor]), затем между
 * концами "хвостиков" ищется кратчайший ортогональный путь (A*) по сетке, построенной из
 * границ всех таблиц, раздутых на [clearance]. Каждый поворот штрафуется ([bendPenalty]),
 * поэтому линия выбирает меньше изломов, а не просто самый короткий путь.
 *
 * @param boxes все таблицы холста — каждая считается препятствием, включая саму from и to.
 * @param stubLength длина прямого хвостика у таблицы (должна быть заметно больше маркера).
 * @param clearance минимальный зазор между линией и любой таблицей; должен быть меньше
 * [stubLength], иначе конец хвостика окажется внутри зазора собственной таблицы.
 */
class ConnectorRouter(
    boxes: List<TableBox>,
    private val stubLength: Float,
    private val clearance: Float,
    private val labelHalfWidth: Float,
    private val labelHalfHeight: Float,
    private val bendPenalty: Float = clearance * 3f
) {
    private class Rect(val l: Float, val t: Float, val r: Float, val b: Float)

    private val tableRects = boxes.map { Rect(it.x, it.y, it.right, it.bottom) }
    private val obstacles = boxes.map {
        Rect(it.x - clearance, it.y - clearance, it.right + clearance, it.bottom + clearance)
    }

    /**
     * Сторона выхода/входа выбирается по положению второй таблицы:
     *  - цель целиком справа от источника → выходим справа, входим слева;
     *  - цель целиком слева → выходим слева, входим справа;
     *  - таблицы перекрываются по X (стоят друг над другом) → обе стороны с той стороны, куда
     *    смещён центр цели: так линия делает короткую скобку сбоку, а не змейку между таблицами;
     *  - связь таблицы с самой собой → скобка справа.
     */
    fun sidesFor(from: TableBox, to: TableBox): Pair<ConnectorSide, ConnectorSide> {
        if (from.id == to.id) return ConnectorSide.RIGHT to ConnectorSide.RIGHT
        return when {
            to.x >= from.right -> ConnectorSide.RIGHT to ConnectorSide.LEFT
            to.right <= from.x -> ConnectorSide.LEFT to ConnectorSide.RIGHT
            to.centerX >= from.centerX -> ConnectorSide.RIGHT to ConnectorSide.RIGHT
            else -> ConnectorSide.LEFT to ConnectorSide.LEFT
        }
    }

    /** [fromY]/[toY] — Y центра строки атрибута на холсте, не относительно карточки. */
    fun route(from: TableBox, fromY: Float, to: TableBox, toY: Float): ConnectorRoute {
        val (fromSide, toSide) = sidesFor(from, to)
        val start = RoutePoint(if (fromSide == ConnectorSide.RIGHT) from.right else from.x, fromY)
        val end = RoutePoint(if (toSide == ConnectorSide.RIGHT) to.right else to.x, toY)

        val stubOut = pickStub(start, fromSide.outwardX)
        val stubIn = pickStub(end, toSide.outwardX)

        // Идём к цели: из from — наружу (outwardX), в to — внутрь, то есть против её "наружу".
        val inner = if (stubOut != null && stubIn != null) {
            findPath(stubOut, dirIndex(fromSide.outwardX), stubIn, dirIndex(-toSide.outwardX))
        } else null

        val raw = ArrayList<RoutePoint>()
        raw.add(start)
        if (inner != null) {
            raw.addAll(inner)
        } else {
            // Запасной вариант, когда путь не найден: таблицы вплотную или перекрываются, и
            // хвостик упирается в соседа. Обходить уже нечего — рисуем простую "скобку".
            val so = stubOut ?: RoutePoint(start.x + fromSide.outwardX * stubLength, start.y)
            val si = stubIn ?: RoutePoint(end.x + toSide.outwardX * stubLength, end.y)
            raw.add(so)
            raw.add(RoutePoint(so.x, si.y))
            raw.add(si)
        }
        raw.add(end)

        val points = simplify(raw)
        return ConnectorRoute(points, fromSide, toSide, pickLabelCenter(points))
    }

    // ---- хвостики ----

    private fun pickStub(anchor: RoutePoint, outwardX: Float): RoutePoint? {
        // Если сосед стоит так близко, что полный хвостик упирается в его зазор — пробуем
        // покороче, но не короче маркера кардинальности (иначе он не поместится на прямом участке).
        for (factor in STUB_SHRINK_STEPS) {
            val p = RoutePoint(anchor.x + outwardX * stubLength * factor, anchor.y)
            if (!insideAnyObstacle(p)) return p
        }
        return null
    }

    private fun insideAnyObstacle(p: RoutePoint): Boolean =
        obstacles.any { p.x > it.l + EPS && p.x < it.r - EPS && p.y > it.t + EPS && p.y < it.b - EPS }

    /** Лежит ли отрезок между двумя СОСЕДНИМИ узлами сетки внутри какого-нибудь препятствия. */
    private fun segmentBlocked(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        // Границы препятствий — линии сетки, поэтому отрезок между соседними узлами либо целиком
        // внутри препятствия, либо целиком снаружи/на границе: достаточно проверить середину.
        val mx = (ax + bx) / 2f
        val my = (ay + by) / 2f
        return obstacles.any { mx > it.l + EPS && mx < it.r - EPS && my > it.t + EPS && my < it.b - EPS }
    }

    // ---- поиск пути (A*) ----

    private class Entry(val f: Float, val g: Float, val state: Int, val isGoal: Boolean)

    /** Индексы направлений: 0 = вправо (+x), 1 = влево (-x), 2 = вниз (+y), 3 = вверх (-y). */
    private fun dirIndex(dx: Float): Int = if (dx > 0f) 0 else 1

    private fun findPath(start: RoutePoint, startDir: Int, goal: RoutePoint, goalDir: Int): List<RoutePoint>? {
        val xs = sortedUnique(obstacles.flatMap { listOf(it.l, it.r) } + start.x + goal.x)
        val ys = sortedUnique(obstacles.flatMap { listOf(it.t, it.b) } + start.y + goal.y)
        val nx = xs.size
        val ny = ys.size
        val sx = indexOfValue(xs, start.x)
        val sy = indexOfValue(ys, start.y)
        val gx = indexOfValue(xs, goal.x)
        val gy = indexOfValue(ys, goal.y)

        // Состояние = (узел сетки, направление, в котором в него пришли) — направление нужно,
        // чтобы штрафовать повороты.
        val stateCount = nx * ny * 4
        val g = FloatArray(stateCount) { Float.MAX_VALUE }
        val parent = IntArray(stateCount) { -1 }
        val open = PriorityQueue<Entry>(compareBy<Entry> { it.f })

        fun heuristic(i: Int, j: Int): Float = abs(xs[i] - xs[gx]) + abs(ys[j] - ys[gy])
        fun stateOf(i: Int, j: Int, dir: Int): Int = (i * ny + j) * 4 + dir

        fun reconstruct(lastState: Int): List<RoutePoint> {
            val pts = ArrayList<RoutePoint>()
            var s = lastState
            while (s != -1) {
                val node = s / 4
                pts.add(RoutePoint(xs[node / ny], ys[node % ny]))
                s = parent[s]
            }
            pts.reverse()
            return pts
        }

        val startState = stateOf(sx, sy, startDir)
        g[startState] = 0f
        open.add(Entry(heuristic(sx, sy), 0f, startState, false))

        while (open.isNotEmpty()) {
            val cur = open.poll() ?: break
            if (cur.isGoal) return reconstruct(cur.state)
            if (cur.g > g[cur.state]) continue

            val dir = cur.state % 4
            val node = cur.state / 4
            val i = node / ny
            val j = node % ny

            if (i == gx && j == gy) {
                if (dir == goalDir) return reconstruct(cur.state)
                // Пришли в цель не тем направлением — вход в таблицу потребует ещё одного
                // поворота. Кладём в очередь как отдельный "финиш" с этим штрафом, чтобы A*
                // не завершился на заведомо худшем варианте раньше лучшего.
                val total = cur.g + bendPenalty
                open.add(Entry(total, total, cur.state, true))
            }

            for (nd in 0..3) {
                if (nd == (dir xor 1)) continue // разворот на 180° не нужен
                val ni = i + DIR_DX[nd]
                val nj = j + DIR_DY[nd]
                if (ni < 0 || ni >= nx || nj < 0 || nj >= ny) continue
                if (segmentBlocked(xs[i], ys[j], xs[ni], ys[nj])) continue

                val length = abs(xs[ni] - xs[i]) + abs(ys[nj] - ys[j])
                val newG = cur.g + length + if (nd != dir) bendPenalty else 0f
                val ns = stateOf(ni, nj, nd)
                if (newG < g[ns]) {
                    g[ns] = newG
                    parent[ns] = cur.state
                    open.add(Entry(newG + heuristic(ni, nj), newG, ns, false))
                }
            }
        }
        return null
    }

    private fun sortedUnique(values: List<Float>): FloatArray {
        val sorted = values.sorted()
        val out = ArrayList<Float>(sorted.size)
        for (v in sorted) {
            if (out.isEmpty() || v - out.last() > EPS) out.add(v)
        }
        return out.toFloatArray()
    }

    private fun indexOfValue(arr: FloatArray, value: Float): Int {
        var best = 0
        for (k in arr.indices) {
            if (abs(arr[k] - value) < abs(arr[best] - value)) best = k
        }
        return best
    }

    // ---- упрощение и подпись ----

    /** Склеивает дубликаты и точки, лежащие на одной прямой с соседями, — остаются только углы. */
    private fun simplify(points: List<RoutePoint>): List<RoutePoint> {
        val out = ArrayList<RoutePoint>()
        for (p in points) {
            if (out.isNotEmpty() && abs(out.last().x - p.x) < EPS && abs(out.last().y - p.y) < EPS) continue
            while (out.size >= 2 && isCollinear(out[out.size - 2], out.last(), p)) {
                out.removeAt(out.size - 1)
            }
            out.add(p)
        }
        return out
    }

    private fun isCollinear(a: RoutePoint, b: RoutePoint, c: RoutePoint): Boolean =
        (abs(a.x - b.x) < EPS && abs(b.x - c.x) < EPS) || (abs(a.y - b.y) < EPS && abs(b.y - c.y) < EPS)

    /**
     * Подпись ставим по центру отрезка линии: берём самые длинные отрезки по убыванию и
     * первый, где подпись не наезжает на таблицы. Крайние отрезки (с маркерами кардинальности
     * на концах) берём только если подпись помещается между маркером и изломом.
     */
    private fun pickLabelCenter(points: List<RoutePoint>): RoutePoint {
        if (points.size < 2) return points.firstOrNull() ?: RoutePoint(0f, 0f)
        val lastIndex = points.size - 2
        val segments = (0..lastIndex).map { index ->
            val a = points[index]
            val b = points[index + 1]
            Triple(index, sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)), RoutePoint((a.x + b.x) / 2f, (a.y + b.y) / 2f))
        }.sortedByDescending { it.second }

        val minEndSegment = labelHalfWidth * 2f + MARKER_ALLOWANCE * 2f
        for ((index, length, center) in segments) {
            val isEnd = index == 0 || index == lastIndex
            if (isEnd && length < minEndSegment) continue
            if (!labelOverlapsTable(center)) return center
        }
        return segments.first().third
    }

    private fun labelOverlapsTable(c: RoutePoint): Boolean = tableRects.any {
        c.x + labelHalfWidth > it.l - LABEL_PAD && c.x - labelHalfWidth < it.r + LABEL_PAD &&
                c.y + labelHalfHeight > it.t - LABEL_PAD && c.y - labelHalfHeight < it.b + LABEL_PAD
    }

    private companion object {
        const val EPS = 0.01f
        const val MARKER_ALLOWANCE = 14f
        const val LABEL_PAD = 2f
        val STUB_SHRINK_STEPS = floatArrayOf(1f, 0.8f, 0.6f)
        val DIR_DX = intArrayOf(1, -1, 0, 0)
        val DIR_DY = intArrayOf(0, 0, 1, -1)
    }
}
