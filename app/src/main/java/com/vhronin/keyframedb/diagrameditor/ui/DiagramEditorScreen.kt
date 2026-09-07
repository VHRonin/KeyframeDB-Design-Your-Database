package com.vhronin.keyframedb.diagrameditor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vhronin.keyframedb.diagrameditor.domain.model.Relationship
import com.vhronin.keyframedb.diagrameditor.domain.model.TableColumn
import com.vhronin.keyframedb.diagrameditor.domain.model.TableEntity
import com.vhronin.keyframedb.theme.KeyframeDBExtendedTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// Ширина/высота таблиц выражены в "точках" сетки, а не в произвольных dp —
// это и есть то, к чему таблицы примагничиваются при перетаскивании (см. GRID_STEP в ViewModel).
private val DotGridStep = DiagramEditorViewModel.GRID_STEP.dp
private val DotRadius = 1.1.dp
private const val TableWidthDots = 9
private const val TableHeaderDots = 2
private const val TableRowDots = 2
private const val MinCanvasScale = 0.4f
private const val MaxCanvasScale = 3f
private val MenuWidth = 168.dp
// В меню три пункта: Редактировать / Переименовать сущность / Связь.
private val MenuHeightExpanded = 144.dp
private val MenuMargin = 8.dp

private val TableCardWidth = DotGridStep * TableWidthDots

private fun tableHeight(columnCount: Int): Dp =
    DotGridStep * (TableHeaderDots + TableRowDots * columnCount)

@Composable
fun DiagramEditorScreen(
    projectId: String = "1",
    onNavigateBack: () -> Unit = {},
    viewModel: DiagramEditorViewModel = koinViewModel(parameters = { parametersOf(projectId) })
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                DiagramEditorNavigationEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            EditorTopBar(
                projectName = uiState.projectName,
                isSaved = uiState.isSaved,
                onBackClick = viewModel::onBackClick,
                onUndoClick = viewModel::onUndoClick,
                onExportClick = viewModel::onExportClick
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                DiagramCanvas(
                    tables = uiState.tables,
                    relationships = uiState.relationships,
                    selectedTableId = uiState.selectedTableId,
                    hasDraggedSinceSelection = uiState.hasDraggedSinceSelection,
                    attributeEditingTableId = uiState.attributeEditingTableId,
                    isOverlayHidden = uiState.renamingTableId != null || uiState.editingColumn != null,
                    onTableDrag = viewModel::onTableDrag,
                    onTableDragEnd = viewModel::onTableDragEnd,
                    onTableSelected = viewModel::onTableSelected,
                    onCanvasTap = viewModel::onCanvasTap,
                    onRenameTableClick = viewModel::onRenameTableClick,
                    onEditAttributesClick = viewModel::onEditAttributesClick,
                    onBuildRelationshipClick = viewModel::onBuildRelationshipClick,
                    onAddColumnClick = viewModel::onAddColumnClick,
                    onDeleteColumn = viewModel::onDeleteColumn,
                    onColumnClick = viewModel::onColumnClick,
                    modifier = Modifier.fillMaxSize()
                )

                EditorToolbar(
                    onAddRelationClick = viewModel::onAddRelationClick,
                    onZoomClick = { /* TODO: следующий шаг */ },
                    onAddTableClick = viewModel::onAddTableClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }

    val renamingTable = uiState.tables.find { it.id == uiState.renamingTableId }
    if (renamingTable != null) {
        RenameTableSheet(
            table = renamingTable,
            onDismiss = viewModel::onRenameDismiss,
            onConfirm = { newName -> viewModel.onTableRenamed(renamingTable.id, newName) }
        )
    }

    val editingRef = uiState.editingColumn
    val editingTable = uiState.tables.find { it.id == editingRef?.tableId }
    val editingColumnValue = editingTable?.columns?.find { it.id == editingRef?.columnId }
    if (editingRef != null && editingTable != null && editingColumnValue != null) {
        EditColumnSheet(
            tableName = editingTable.name,
            column = editingColumnValue,
            onDismiss = viewModel::onColumnEditDismiss,
            onNameChange = { viewModel.onColumnNameChanged(editingTable.id, editingColumnValue.id, it) },
            onTypeChange = { viewModel.onColumnTypeChanged(editingTable.id, editingColumnValue.id, it) },
            onTogglePrimaryKey = { viewModel.onTogglePrimaryKey(editingTable.id, editingColumnValue.id) },
            onToggleForeignKey = { viewModel.onToggleForeignKey(editingTable.id, editingColumnValue.id) },
            onDelete = { viewModel.onDeleteColumn(editingTable.id, editingColumnValue.id) }
        )
    }
}

@Composable
private fun EditorTopBar(
    projectName: String,
    isSaved: Boolean,
    onBackClick: () -> Unit,
    onUndoClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Назад",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = projectName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isSaved) "Сохранено" else "Есть несохранённые изменения",
                style = MaterialTheme.typography.bodySmall,
                color = KeyframeDBExtendedTheme.colors.inkMuted
            )
        }

        IconButton(onClick = onUndoClick) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Отменить",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(onClick = onExportClick) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = "Экспорт в PDF",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun DiagramCanvas(
    tables: List<TableEntity>,
    relationships: List<Relationship>,
    selectedTableId: String?,
    hasDraggedSinceSelection: Boolean,
    attributeEditingTableId: String?,
    isOverlayHidden: Boolean,
    onTableDrag: (tableId: String, dx: Float, dy: Float) -> Unit,
    onTableDragEnd: (tableId: String) -> Unit,
    onTableSelected: (tableId: String) -> Unit,
    onCanvasTap: () -> Unit,
    onRenameTableClick: (tableId: String) -> Unit,
    onEditAttributesClick: (tableId: String) -> Unit,
    onBuildRelationshipClick: (tableId: String) -> Unit,
    onAddColumnClick: (tableId: String) -> Unit,
    onDeleteColumn: (tableId: String, columnId: String) -> Unit,
    onColumnClick: (tableId: String, columnId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current

    // Камера холста: во сколько раз увеличено (scale) и насколько сдвинуто (panOffset, в px).
    // Чисто визуальное UI-состояние экрана — в ViewModel его не выносим, там нечего хранить.
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasSizePx by remember { mutableStateOf(IntSize.Zero) }
    // Какая таблица прямо сейчас реально тащится пальцем — отдельно от "выбрана".
    // Пока идёт перетаскивание, плавающее меню прячем (см. п.1 запроса).
    var draggingTableId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .onSizeChanged { canvasSizePx = it }
            .drawBehind { drawDotGrid(dotColor, scale, panOffset) }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCanvasTap() })
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MinCanvasScale, MaxCanvasScale)
                    // Компенсируем сдвиг так, чтобы зум "рос" из точки под пальцами, а не из угла холста.
                    val appliedZoom = newScale / scale
                    panOffset = (panOffset - centroid) * appliedZoom + centroid + pan
                    scale = newScale
                }
            }
    ) {
        // Всё содержимое "мира" — точки уже нарисованы выше отдельно, здесь только сами
        // объекты диаграммы, которые должны двигаться/масштабироваться как единое целое.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = panOffset.x,
                    translationY = panOffset.y,
                    transformOrigin = TransformOrigin(0f, 0f)
                )
        ) {
            ConnectorLines(tables = tables, relationships = relationships)

            tables.forEachIndexed { index, table ->
                val accentColor = if (index % 2 == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    KeyframeDBExtendedTheme.colors.accent2
                }
                key(table.id) {
                    TableCard(
                        table = table,
                        accentColor = accentColor,
                        isSelected = selectedTableId == table.id,
                        isDragging = draggingTableId == table.id,
                        isEditingAttributes = attributeEditingTableId == table.id,
                        onTap = { onTableSelected(table.id) },
                        onDrag = { dx, dy -> onTableDrag(table.id, dx, dy) },
                        onDragStart = {
                            onTableSelected(table.id)
                            draggingTableId = table.id
                        },
                        onDragEnd = {
                            onTableDragEnd(table.id)
                            draggingTableId = null
                        },
                        onDragCancel = { draggingTableId = null },
                        onDeleteColumn = { columnId -> onDeleteColumn(table.id, columnId) },
                        onColumnClick = { columnId -> onColumnClick(table.id, columnId) },
                        onAddColumnClick = { onAddColumnClick(table.id) }
                    )
                }
            }
        }

        // Меню действий рисуем поверх трансформированного слоя, не внутри него —
        // иначе кнопки становились бы крошечными при отдалении холста.
        // Показываем его только в окне "таблицу выделили, но ещё не двигали":
        // прячем, пока таблицу реально тащат, после того как её подвинули и отпустили,
        // и пока открыт какой-то BottomSheet.
        if (!isOverlayHidden) {
            val selectedTable = tables.find { it.id == selectedTableId }
            if (selectedTable != null && draggingTableId == null && !hasDraggedSinceSelection) {
                TableActionMenu(
                    table = selectedTable,
                    scale = scale,
                    panOffset = panOffset,
                    canvasSizePx = canvasSizePx,
                    density = density,
                    onEditAttributesClick = { onEditAttributesClick(selectedTable.id) },
                    onRenameClick = { onRenameTableClick(selectedTable.id) },
                    onBuildRelationClick = { onBuildRelationshipClick(selectedTable.id) }
                )
            }
        }
    }
}

/**
 * Плавающее меню действий над/под выбранной таблицей. Позиция считается в экранных
 * координатах (через текущие scale/panOffset), а не помещается в трансформированный
 * слой — размер кнопок должен оставаться удобным для нажатия на любом масштабе.
 */
@Composable
private fun TableActionMenu(
    table: TableEntity,
    scale: Float,
    panOffset: Offset,
    canvasSizePx: IntSize,
    density: Density,
    onEditAttributesClick: () -> Unit,
    onRenameClick: () -> Unit,
    onBuildRelationClick: () -> Unit
) {
    val menuHeightPx = with(density) { MenuHeightExpanded.toPx() }
    val menuWidthPx = with(density) { MenuWidth.toPx() }
    val marginPx = with(density) { MenuMargin.toPx() }

    val tableLeftPx = with(density) { table.x.dp.toPx() } * scale + panOffset.x
    val tableTopPx = with(density) { table.y.dp.toPx() } * scale + panOffset.y
    val tableHeightPx = with(density) { tableHeight(table.columns.size).toPx() } * scale

    // Если сверху не помещается — показываем меню снизу от таблицы.
    val placeAbove = tableTopPx >= menuHeightPx + marginPx
    val menuYPx = if (placeAbove) {
        tableTopPx - menuHeightPx - marginPx
    } else {
        tableTopPx + tableHeightPx + marginPx
    }

    // По горизонтали держим меню в границах холста, даже если таблица у самого края.
    val maxMenuXPx = (canvasSizePx.width - menuWidthPx - marginPx).coerceAtLeast(marginPx)
    val menuXPx = tableLeftPx.coerceIn(marginPx, maxMenuXPx)

    Column(
        modifier = Modifier
            .offset(x = with(density) { menuXPx.toDp() }, y = with(density) { menuYPx.toDp() })
            .width(MenuWidth)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        MenuAction(icon = Icons.Default.Edit, label = "Редактировать", onClick = onEditAttributesClick)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuAction(icon = Icons.Default.DriveFileRenameOutline, label = "Переименовать сущность", onClick = onRenameClick)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuAction(icon = Icons.Default.Link, label = "Связь", onClick = onBuildRelationClick)
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Точки считаются заново на каждый кадр от текущих scale/pan, а не масштабируются как
 * готовая картинка — иначе при отдалении вокруг холста были бы пустые поля вместо
 * "бесконечной" сетки. Всегда рисуем ровно столько точек, сколько видно в текущей области.
 */
private fun DrawScope.drawDotGrid(color: Color, scale: Float, panOffset: Offset) {
    val step = DotGridStep.toPx() * scale
    if (step < 1f) return

    val radius = (DotRadius.toPx() * scale).coerceAtLeast(0.5f)
    val startX = panOffset.x.safeMod(step) - step
    val startY = panOffset.y.safeMod(step) - step

    var y = startY
    while (y < size.height + step) {
        var x = startX
        while (x < size.width + step) {
            drawCircle(color = color, radius = radius, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

private fun Float.safeMod(step: Float): Float = ((this % step) + step) % step

@Composable
private fun ConnectorLines(
    tables: List<TableEntity>,
    relationships: List<Relationship>
) {
    val density = LocalDensity.current
    val lineColor = MaterialTheme.colorScheme.primary
    val tablesById = tables.associateBy { it.id }

    Canvas(modifier = Modifier.fillMaxSize()) {
        relationships.forEach { relationship ->
            val from = tablesById[relationship.fromTableId] ?: return@forEach
            val to = tablesById[relationship.toTableId] ?: return@forEach

            val start = with(density) {
                Offset(
                    x = (from.x.dp + TableCardWidth).toPx(),
                    y = (from.y.dp + tableHeight(from.columns.size) / 2).toPx()
                )
            }
            val end = with(density) {
                Offset(
                    x = to.x.dp.toPx(),
                    y = (to.y.dp + tableHeight(to.columns.size) / 2).toPx()
                )
            }

            val controlOffsetX = (end.x - start.x) * 0.6f
            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(
                    start.x + controlOffsetX, start.y,
                    end.x - controlOffsetX, end.y,
                    end.x, end.y
                )
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = start)
            drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = end)
        }
    }

    relationships.forEach { relationship ->
        val from = tablesById[relationship.fromTableId] ?: return@forEach
        val to = tablesById[relationship.toTableId] ?: return@forEach
        if (relationship.label.isNotBlank()) {
            val midX = (from.x + to.x + TableCardWidth.value) / 2f
            val midY = (from.y + to.y) / 2f + 12f
            RelationLabel(
                text = relationship.label,
                modifier = Modifier.offset(x = midX.dp, y = midY.dp)
            )
        }
    }
}

@Composable
private fun RelationLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, KeyframeDBExtendedTheme.colors.accentSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun TableCard(
    table: TableEntity,
    accentColor: Color,
    isSelected: Boolean,
    isDragging: Boolean,
    isEditingAttributes: Boolean,
    onTap: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDeleteColumn: (columnId: String) -> Unit,
    onColumnClick: (columnId: String) -> Unit,
    onAddColumnClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardScale by animateFloatAsState(targetValue = if (isDragging) 1.06f else 1f, label = "tableScale")
    // Инлайн-редактирование (крестики на атрибутах + плюсик снизу) показываем только когда
    // пользователь явно включил его кнопкой "Редактировать" в плавающем меню —
    // не просто по факту выделения таблицы, и не во время перетаскивания.
    val showEditAffordances = isEditingAttributes && !isDragging

    Column(modifier = modifier.offset(x = table.x.dp, y = table.y.dp)) {
        Column(
            modifier = Modifier
                .width(TableCardWidth)
                .scale(cardScale)
                .shadow(
                    elevation = if (isDragging) 10.dp else 0.dp,
                    shape = RoundedCornerShape(13.dp)
                )
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (isDragging) 1.5.dp else 1.dp,
                    color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(13.dp)
                )
                .pointerInput(table.id) {
                    // Отдельный детектор простого тапа — выделяет таблицу без запуска drag,
                    // чтобы плавающее меню могло появиться сразу по короткому нажатию.
                    detectTapGestures(onTap = { onTap() })
                }
                .pointerInput(table.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() }
                    ) { change, dragAmount ->
                        change.consume()
                        with(density) {
                            onDrag(dragAmount.x.toDp().value, dragAmount.y.toDp().value)
                        }
                    }
                }
        ) {
            Text(
                text = table.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )

            table.columns.forEachIndexed { index, column ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                key(column.id) {
                    FieldRow(
                        column = column,
                        isEditable = showEditAffordances,
                        onClick = { onColumnClick(column.id) },
                        onDelete = { onDeleteColumn(column.id) }
                    )
                }
            }
        }

        if (showEditAffordances) {
            AddColumnButton(
                onClick = onAddColumnClick,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun AddColumnButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Добавить атрибут",
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun FieldRow(
    column: TableColumn,
    isEditable: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isEditable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (column.isPrimaryKey) {
                FieldBadge(
                    text = "PK",
                    color = MaterialTheme.colorScheme.primary,
                    background = KeyframeDBExtendedTheme.colors.accentSoft
                )
                Spacer(Modifier.width(5.dp))
            }
            if (column.isForeignKey) {
                FieldBadge(
                    text = "FK",
                    color = KeyframeDBExtendedTheme.colors.accent2,
                    background = KeyframeDBExtendedTheme.colors.accent2Soft
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = column.name.ifBlank { "…" },
                style = MaterialTheme.typography.bodySmall,
                color = if (column.name.isBlank()) KeyframeDBExtendedTheme.colors.inkFaint else MaterialTheme.colorScheme.onSurface
            )
        }

        if (isEditable) {
            IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить атрибут",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Text(
                text = column.type,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = KeyframeDBExtendedTheme.colors.inkFaint
            )
        }
    }
}

@Composable
private fun FieldBadge(text: String, color: Color, background: Color) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
private fun EditorToolbar(
    onAddRelationClick: () -> Unit,
    onZoomClick: () -> Unit,
    onAddTableClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolbarIconButton(
            icon = Icons.Default.Link,
            contentDescription = "Добавить связь",
            onClick = onAddRelationClick
        )
        ToolbarIconButton(
            icon = Icons.Default.ZoomIn,
            contentDescription = "Масштаб",
            onClick = onZoomClick
        )

        VerticalDivider(
            modifier = Modifier.height(20.dp),
            color = MaterialTheme.colorScheme.outline
        )

        IconButton(
            onClick = onAddTableClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить таблицу", tint = Color.White)
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = KeyframeDBExtendedTheme.colors.inkMuted
        )
    }
}

// ---- Общие элементы стиля для BottomSheet'ов ----

@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(KeyframeDBExtendedTheme.colors.accentSoft)
    )
}

@Composable
private fun SheetHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = KeyframeDBExtendedTheme.colors.inkMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameTableSheet(
    table: TableEntity,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var draftName by remember(table.id) { mutableStateOf(table.name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            SheetHeader(icon = Icons.Default.Edit, title = "Название таблицы", subtitle = "Текущее имя: ${table.name}")

            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                label = { Text("Имя таблицы") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onConfirm(draftName) },
                enabled = draftName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Готово")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditColumnSheet(
    tableName: String,
    column: TableColumn,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onTogglePrimaryKey: () -> Unit,
    onToggleForeignKey: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            SheetHeader(icon = Icons.Default.Edit, title = "Атрибут", subtitle = tableName)

            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = column.name,
                onValueChange = onNameChange,
                label = { Text("Имя атрибута") },
                placeholder = { Text("например, email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = column.type,
                onValueChange = onTypeChange,
                label = { Text("Тип") },
                placeholder = { Text("например, varchar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = column.isPrimaryKey,
                    onClick = onTogglePrimaryKey,
                    label = { Text("Первичный ключ") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = column.isForeignKey,
                    onClick = onToggleForeignKey,
                    label = { Text("Внешний ключ") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KeyframeDBExtendedTheme.colors.accent2,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    onDelete()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Удалить атрибут")
            }
        }
    }
}
