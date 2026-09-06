package com.vhronin.keyframedb.diagrameditor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vhronin.keyframedb.diagrameditor.domain.model.Relationship
import com.vhronin.keyframedb.diagrameditor.domain.model.TableColumn
import com.vhronin.keyframedb.diagrameditor.domain.model.TableEntity
import com.vhronin.keyframedb.theme.KeyframeDBExtendedTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val TableCardWidth = 140.dp
private val TableHeaderHeight = 34.dp
private val TableRowHeight = 30.dp
private val DotGridStep = 16.dp
private val DotRadius = 1.1.dp

private fun tableHeight(columnCount: Int): Dp = TableHeaderHeight + TableRowHeight * columnCount

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
                    onTableDrag = viewModel::onTableDrag,
                    modifier = Modifier.fillMaxSize()
                )

                EditorToolbar(
                    onAddRelationClick = viewModel::onAddRelationClick,
                    onZoomClick = { /* TODO: zoom/pan холста — следующий шаг */ },
                    onAddTableClick = viewModel::onAddTableClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }
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
    onTableDrag: (tableId: String, dx: Float, dy: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .drawBehind { drawDotGrid(dotColor) }
    ) {
        ConnectorLines(tables = tables, relationships = relationships)

        tables.forEachIndexed { index, table ->
            val accentColor = if (index % 2 == 0) {
                MaterialTheme.colorScheme.primary
            } else {
                KeyframeDBExtendedTheme.colors.accent2
            }
            TableCard(
                table = table,
                accentColor = accentColor,
                onDrag = { dx, dy -> onTableDrag(table.id, dx, dy) }
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDotGrid(color: Color) {
    val step = DotGridStep.toPx()
    val radius = DotRadius.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            drawCircle(color = color, radius = radius, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

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
    onDrag: (dx: Float, dy: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .offset(x = table.x.dp, y = table.y.dp)
            .width(TableCardWidth)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(13.dp))
            .pointerInput(table.id) {
                detectDragGestures { change, dragAmount ->
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
            FieldRow(column)
        }
    }
}

@Composable
private fun FieldRow(column: TableColumn) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                text = column.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = column.type,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = KeyframeDBExtendedTheme.colors.inkFaint
        )
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
