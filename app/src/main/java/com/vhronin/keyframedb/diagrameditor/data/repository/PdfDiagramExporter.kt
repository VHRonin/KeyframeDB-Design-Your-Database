package com.vhronin.keyframedb.diagrameditor.data.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.vhronin.keyframedb.diagrameditor.domain.layout.ConnectorRoute
import com.vhronin.keyframedb.diagrameditor.domain.layout.ConnectorRouter
import com.vhronin.keyframedb.diagrameditor.domain.layout.TableBox
import com.vhronin.keyframedb.diagrameditor.domain.layout.traceRoundedPolyline
import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import com.vhronin.keyframedb.diagrameditor.domain.model.Relationship
import com.vhronin.keyframedb.diagrameditor.domain.model.RelationshipType
import com.vhronin.keyframedb.diagrameditor.domain.model.TableColor
import com.vhronin.keyframedb.diagrameditor.domain.model.TableEntity
import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramPdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Рисует диаграмму как одну PDF-страницу через стандартный android.graphics.pdf.PdfDocument —
 * без сторонних библиотек, ничего нового в build.gradle добавлять не нужно.
 *
 * Раскладка (ширина карточек, отступы, шаг сетки) намеренно повторяет то, что реально рисуется
 * на холсте в DiagramEditorScreen (см. computeTableCardWidth и константы рядом с ним) — иначе
 * PDF визуально разъехался бы с тем, что пользователь видел на экране. Числа синхронизированы
 * вручную (см. константы в самом низу файла): выносить их в общий модуль ради горстки чисел,
 * которые тянут за собой Compose как зависимость на сторону экспортёра — избыточное усложнение.
 *
 * 1dp экрана = 1pt PDF: координаты из domain-модели (TableEntity.x/y в dp) переносятся в систему
 * координат PDF без масштабирования, только со сдвигом на отступ страницы (см. PdfDiagramLayout).
 *
 * Файл кладём в cacheDir — это временный экспорт для шэринга через Intent.ACTION_SEND (см.
 * FileProvider в DiagramEditorScreen), а не постоянное хранилище: как и сама диаграмма на холсте,
 * он в любой момент может быть перегенерирован заново из текущего состояния.
 */
class PdfDiagramExporter(
    private val context: Context
) : DiagramPdfExporter {

    override suspend fun export(diagram: Diagram): File = withContext(Dispatchers.IO) {
        val paints = PdfPaints()
        val layout = PdfDiagramLayout(diagram, paints)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(
            ceil(layout.pageWidthPt).toInt().coerceAtLeast(1),
            ceil(layout.pageHeightPt).toInt().coerceAtLeast(1),
            1
        ).create()
        val page = pdfDocument.startPage(pageInfo)
        renderDiagram(page.canvas, diagram, layout, paints)
        pdfDocument.finishPage(page)

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "${sanitizeFileName(diagram.projectName)}.pdf")
        try {
            FileOutputStream(file).use { output -> pdfDocument.writeTo(output) }
        } finally {
            pdfDocument.close()
        }
        file
    }

    private fun renderDiagram(canvas: Canvas, diagram: Diagram, layout: PdfDiagramLayout, paints: PdfPaints) {
        canvas.drawColor(ColorBackground)
        // Точечную сетку рисуем только если страница не гигантская — для очень больших схем
        // это тысячи лишних drawCircle без заметной визуальной пользы и с заметным ростом файла.
        if (layout.pageWidthPt * layout.pageHeightPt <= MaxDotGridAreaPt2) {
            drawDotGrid(canvas, layout.pageWidthPt, layout.pageHeightPt, paints.dot)
        }
        drawTitle(canvas, diagram.projectName, paints)

        if (diagram.tables.isEmpty()) {
            drawEmptyState(canvas, layout, paints)
            return
        }

        // Сдвигаем систему координат так, чтобы верхний левый угол bounding box таблиц лёг
        // точно под area заголовка с нужным отступом (см. PdfDiagramLayout.contentOffset*).
        canvas.save()
        canvas.translate(layout.contentOffsetX, layout.contentOffsetY)
        // Порядок как в DiagramCanvas/ConnectorLines в Compose: линии связей — под подписями,
        // подписи — под карточками таблиц, карточки — сверху всего.
        drawConnectors(canvas, layout, paints)
        drawRelationshipLabels(canvas, layout, paints)
        diagram.tables.forEach { table -> drawTableCard(canvas, table, layout.widthOf(table), paints) }
        canvas.restore()
    }

    private fun drawTitle(canvas: Canvas, projectName: String, paints: PdfPaints) {
        val name = projectName.ifBlank { "Без названия" }
        canvas.drawText(name, PageMarginPt, PageMarginPt + 16f, paints.title)
        val dateText = "Экспортировано " + TitleDateFormat.format(Date())
        canvas.drawText(dateText, PageMarginPt, PageMarginPt + 32f, paints.subtitle)
    }

    private fun drawEmptyState(canvas: Canvas, layout: PdfDiagramLayout, paints: PdfPaints) {
        val text = "Схема пуста — добавьте хотя бы одну таблицу"
        val textWidth = paints.subtitle.measureText(text)
        canvas.drawText(text, (layout.pageWidthPt - textWidth) / 2f, layout.pageHeightPt / 2f, paints.subtitle)
    }

    private fun drawDotGrid(canvas: Canvas, pageWidthPt: Float, pageHeightPt: Float, dotPaint: Paint) {
        var y = 0f
        while (y < pageHeightPt) {
            var x = 0f
            while (x < pageWidthPt) {
                canvas.drawCircle(x, y, DotGridRadiusPt, dotPaint)
                x += GridStepPt
            }
            y += GridStepPt
        }
    }

    private fun drawConnectors(canvas: Canvas, layout: PdfDiagramLayout, paints: PdfPaints) {
        // Линии уже проложены в PdfDiagramLayout тем же ConnectorRouter, что и на холсте
        // (см. routeRelationships в DiagramEditorScreen.kt) — здесь только рисуем.
        layout.routes.forEach { (relationship, route) ->
            val path = Path()
            traceRoundedPolyline(
                points = route.points,
                radius = ConnectorCornerRadiusPt,
                moveTo = { x, y -> path.moveTo(x, y) },
                lineTo = { x, y -> path.lineTo(x, y) },
                quadTo = { cx, cy, x, y -> path.quadTo(cx, cy, x, y) }
            )
            canvas.drawPath(path, paints.connectorLine)

            val start = route.points.first()
            val end = route.points.last()
            drawCardinalityMarker(
                canvas, Pt(start.x, start.y), Pt(route.fromSide.outwardX, 0f),
                relationship.type.isManyAt(isFromEnd = true), paints.connectorLine
            )
            drawCardinalityMarker(
                canvas, Pt(end.x, end.y), Pt(route.toSide.outwardX, 0f),
                relationship.type.isManyAt(isFromEnd = false), paints.connectorLine
            )
        }
    }

    /**
     * Та же ER-нотация "вороньей лапки", что и на холсте (см. drawCardinalityMarker в
     * DiagramEditorScreen.kt): одна перпендикулярная чёрточка — "один", раздвоенные ножки — "много".
     * `outward` здесь всегда строго горизонтален (влево/вправо по стороне таблицы) — гарантирован
     * фиксированным хвостиком связи (см. ConnectorStubPt).
     */
    private fun drawCardinalityMarker(canvas: Canvas, point: Pt, outward: Pt, isMany: Boolean, linePaint: Paint) {
        val perp = Pt(-outward.y, outward.x)
        if (isMany) {
            val back = point + outward * MarkerReachPt
            val legA = back - perp * MarkerSpreadPt
            val legB = back + perp * MarkerSpreadPt
            canvas.drawLine(legA.x, legA.y, point.x, point.y, linePaint)
            canvas.drawLine(legB.x, legB.y, point.x, point.y, linePaint)
        } else {
            val tickCenter = point + outward * (MarkerReachPt * 0.65f)
            val tickA = tickCenter - perp * MarkerSpreadPt
            val tickB = tickCenter + perp * MarkerSpreadPt
            canvas.drawLine(tickA.x, tickA.y, tickB.x, tickB.y, linePaint)
        }
    }

    private fun drawRelationshipLabels(canvas: Canvas, layout: PdfDiagramLayout, paints: PdfPaints) {
        layout.routes.forEach { (relationship, route) ->
            val text = relationship.type.shortLabel
            val fm = paints.relationLabel.fontMetrics
            val textWidth = paints.relationLabel.measureText(text)
            val chipWidth = textWidth + LabelHorizontalPaddingPt * 2
            val chipHeight = (fm.descent - fm.ascent) + LabelVerticalPaddingPt * 2
            // Подпись центрируется на выбранной роутером точке линии (см. ConnectorRoute.labelCenter).
            val chipLeft = route.labelCenter.x - chipWidth / 2f
            val chipTop = route.labelCenter.y - chipHeight / 2f
            val chipRect = RectF(chipLeft, chipTop, chipLeft + chipWidth, chipTop + chipHeight)

            canvas.drawRoundRect(chipRect, LabelCornerRadiusPt, LabelCornerRadiusPt, paints.relationLabelBackground)
            canvas.drawRoundRect(chipRect, LabelCornerRadiusPt, LabelCornerRadiusPt, paints.relationLabelBorder)
            canvas.drawText(
                text,
                chipLeft + LabelHorizontalPaddingPt,
                paints.relationLabel.baselineForCenter(chipRect.centerY()),
                paints.relationLabel
            )
        }
    }

    private fun drawTableCard(canvas: Canvas, table: TableEntity, widthPt: Float, paints: PdfPaints) {
        val headerHeight = GridStepPt * TableHeaderDots
        val rowHeight = GridStepPt * TableRowDots
        val totalHeight = headerHeight + rowHeight * table.columns.size
        val cardRect = RectF(table.x, table.y, table.x + widthPt, table.y + totalHeight)
        val clipPath = Path().apply {
            addRoundRect(cardRect, CardCornerRadiusPt, CardCornerRadiusPt, Path.Direction.CW)
        }

        // Тело карточки и цветной заголовок рисуем внутри клипа по скруглению всей карточки —
        // так же, как в Compose .clip(RoundedCornerShape(13.dp)) на всю Column целиком: верх
        // заголовка получает скруглённые углы карточки, а его нижний край — обычная прямая линия.
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(cardRect, paints.cardBackground)
        canvas.drawRect(table.x, table.y, table.x + widthPt, table.y + headerHeight, paints.header(table.color))
        for (index in 1 until table.columns.size) {
            val y = table.y + headerHeight + rowHeight * index
            canvas.drawLine(table.x, y, table.x + widthPt, y, paints.divider)
        }
        canvas.restore()

        canvas.drawRoundRect(cardRect, CardCornerRadiusPt, CardCornerRadiusPt, paints.cardBorder)
        canvas.drawText(
            table.name,
            table.x + CardHorizontalPaddingPt,
            paints.headerText.baselineForCenter(table.y + headerHeight / 2f),
            paints.headerText
        )

        table.columns.forEachIndexed { index, column ->
            val rowTop = table.y + headerHeight + rowHeight * index
            val rowCenterY = rowTop + rowHeight / 2f
            var cursorX = table.x + CardHorizontalPaddingPt

            if (column.isPrimaryKey) {
                cursorX = drawBadge(canvas, "PK", cursorX, rowCenterY, paints.pkBadgeText, paints.pkBadgeBackground) +
                        BadgeSpacingPt
            }
            if (column.isForeignKey) {
                cursorX = drawBadge(canvas, "FK", cursorX, rowCenterY, paints.fkBadgeText, paints.fkBadgeBackground) +
                        BadgeSpacingPt
            }

            val nameText = column.name.ifBlank { "…" }
            val namePaint = if (column.name.isBlank()) paints.columnNameFaint else paints.columnName
            canvas.drawText(nameText, cursorX, namePaint.baselineForCenter(rowCenterY), namePaint)

            val typeWidth = paints.columnType.measureText(column.type)
            canvas.drawText(
                column.type,
                table.x + widthPt - CardHorizontalPaddingPt - typeWidth,
                paints.columnType.baselineForCenter(rowCenterY),
                paints.columnType
            )
        }
    }

    /** Рисует один бейдж (PK/FK) и возвращает x его правого края — вызывающий код продолжает цепочку от него. */
    private fun drawBadge(canvas: Canvas, text: String, x: Float, centerY: Float, textPaint: Paint, bgPaint: Paint): Float {
        val textWidth = textPaint.measureText(text)
        val fm = textPaint.fontMetrics
        val badgeHeight = (fm.descent - fm.ascent) + BadgeVerticalPaddingPt * 2
        val badgeWidth = textWidth + BadgeHorizontalPaddingPt * 2
        val rect = RectF(x, centerY - badgeHeight / 2f, x + badgeWidth, centerY + badgeHeight / 2f)
        canvas.drawRoundRect(rect, BadgeCornerRadiusPt, BadgeCornerRadiusPt, bgPaint)
        canvas.drawText(text, x + BadgeHorizontalPaddingPt, textPaint.baselineForCenter(centerY), textPaint)
        return x + badgeWidth
    }

    private fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").take(64)
        return cleaned.ifBlank { "diagram" }
    }

    private companion object {
        val TitleDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
}

/** Смещает baseline так, чтобы строка текста легла визуально по центру относительно centerY. */
private fun Paint.baselineForCenter(centerY: Float): Float {
    val fm = fontMetrics
    return centerY - (fm.ascent + fm.descent) / 2f
}

/** Минимальный набор векторной арифметики для геометрии связей — без лишней зависимости от android.graphics.PointF. */
private data class Pt(val x: Float, val y: Float) {
    operator fun plus(other: Pt) = Pt(x + other.x, y + other.y)
    operator fun minus(other: Pt) = Pt(x - other.x, y - other.y)
    operator fun times(scale: Float) = Pt(x * scale, y * scale)
}

/** См. tableHeight(columnCount) в DiagramEditorScreen.kt — та же формула. */
private fun tableHeight(columnCount: Int): Float = GridStepPt * (TableHeaderDots + TableRowDots * columnCount)

/**
 * См. columnCenterOffsetY(columnIndex) в DiagramEditorScreen.kt — та же формула: связи и их
 * подписи теперь идут в конкретную строку атрибута, а не в геометрический центр карточки.
 */
private fun columnCenterOffsetY(columnIndex: Int): Float =
    tableHeight(columnIndex) + TableRowDots * GridStepPt / 2f

/**
 * Ширина карточки под содержимое таблицы — повторяет computeTableCardWidth в
 * DiagramEditorScreen.kt (тот же пол в TableCardMinWidthPt, то же округление вверх до шага
 * сетки), но меряет текст через android.graphics.Paint вместо Compose TextMeasurer, раз PDF
 * рисуется вне дерева Compose.
 */
private fun measureTableWidth(table: TableEntity, paints: PdfPaints): Float {
    val headerWidth = paints.headerText.measureText(table.name.ifBlank { " " }) + CardHorizontalPaddingPt * 2

    val rowWidths = table.columns.map { column ->
        var leftGroupWidth = paints.columnName.measureText(column.name.ifBlank { "…" })
        if (column.isPrimaryKey) {
            leftGroupWidth += paints.pkBadgeText.measureText("PK") + BadgeHorizontalPaddingPt * 2 + BadgeSpacingPt
        }
        if (column.isForeignKey) {
            leftGroupWidth += paints.fkBadgeText.measureText("FK") + BadgeHorizontalPaddingPt * 2 + BadgeSpacingPt
        }
        val typeWidth = paints.columnType.measureText(column.type)
        CardHorizontalPaddingPt * 2 + leftGroupWidth + FieldRowMinGapPt + typeWidth
    }

    val contentWidth = (rowWidths + headerWidth).maxOrNull()?.coerceAtLeast(TableCardMinWidthPt) ?: TableCardMinWidthPt
    val steps = ceil(contentWidth / GridStepPt).toInt()
    return steps * GridStepPt
}

/**
 * Границы страницы и общий сдвиг контента (bounding box всех таблиц плюс запас под подписи
 * связей и поля страницы) — считаются один раз на export(), а не на каждый draw-вызов.
 */
private class PdfDiagramLayout(diagram: Diagram, paints: PdfPaints) {
    private val widths: Map<String, Float> = diagram.tables.associate { it.id to measureTableWidth(it, paints) }
    val pageWidthPt: Float
    val pageHeightPt: Float
    val contentOffsetX: Float
    val contentOffsetY: Float

    /** Проложенные связи — считаем один раз: по ним же строится и bounding box страницы. */
    val routes: List<PdfRoutedRelationship>

    init {
        routes = routeRelationships(diagram)
        if (diagram.tables.isEmpty()) {
            pageWidthPt = EmptyPageWidthPt
            pageHeightPt = EmptyPageHeightPt
            contentOffsetX = 0f
            contentOffsetY = 0f
        } else {
            var minX = diagram.tables.minOf { it.x }
            var minY = diagram.tables.minOf { it.y }
            var maxX = diagram.tables.maxOf { it.x + (widths[it.id] ?: TableCardMinWidthPt) }
            var maxY = diagram.tables.maxOf { it.y + tableHeight(it.columns.size) }

            // Линии обходят таблицы снаружи и могут выйти за крайние из них (во все стороны), а
            // подписи — высунуться за линию: расширяем bounding box под точки каждой проложенной
            // линии и под подпись, а не обрезаем.
            routes.forEach { (_, route) ->
                route.points.forEach { p ->
                    minX = minOf(minX, p.x)
                    maxX = maxOf(maxX, p.x)
                    minY = minOf(minY, p.y)
                    maxY = maxOf(maxY, p.y)
                }
                minX = minOf(minX, route.labelCenter.x - RelationLabelWidthEstimatePt / 2f)
                maxX = maxOf(maxX, route.labelCenter.x + RelationLabelWidthEstimatePt / 2f)
                minY = minOf(minY, route.labelCenter.y - RelationLabelHeightEstimatePt / 2f)
                maxY = maxOf(maxY, route.labelCenter.y + RelationLabelHeightEstimatePt / 2f)
            }

            pageWidthPt = (maxX - minX) + PageMarginPt * 2
            pageHeightPt = (maxY - minY) + PageMarginPt * 2 + TitleAreaHeightPt
            contentOffsetX = PageMarginPt - minX
            contentOffsetY = PageMarginPt + TitleAreaHeightPt - minY
        }
    }

    /**
     * Прокладывает связи тем же ConnectorRouter и с теми же параметрами, что и routeRelationships
     * в DiagramEditorScreen.kt — иначе линии в PDF разъехались бы с тем, что на экране.
     */
    private fun routeRelationships(diagram: Diagram): List<PdfRoutedRelationship> {
        val boxes = diagram.tables.map { table ->
            TableBox(
                id = table.id,
                x = table.x,
                y = table.y,
                width = widths[table.id] ?: TableCardMinWidthPt,
                height = tableHeight(table.columns.size)
            )
        }
        val boxesById = boxes.associateBy { it.id }
        val tablesById = diagram.tables.associateBy { it.id }
        val router = ConnectorRouter(
            boxes = boxes,
            stubLength = ConnectorStubPt,
            clearance = ConnectorClearancePt,
            labelHalfWidth = RelationLabelWidthEstimatePt / 2f,
            labelHalfHeight = RelationLabelHeightEstimatePt / 2f
        )

        return diagram.relationships.mapNotNull { relationship ->
            val from = tablesById[relationship.fromTableId] ?: return@mapNotNull null
            val to = tablesById[relationship.toTableId] ?: return@mapNotNull null
            val fromColumnIndex = from.columns.indexOfFirst { it.id == relationship.fromColumnId }
            val toColumnIndex = to.columns.indexOfFirst { it.id == relationship.toColumnId }
            if (fromColumnIndex == -1 || toColumnIndex == -1) return@mapNotNull null

            PdfRoutedRelationship(
                relationship = relationship,
                route = router.route(
                    from = boxesById.getValue(from.id),
                    fromY = from.y + columnCenterOffsetY(fromColumnIndex),
                    to = boxesById.getValue(to.id),
                    toY = to.y + columnCenterOffsetY(toColumnIndex)
                )
            )
        }
    }

    fun widthOf(table: TableEntity): Float = widths[table.id] ?: TableCardMinWidthPt
}

private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.FILL
}

private fun strokePaint(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
}

private fun textPaint(size: Float, color: Int, typeface: Typeface = Typeface.DEFAULT): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
    }

private fun tableAccentColor(tableColor: TableColor): Int = when (tableColor) {
    TableColor.INDIGO -> ColorPrimary
    TableColor.EMERALD -> ColorAccent2
    TableColor.AMBER -> ColorAmber
    TableColor.ROSE -> ColorRose
    TableColor.VIOLET -> ColorViolet
    TableColor.SKY -> ColorSky
}

/**
 * Та же логика, что и в DiagramEditorScreen.kt (isManyAt там же): левая сторона короткой подписи
 * связи ("1 – N") — про from, правая — про to.
 */
private fun RelationshipType.isManyAt(isFromEnd: Boolean): Boolean = when (this) {
    RelationshipType.ONE_TO_ONE -> false
    RelationshipType.ONE_TO_MANY -> !isFromEnd
    RelationshipType.MANY_TO_MANY -> true
}

/**
 * Все Paint'ы экспорта — созданы один раз на export() и переиспользуются и для замера текста
 * (measureTableWidth), и для самой отрисовки: те же инстансы гарантируют, что раскладка не
 * разъедется с тем, что реально нарисовано.
 */
private class PdfPaints {
    val title = textPaint(16f, ColorInk, Typeface.DEFAULT_BOLD)
    val subtitle = textPaint(10f, ColorInkMuted)

    val headerText = textPaint(14.5f, Color.WHITE, Typeface.DEFAULT_BOLD)
    val columnName = textPaint(12f, ColorInk)
    val columnNameFaint = textPaint(12f, ColorInkFaint)
    val columnType = textPaint(10f, ColorInkFaint, Typeface.MONOSPACE)

    val pkBadgeText = textPaint(9f, ColorPrimary, Typeface.DEFAULT_BOLD)
    val pkBadgeBackground = fillPaint(ColorAccentSoft)
    val fkBadgeText = textPaint(9f, ColorAccent2, Typeface.DEFAULT_BOLD)
    val fkBadgeBackground = fillPaint(ColorAccent2Soft)

    val relationLabel = textPaint(12f, ColorPrimary)
    val relationLabelBackground = fillPaint(ColorBackground)
    val relationLabelBorder = strokePaint(ColorAccentSoft, 1f)

    val cardBackground = fillPaint(ColorSurface)
    val cardBorder = strokePaint(ColorOutline, 1f)
    val divider = strokePaint(ColorOutline, 0.75f)

    val connectorLine = strokePaint(ColorPrimary, 2f).apply { strokeCap = Paint.Cap.ROUND }

    val dot = fillPaint(ColorOutline)

    private val headerFillPaints: Map<TableColor, Paint> =
        TableColor.values().associateWith { fillPaint(tableAccentColor(it)) }

    fun header(color: TableColor): Paint = headerFillPaints.getValue(color)
}

// ---- Визуальные константы — зеркалят DotGridStep/TableWidthDots/TableHeaderDots/TableRowDots
// и отступы карточек в DiagramEditorScreen.kt, GRID_STEP в DiagramEditorViewModel.kt. Отдельный
// набор осознанно: домену/data-слою незачем тянуть Compose как зависимость ради общих чисел.
// Поменяете раскладку на холсте — поправьте те же значения и здесь. ----
private const val GridStepPt = 16f
private const val TableHeaderDots = 2
private const val TableRowDots = 2
private const val TableWidthDots = 9
private data class PdfRoutedRelationship(val relationship: Relationship, val route: ConnectorRoute)

private val TableCardMinWidthPt = GridStepPt * TableWidthDots

private const val CardHorizontalPaddingPt = 12f
private const val BadgeHorizontalPaddingPt = 5f
private const val BadgeVerticalPaddingPt = 1f
private const val BadgeSpacingPt = 5f
private const val BadgeCornerRadiusPt = 5f
private const val FieldRowMinGapPt = 16f
private const val CardCornerRadiusPt = 13f

private const val LabelHorizontalPaddingPt = 6f
private const val LabelVerticalPaddingPt = 2f
private const val LabelCornerRadiusPt = 8f
private const val RelationLabelWidthEstimatePt = 48f // = 2 * RelationLabelHalfWidth на холсте
private const val RelationLabelHeightEstimatePt = 22f // = 2 * RelationLabelHalfHeight на холсте

// Размер маркера кардинальности ("1"/"N" на конце связи) — те же числа, что MarkerReach/MarkerSpread
// в DiagramEditorScreen.kt, чтобы отметка не разъезжалась между экраном и PDF.
private const val MarkerReachPt = 11f
private const val MarkerSpreadPt = 5f
// Длина фиксированного горизонтального "хвостика" связи на выходе/входе — то же самое
// ConnectorStubLength в DiagramEditorScreen.kt; должна быть заметно больше MarkerReachPt.
private const val ConnectorStubPt = 20f
// Зазор между линией связи и таблицами и радиус скругления её углов — те же числа, что
// ConnectorClearance / ConnectorCornerRadius в DiagramEditorScreen.kt.
private const val ConnectorClearancePt = 10f
private const val ConnectorCornerRadiusPt = 8f
private const val DotGridRadiusPt = 1.1f
private const val MaxDotGridAreaPt2 = 6_000_000f

private const val PageMarginPt = 32f
private const val TitleAreaHeightPt = 44f
private const val EmptyPageWidthPt = 420f
private const val EmptyPageHeightPt = 300f

private val ColorBackground = Color.parseColor("#F5F6FB")
private val ColorSurface = Color.parseColor("#FFFFFF")
private val ColorInk = Color.parseColor("#14151A")
private val ColorInkMuted = Color.parseColor("#6B6E7A")
private val ColorInkFaint = Color.parseColor("#A2A5B3")
private val ColorOutline = Color.parseColor("#E5E7F0")
private val ColorPrimary = Color.parseColor("#5B5FEF")
private val ColorAccentSoft = Color.parseColor("#ECEDFD")
private val ColorAccent2 = Color.parseColor("#06B88A")
private val ColorAccent2Soft = Color.parseColor("#E2F8F1")
private val ColorAmber = Color.parseColor("#F5A524")
private val ColorRose = Color.parseColor("#F43F7E")
private val ColorViolet = Color.parseColor("#8B5CF6")
private val ColorSky = Color.parseColor("#0EA5E9")
