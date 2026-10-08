package com.vhronin.keyframedb.diagrameditor.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.vhronin.keyframedb.diagrameditor.domain.model.Relationship
import com.vhronin.keyframedb.diagrameditor.domain.model.RelationshipType
import com.vhronin.keyframedb.diagrameditor.domain.model.TableColor
import com.vhronin.keyframedb.diagrameditor.domain.model.TableColumn
import com.vhronin.keyframedb.diagrameditor.domain.model.TableEntity
import com.vhronin.keyframedb.theme.KeyframeDBExtendedTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

// Ширина/высота таблиц выражены в "точках" сетки, а не в произвольных dp —
// это и есть то, к чему таблицы примагничиваются при перетаскивании (см. GRID_STEP в ViewModel).
private val DotGridStep = DiagramEditorViewModel.GRID_STEP.dp
private val DotRadius = 1.1.dp
private const val TableWidthDots = 9
private const val TableHeaderDots = 2
private const val TableRowDots = 2
private const val MinCanvasScale = 0.4f
private const val MaxCanvasScale = 3f
// Насколько меняется scale за одно нажатие кнопки зума на тулбаре (20% за шаг).
private const val ZoomStepFactor = 1.2f

/**
 * Считает новые scale/panOffset для одного шага зума кнопкой на тулбаре — от центра видимой
 * области холста, той же математикой компенсации сдвига, что и в жесте пальцами (см. DiagramCanvas).
 */
private fun zoomStep(
    currentScale: Float,
    currentPan: Offset,
    canvasSizePx: IntSize,
    factor: Float
): Pair<Float, Offset> {
    val newScale = (currentScale * factor).coerceIn(MinCanvasScale, MaxCanvasScale)
    val appliedZoom = newScale / currentScale
    val centroid = Offset(canvasSizePx.width / 2f, canvasSizePx.height / 2f)
    val newPan = (currentPan - centroid) * appliedZoom + centroid
    return newScale to newPan
}

// Акцентные цвета таблиц, которых нет в основной палитре темы (Indigo/Emerald там уже есть
// как primary/accent2 — берём их оттуда, чтобы не дублировать). Небольшой курируемый набор,
// без произвольного RGB — см. TableColor в domain-слое.
private val TableColorAmber = Color(0xFFF5A524)
private val TableColorRose = Color(0xFFF43F7E)
private val TableColorViolet = Color(0xFF8B5CF6)
private val TableColorSky = Color(0xFF0EA5E9)

@Composable
private fun TableColor.toComposeColor(): Color = when (this) {
    TableColor.INDIGO -> MaterialTheme.colorScheme.primary
    TableColor.EMERALD -> KeyframeDBExtendedTheme.colors.accent2
    TableColor.AMBER -> TableColorAmber
    TableColor.ROSE -> TableColorRose
    TableColor.VIOLET -> TableColorViolet
    TableColor.SKY -> TableColorSky
}

private val MenuWidth = 168.dp
// В меню четыре пункта: Редактировать / Переименовать / Связь / Удалить.
private val MenuHeightExpanded = 192.dp
private val MenuMargin = 8.dp

// Раньше это была единственная, фиксированная ширина карточки. Теперь это только нижняя
// граница: карточка не бывает уже этого, но по факту подстраивается под содержимое —
// см. rememberTableWidths ниже.
private val TableCardMinWidth = DotGridStep * TableWidthDots

private fun tableHeight(columnCount: Int): Dp =
    DotGridStep * (TableHeaderDots + TableRowDots * columnCount)

/**
 * Y-смещение центра строки атрибута с данным индексом (0-based) от верхнего края карточки —
 * связи теперь идут не в геометрический центр таблицы, а в конкретную строку атрибута
 * (см. ConnectorLines). tableHeight(columnIndex) — это как раз высота заголовка плюс всех
 * строк ДО этой, т.е. Y её верхнего края; прибавляем половину высоты строки, чтобы попасть
 * в её середину — та же логика, что и раньше была для середины всей карточки.
 */
private fun columnCenterOffsetY(columnIndex: Int): Dp =
    tableHeight(columnIndex) + DotGridStep * TableRowDots / 2

// ---- Адаптивная ширина карточки таблицы ----
//
// Раскладка ровно повторяет то, что реально рисуют заголовок в TableCard и FieldRow (строка
// атрибута) ниже — те же стили и отступы, иначе измеренная ширина разъедется с отрисованной.

// Горизонтальные отступы заголовка и строки атрибута (padding(horizontal = 12.dp) в TableCard/FieldRow).
private val CardHorizontalPadding = 12.dp
// padding(horizontal = 5.dp) у FieldBadge + Spacer(5.dp) после неё.
private val BadgeHorizontalPadding = 5.dp
private val BadgeSpacing = 5.dp
// Минимальный зазор между именем атрибута и его типом. Строка использует
// Arrangement.SpaceBetween — без запаса при точно подогнанной под контент ширине карточки
// имя и тип легли бы вплотную друг к другу.
private val FieldRowMinGap = 16.dp

/** То, что реально влияет на ширину карточки — имя таблицы и её атрибуты, без x/y. */
private data class TableContentSignature(val name: String, val columns: List<TableColumn>)

/**
 * Ширина карточки под содержимое конкретной таблицы, с полом в TableCardMinWidth и
 * округлением вверх до шага сетки (чтобы правый край карточки, как и раньше, попадал
 * в точку сетки — сама сетка ведь только этому и служит).
 */
private fun computeTableCardWidth(
    signature: TableContentSignature,
    textMeasurer: TextMeasurer,
    headerStyle: TextStyle,
    nameStyle: TextStyle,
    typeStyle: TextStyle,
    badgeStyle: TextStyle,
    density: Density
): Dp {
    fun measuredWidth(text: String, style: TextStyle): Dp =
        with(density) { textMeasurer.measure(text, style).size.width.toDp() }

    val headerWidth = measuredWidth(signature.name.ifBlank { " " }, headerStyle) + CardHorizontalPadding * 2

    val rowWidths = signature.columns.map { column ->
        var leftGroupWidth = measuredWidth(column.name.ifBlank { "…" }, nameStyle)
        if (column.isPrimaryKey) {
            leftGroupWidth += measuredWidth("PK", badgeStyle) + BadgeHorizontalPadding * 2 + BadgeSpacing
        }
        if (column.isForeignKey) {
            leftGroupWidth += measuredWidth("FK", badgeStyle) + BadgeHorizontalPadding * 2 + BadgeSpacing
        }
        val typeWidth = measuredWidth(column.type, typeStyle)
        CardHorizontalPadding * 2 + leftGroupWidth + FieldRowMinGap + typeWidth
    }

    val contentWidth = (rowWidths + headerWidth).maxOrNull()?.coerceAtLeast(TableCardMinWidth) ?: TableCardMinWidth
    val steps = ceil(contentWidth.value / DotGridStep.value).toInt()
    return DotGridStep * steps
}

/**
 * Ширины карточек для всех таблиц на холсте — одним источником для TableCard (рисует карточку)
 * и ConnectorLines (считает, откуда и куда идут линии связей), чтобы не разъезжались.
 *
 * Ключ remember — не сам `tables`, а его "сигнатуры" (имя + атрибуты, без x/y): перетаскивание
 * таблицы на каждый кадр меняет только позицию, и без этого пересчёт измерения текста для
 * ВСЕХ таблиц гонялся бы на каждый пиксель драга.
 */
@Composable
private fun rememberTableWidths(tables: List<TableEntity>): Map<String, Dp> {
    val textMeasurer = rememberTextMeasurer()
    // Бейдж и тип рисуются без явного базового style (см. FieldBadge/FieldRow) — их итоговый
    // стиль это LocalTextStyle.current с точечными override'ами fontSize/fontWeight/fontFamily.
    // Меряем тем же сочетанием, а не голым TextStyle(...), чтобы ширина не разъехалась с тем,
    // что реально нарисуется.
    val baseTextStyle = LocalTextStyle.current
    val headerStyle = MaterialTheme.typography.titleMedium
    val nameStyle = MaterialTheme.typography.bodySmall
    val typeStyle = baseTextStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    val badgeStyle = baseTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
    val density = LocalDensity.current

    val signatures = tables.map { it.id to TableContentSignature(it.name, it.columns) }

    return remember(signatures, textMeasurer, headerStyle, nameStyle, typeStyle, badgeStyle, density) {
        signatures.associate { (id, signature) ->
            id to computeTableCardWidth(
                signature = signature,
                textMeasurer = textMeasurer,
                headerStyle = headerStyle,
                nameStyle = nameStyle,
                typeStyle = typeStyle,
                badgeStyle = badgeStyle,
                density = density
            )
        }
    }
}

@Composable
fun DiagramEditorScreen(
    projectId: String = "1",
    onNavigateBack: () -> Unit = {},
    viewModel: DiagramEditorViewModel = koinViewModel(parameters = { parametersOf(projectId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Камера холста живёт здесь, а не внутри DiagramCanvas — тулбар с кнопками зума лежит
    // рядом с холстом (сосед по Box), а не внутри него, и тоже должен уметь ей управлять.
    var canvasScale by remember { mutableStateOf(1f) }
    var canvasPanOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasSizePx by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                DiagramEditorNavigationEvent.NavigateBack -> onNavigateBack()
                is DiagramEditorNavigationEvent.SharePdf -> sharePdf(context, event.file)
                DiagramEditorNavigationEvent.ExportFailed -> Toast.makeText(
                    context,
                    "Не удалось экспортировать PDF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Системный жест/кнопка "Назад" раньше обходили viewModel.onBackClick() стороной — NavHost
    // всплывал по умолчанию сразу, не дожидаясь, пока ViewModel досохранит то, что ещё не успел
    // debounce автосохранения (см. persist в DiagramEditorViewModel.onBackClick). Из-за этого
    // переименование диаграммы прямо перед системным "назад" на экране списка иногда мелькало
    // старым именем, пока список не перечитывался заново при следующем входе. Заворачиваем тот
    // же путь и на системный back — теперь оба способа выйти с экрана идут через один и тот же
    // немедленный flush перед навигацией.
    BackHandler(onBack = viewModel::onBackClick)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            EditorTopBar(
                projectName = uiState.projectName,
                isSaved = uiState.isSaved,
                canUndo = uiState.canUndo,
                isExporting = uiState.isExporting,
                onBackClick = viewModel::onBackClick,
                onUndoClick = viewModel::onUndoClick,
                onExportClick = viewModel::onExportClick,
                onRenameProjectClick = viewModel::onRenameProjectClick
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                DiagramCanvas(
                    tables = uiState.tables,
                    relationships = uiState.relationships,
                    selectedTableId = uiState.selectedTableId,
                    hasDraggedSinceSelection = uiState.hasDraggedSinceSelection,
                    attributeEditingTableId = uiState.attributeEditingTableId,
                    linkingState = uiState.linkingState,
                    isOverlayHidden = uiState.renamingTableId != null ||
                            uiState.editingColumn != null ||
                            uiState.pendingRelationship != null ||
                            uiState.editingRelationshipId != null ||
                            uiState.isRenamingProject,
                    onTableDrag = viewModel::onTableDrag,
                    onTableDragEnd = viewModel::onTableDragEnd,
                    onTableSelected = viewModel::onTableSelected,
                    onCanvasTap = viewModel::onCanvasTap,
                    onRenameTableClick = viewModel::onRenameTableClick,
                    onEditAttributesClick = viewModel::onEditAttributesClick,
                    onBuildRelationshipClick = viewModel::onBuildRelationshipClick,
                    onDeleteTableClick = viewModel::onDeleteTable,
                    onLinkColumnTap = viewModel::onLinkColumnTap,
                    onCancelLinking = viewModel::onCancelLinking,
                    onRelationshipBadgeClick = viewModel::onRelationshipBadgeClick,
                    onAddColumnClick = viewModel::onAddColumnClick,
                    onDeleteColumn = viewModel::onDeleteColumn,
                    onColumnClick = viewModel::onColumnClick,
                    scale = canvasScale,
                    onScaleChange = { canvasScale = it },
                    panOffset = canvasPanOffset,
                    onPanOffsetChange = { canvasPanOffset = it },
                    canvasSizePx = canvasSizePx,
                    onCanvasSizeChange = { canvasSizePx = it },
                    modifier = Modifier.fillMaxSize()
                )

                EditorToolbar(
                    onAddRelationClick = viewModel::onAddRelationClick,
                    onZoomInClick = {
                        val (newScale, newPan) = zoomStep(canvasScale, canvasPanOffset, canvasSizePx, ZoomStepFactor)
                        canvasScale = newScale
                        canvasPanOffset = newPan
                    },
                    onZoomOutClick = {
                        val (newScale, newPan) = zoomStep(canvasScale, canvasPanOffset, canvasSizePx, 1f / ZoomStepFactor)
                        canvasScale = newScale
                        canvasPanOffset = newPan
                    },
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
            onConfirm = { newName -> viewModel.onTableRenamed(renamingTable.id, newName) },
            onColorChange = { color -> viewModel.onTableColorChanged(renamingTable.id, color) }
        )
    }

    if (uiState.isRenamingProject) {
        RenameProjectSheet(
            currentName = uiState.projectName,
            onDismiss = viewModel::onProjectRenameDismiss,
            onConfirm = viewModel::onProjectRenamed
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

    val pending = uiState.pendingRelationship
    val pendingFromTable = uiState.tables.find { it.id == pending?.fromTableId }
    val pendingFromColumn = pendingFromTable?.columns?.find { it.id == pending?.fromColumnId }
    val pendingToTable = uiState.tables.find { it.id == pending?.toTableId }
    val pendingToColumn = pendingToTable?.columns?.find { it.id == pending?.toColumnId }
    if (pending != null && pendingFromTable != null && pendingFromColumn != null &&
        pendingToTable != null && pendingToColumn != null
    ) {
        RelationshipSheet(
            fromLabel = attributeLabel(pendingFromTable.name, pendingFromColumn.name),
            toLabel = attributeLabel(pendingToTable.name, pendingToColumn.name),
            type = pending.type,
            isNew = true,
            onTypeChange = viewModel::onPendingRelationshipTypeChanged,
            onDismiss = viewModel::onDismissPendingRelationship,
            onConfirmNew = viewModel::onConfirmPendingRelationship
        )
    }

    val editingRelationship = uiState.relationships.find { it.id == uiState.editingRelationshipId }
    val editingRelFromTable = uiState.tables.find { it.id == editingRelationship?.fromTableId }
    val editingRelFromColumn = editingRelFromTable?.columns?.find { it.id == editingRelationship?.fromColumnId }
    val editingRelToTable = uiState.tables.find { it.id == editingRelationship?.toTableId }
    val editingRelToColumn = editingRelToTable?.columns?.find { it.id == editingRelationship?.toColumnId }
    if (editingRelationship != null && editingRelFromTable != null && editingRelFromColumn != null &&
        editingRelToTable != null && editingRelToColumn != null
    ) {
        RelationshipSheet(
            fromLabel = attributeLabel(editingRelFromTable.name, editingRelFromColumn.name),
            toLabel = attributeLabel(editingRelToTable.name, editingRelToColumn.name),
            type = editingRelationship.type,
            isNew = false,
            onTypeChange = viewModel::onEditingRelationshipTypeChanged,
            onDismiss = viewModel::onRelationshipEditDismiss,
            onDelete = { viewModel.onDeleteRelationship(editingRelationship.id) }
        )
    }
}

@Composable
private fun EditorTopBar(
    projectName: String,
    isSaved: Boolean,
    canUndo: Boolean,
    isExporting: Boolean,
    onBackClick: () -> Unit,
    onUndoClick: () -> Unit,
    onExportClick: () -> Unit,
    onRenameProjectClick: () -> Unit
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

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRenameProjectClick)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Переименовать схему",
                    tint = KeyframeDBExtendedTheme.colors.inkMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = if (isSaved) "Сохранено" else "Есть несохранённые изменения",
                style = MaterialTheme.typography.bodySmall,
                color = KeyframeDBExtendedTheme.colors.inkMuted
            )
        }

        IconButton(onClick = onUndoClick, enabled = canUndo) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Отменить",
                tint = if (canUndo) MaterialTheme.colorScheme.onBackground else KeyframeDBExtendedTheme.colors.inkMuted
            )
        }
        IconButton(onClick = onExportClick, enabled = !isExporting) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Экспорт в PDF",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/**
 * Открывает системный диалог "Поделиться" для готового PDF — через FileProvider, т.к. начиная
 * с Android N голый file:// Uri в Intent между приложениями запрещён (StrictMode/FileUriExposedException).
 * authority "$packageName.fileprovider" должен быть объявлен в AndroidManifest.xml (см. res/xml/file_paths.xml).
 */
private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Поделиться диаграммой")
    // LocalContext в Compose обычно и есть сама Activity, но на случай обёрнутого контекста —
    // без NEW_TASK запуск Activity из не-Activity контекста упадёт с исключением.
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

@Composable
private fun DiagramCanvas(
    tables: List<TableEntity>,
    relationships: List<Relationship>,
    selectedTableId: String?,
    hasDraggedSinceSelection: Boolean,
    attributeEditingTableId: String?,
    linkingState: LinkingState?,
    isOverlayHidden: Boolean,
    onTableDrag: (tableId: String, dx: Float, dy: Float) -> Unit,
    onTableDragEnd: (tableId: String) -> Unit,
    onTableSelected: (tableId: String) -> Unit,
    onCanvasTap: () -> Unit,
    onRenameTableClick: (tableId: String) -> Unit,
    onEditAttributesClick: (tableId: String) -> Unit,
    onBuildRelationshipClick: (tableId: String) -> Unit,
    onDeleteTableClick: (tableId: String) -> Unit,
    onLinkColumnTap: (tableId: String, columnId: String) -> Unit,
    onCancelLinking: () -> Unit,
    onRelationshipBadgeClick: (relationshipId: String) -> Unit,
    onAddColumnClick: (tableId: String) -> Unit,
    onDeleteColumn: (tableId: String, columnId: String) -> Unit,
    onColumnClick: (tableId: String, columnId: String) -> Unit,
    // Камера холста вынесена наружу (а не просто remember здесь), чтобы кнопки зума в EditorToolbar,
    // который лежит рядом с DiagramCanvas, а не внутри него, тоже могли ей управлять.
    scale: Float,
    onScaleChange: (Float) -> Unit,
    panOffset: Offset,
    onPanOffsetChange: (Offset) -> Unit,
    canvasSizePx: IntSize,
    onCanvasSizeChange: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    // Единый источник ширины карточки на таблицу — им пользуются и сами карточки ниже,
    // и ConnectorLines (концы линий связей должны упираться именно в край карточки).
    val tableWidths = rememberTableWidths(tables)

    // pointerInput(Unit) ниже живёт на весь жизненный цикл холста и не перезапускается —
    // поэтому, как и в TableCard, читаем/пишем камеру через rememberUpdatedState, а не напрямую
    // через параметры: иначе жест масштабирования видел бы значения только первой композиции.
    val currentScale by rememberUpdatedState(scale)
    val currentPanOffset by rememberUpdatedState(panOffset)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnPanOffsetChange by rememberUpdatedState(onPanOffsetChange)
    // Какая таблица прямо сейчас реально тащится пальцем — нужна только для визуального
    // фидбека карточки во время перетаскивания (scale/тень в TableCard, см. isDragging).
    // Долгое нажатие саму таблицу больше не выделяет и меню не открывает — см. onDragStart ниже,
    // оно только двигает таблицу. Открывается меню исключительно тапом (onTableSelected).
    var draggingTableId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .onSizeChanged { onCanvasSizeChange(it) }
            .drawBehind { drawDotGrid(dotColor, scale, panOffset) }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCanvasTap() })
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (currentScale * zoom).coerceIn(MinCanvasScale, MaxCanvasScale)
                    // Компенсируем сдвиг так, чтобы зум "рос" из точки под пальцами, а не из угла холста.
                    val appliedZoom = newScale / currentScale
                    currentOnPanOffsetChange((currentPanOffset - centroid) * appliedZoom + centroid + pan)
                    currentOnScaleChange(newScale)
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
            // Пока идёт связывание, подписи существующих связей временно не тапабельны —
            // иначе было бы неясно, что имел в виду тап: выбрать таблицу или отредактировать связь.
            val isLinking = linkingState != null
            ConnectorLines(
                tables = tables,
                relationships = relationships,
                tableWidths = tableWidths,
                onRelationshipClick = if (isLinking) null else onRelationshipBadgeClick
            )

            // Пока идёт связывание, тап по СТРОКЕ АТРИБУТА (а не по таблице целиком) выбирает
            // первую/вторую точку связи, а перетаскивание временно выключаем — чтобы случайный
            // сдвиг пальца не сбивал режим. Пока источник ещё не выбран целиком и таблица-
            // источник уже известна заранее (запуск из меню конкретной таблицы, см.
            // onBuildRelationshipClick), тапабельны атрибуты только внутри неё — остальные
            // таблицы на этом шаге визуально притушены (см. isLinkableForColumnTap ниже).
            val isChoosingSource = isLinking && linkingState?.fromColumnId == null
            tables.forEach { table ->
                key(table.id) {
                    val isLinkableForColumnTap = when {
                        !isLinking -> false
                        isChoosingSource -> linkingState?.fromTableId == null || linkingState.fromTableId == table.id
                        else -> true
                    }
                    TableCard(
                        table = table,
                        width = tableWidths[table.id] ?: TableCardMinWidth,
                        accentColor = table.color.toComposeColor(),
                        isSelected = selectedTableId == table.id,
                        isDragging = draggingTableId == table.id,
                        isEditingAttributes = attributeEditingTableId == table.id,
                        isLinkSource = linkingState?.fromTableId == table.id,
                        isLinking = isLinking,
                        isLinkableForColumnTap = isLinkableForColumnTap,
                        linkingSourceColumnId = if (linkingState?.fromTableId == table.id) linkingState.fromColumnId else null,
                        onTap = { if (!isLinking) onTableSelected(table.id) },
                        onDrag = { dx, dy -> if (!isLinking) onTableDrag(table.id, dx, dy) },
                        onDragStart = {
                            if (!isLinking) {
                                draggingTableId = table.id
                            }
                        },
                        onDragEnd = {
                            if (!isLinking) {
                                onTableDragEnd(table.id)
                                draggingTableId = null
                            }
                        },
                        onDragCancel = { if (!isLinking) draggingTableId = null },
                        onDeleteColumn = { columnId -> onDeleteColumn(table.id, columnId) },
                        onColumnClick = { columnId -> onColumnClick(table.id, columnId) },
                        onColumnLinkTap = { columnId -> onLinkColumnTap(table.id, columnId) },
                        onAddColumnClick = { onAddColumnClick(table.id) }
                    )
                }
            }
        }

        // Меню действий рисуем поверх трансформированного слоя, не внутри него —
        // иначе кнопки становились бы крошечными при отдалении холста.
        // Открывается только тапом по таблице (onTableSelected) и остаётся видимым, пока таблицу
        // не подвинули, не тапнули по другому месту, не открыли BottomSheet или режим связывания
        // (тогда вместо меню показан баннер выбора таблицы) — см. onDragStart, там меню не открывается.
        val menuTable = if (!isOverlayHidden && linkingState == null) {
            tables.find { it.id == selectedTableId }?.takeIf { !hasDraggedSinceSelection }
        } else {
            null
        }
        // AnimatedVisibility на время exit-анимации продолжает показывать последний контент,
        // поэтому держим последнюю ненулевую таблицу отдельно — иначе на затухании было бы нечего рисовать.
        var lastMenuTable by remember { mutableStateOf<TableEntity?>(null) }
        if (menuTable != null) {
            lastMenuTable = menuTable
        }
        AnimatedVisibility(
            visible = menuTable != null,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f, animationSpec = tween(140)),
            exit = fadeOut(tween(110)) + scaleOut(targetScale = 0.85f, animationSpec = tween(110))
        ) {
            lastMenuTable?.let { table ->
                TableActionMenu(
                    table = table,
                    scale = scale,
                    panOffset = panOffset,
                    canvasSizePx = canvasSizePx,
                    density = density,
                    onEditAttributesClick = { onEditAttributesClick(table.id) },
                    onRenameClick = { onRenameTableClick(table.id) },
                    onBuildRelationClick = { onBuildRelationshipClick(table.id) },
                    onDeleteClick = { onDeleteTableClick(table.id) }
                )
            }
        }

        // Баннер живёт на протяжении обоих шагов связывания (выбор первой и второй таблицы),
        // поэтому "последнее известное" здесь — это весь LinkingState, а не одна таблица.
        var lastLinkingState by remember { mutableStateOf<LinkingState?>(null) }
        if (linkingState != null) {
            lastLinkingState = linkingState
        }
        val linkingSourceTable = tables.find { it.id == lastLinkingState?.fromTableId }
        val linkingSourceColumn = linkingSourceTable?.columns?.find { it.id == lastLinkingState?.fromColumnId }
        AnimatedVisibility(
            visible = linkingState != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(110))
        ) {
            LinkingModeBanner(
                sourceTableName = linkingSourceTable?.name,
                sourceColumnName = linkingSourceColumn?.name,
                onCancel = onCancelLinking
            )
        }
    }
}

/**
 * Баннер поверх холста на время выбора АТРИБУТОВ для связи (см.
 * onBuildRelationshipClick/onAddRelationClick). sourceColumnName приходит только после того,
 * как источник выбран целиком — пока задана лишь таблица (запуск из её меню), но конкретный
 * атрибут внутри неё ещё не тапнули, он остаётся null.
 */
@Composable
private fun LinkingModeBanner(sourceTableName: String?, sourceColumnName: String?, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = when {
                sourceColumnName != null ->
                    "Выберите атрибут для связи с «${attributeLabel(sourceTableName.orEmpty(), sourceColumnName)}»"
                sourceTableName != null -> "Выберите атрибут в «$sourceTableName» для связи"
                else -> "Выберите атрибут-источник для связи"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Отменить создание связи",
                tint = KeyframeDBExtendedTheme.colors.inkMuted,
                modifier = Modifier.size(14.dp)
            )
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
    onBuildRelationClick: () -> Unit,
    onDeleteClick: () -> Unit
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
        MenuAction(icon = Icons.Default.DriveFileRenameOutline, label = "Переименовать", onClick = onRenameClick)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuAction(icon = Icons.Default.Link, label = "Связь", onClick = onBuildRelationClick)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuAction(
            icon = Icons.Default.Delete,
            label = "Удалить",
            onClick = onDeleteClick,
            contentColor = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, onClick: () -> Unit, contentColor: Color = MaterialTheme.colorScheme.primary) {
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
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = contentColor)
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

// Насколько "хвост" маркера кардинальности уходит вдоль линии связи от края таблицы и насколько
// расходятся его ножки — те же числа продублированы в PdfDiagramExporter (drawCardinalityMarker
// там же), чтобы отметки "1"/"N" на связи не разъезжались между экраном и PDF-экспортом.
private val MarkerReach = 11.dp
private val MarkerSpread = 5.dp

// Длина фиксированного горизонтального "хвостика" на выходе/входе связи — должна быть заметно
// больше MarkerReach, чтобы маркер целиком поместился на прямом участке (см. ConnectorLines).
private val ConnectorStubLength = 20.dp

// Нижняя граница смещения контрольных точек переходной кривой между хвостиками (см. controlOffset
// в ConnectorLines) — чтобы кривая не сгибалась резко, даже если оба хвостика оказались рядом
// и по X, и по Y.
private val ConnectorMinCurveOffset = 24.dp

/**
 * Чья сторона связи — "один" или "много", по типу связи и тому, это конец "from" или "to".
 * Читаем так же, как читается сама подпись связи (RelationshipType.shortLabel, "1 – N" —
 * первый символ про from, второй про to) и заголовок RelationshipSheet ("$fromLabel →
 * $toLabel") — левая сторона всегда про from, правая про to.
 */
private fun RelationshipType.isManyAt(isFromEnd: Boolean): Boolean = when (this) {
    RelationshipType.ONE_TO_ONE -> false
    RelationshipType.ONE_TO_MANY -> !isFromEnd
    RelationshipType.MANY_TO_MANY -> true
}

/**
 * ER-нотация "вороньей лапки" на конце линии связи: одна короткая перпендикулярная чёрточка —
 * кардинальность "один", раздвоенные ножки — "много" (третья, прямая "ножка" — сама линия связи,
 * отдельно её рисовать не нужно).
 *
 * `outward` здесь всегда СТРОГО горизонтален (см. ConnectorStubLength в ConnectorLines) — связь
 * специально построена так, что первые ConnectorStubLength от каждой таблицы идут прямо и
 * горизонтально, что бы ни было на другом конце. Раньше маркер ориентировался по касательной
 * кривой, которая тянется к цели — и если цель оказывалась близко по X, но далеко по Y (или вовсе
 * левее источника), кривая почти сразу уходила в сторону, и маркер либо повисал в стороне от
 * линии, либо уезжал так далеко, что казалось, будто его нет вовсе. Фиксированный прямой участок
 * у каждого конца убирает эту проблему полностью — маркер гарантированно виден при любом
 * взаимном расположении таблиц.
 */
private fun DrawScope.drawCardinalityMarker(point: Offset, outward: Offset, isMany: Boolean, color: Color, strokeWidthPx: Float) {
    val reachPx = MarkerReach.toPx()
    val spreadPx = MarkerSpread.toPx()
    val perp = Offset(-outward.y, outward.x)
    if (isMany) {
        val back = point + outward * reachPx
        drawLine(color, back - perp * spreadPx, point, strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
        drawLine(color, back + perp * spreadPx, point, strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
    } else {
        val tickCenter = point + outward * (reachPx * 0.65f)
        drawLine(color, tickCenter - perp * spreadPx, tickCenter + perp * spreadPx, strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
    }
}

@Composable
private fun ConnectorLines(
    tables: List<TableEntity>,
    relationships: List<Relationship>,
    tableWidths: Map<String, Dp>,
    onRelationshipClick: ((relationshipId: String) -> Unit)?
) {
    val density = LocalDensity.current
    val lineColor = MaterialTheme.colorScheme.primary
    val tablesById = tables.associateBy { it.id }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidthPx = 2.dp.toPx()
        val stubLenPx = ConnectorStubLength.toPx()
        relationships.forEach { relationship ->
            val from = tablesById[relationship.fromTableId] ?: return@forEach
            val to = tablesById[relationship.toTableId] ?: return@forEach
            // Индекс конкретного атрибута внутри карточки — если его больше нет (удалили
            // атрибут ещё до того, как каскадное удаление связи в ViewModel её убрало,
            // например при отмене отдельной сериализованной правки), тихо пропускаем связь,
            // как и раньше пропускали связи на несуществующие таблицы.
            val fromColumnIndex = from.columns.indexOfFirst { it.id == relationship.fromColumnId }
            val toColumnIndex = to.columns.indexOfFirst { it.id == relationship.toColumnId }
            if (fromColumnIndex == -1 || toColumnIndex == -1) return@forEach
            val fromWidth = tableWidths[from.id] ?: TableCardMinWidth

            val start = with(density) {
                Offset(
                    x = (from.x.dp + fromWidth).toPx(),
                    y = (from.y.dp + columnCenterOffsetY(fromColumnIndex)).toPx()
                )
            }
            val end = with(density) {
                Offset(
                    x = to.x.dp.toPx(),
                    y = (to.y.dp + columnCenterOffsetY(toColumnIndex)).toPx()
                )
            }

            // Фиксированный горизонтальный "хвостик" на выходе из from и на входе в to — независимо
            // от того, где на самом деле стоит вторая таблица (даже если она левее первой или сильно
            // смещена по Y). Между концами хвостиков — плавная S-кривая к цели.
            val stubOut = Offset(start.x + stubLenPx, start.y)
            val stubIn = Offset(end.x - stubLenPx, end.y)

            // Смещение контрольных точек считаем от БОЛЬШЕГО из dx/dy между хвостиками, а не только
            // от dx: если таблицы стоят близко по X, но далеко по Y, dx между хвостиками может
            // оказаться совсем маленьким (или даже отрицательным) — и кривая, опираясь только на
            // него, сгибалась почти в одной точке резким изломом вместо плавного разворота вниз.
            // Учитывая и dy, кривая всегда получает достаточный "разбег" перед поворотом к цели.
            val dx = stubIn.x - stubOut.x
            val dy = stubIn.y - stubOut.y
            val controlOffset = maxOf(abs(dx) * 0.6f, abs(dy) * 0.4f, ConnectorMinCurveOffset.toPx())
            val midControl1 = Offset(stubOut.x + controlOffset, stubOut.y)
            val midControl2 = Offset(stubIn.x - controlOffset, stubIn.y)

            val path = Path().apply {
                moveTo(start.x, start.y)
                lineTo(stubOut.x, stubOut.y)
                cubicTo(midControl1.x, midControl1.y, midControl2.x, midControl2.y, stubIn.x, stubIn.y)
                lineTo(end.x, end.y)
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = strokeWidthPx))

            drawCardinalityMarker(
                point = start,
                outward = Offset(1f, 0f),
                isMany = relationship.type.isManyAt(isFromEnd = true),
                color = lineColor,
                strokeWidthPx = strokeWidthPx
            )
            drawCardinalityMarker(
                point = end,
                outward = Offset(-1f, 0f),
                isMany = relationship.type.isManyAt(isFromEnd = false),
                color = lineColor,
                strokeWidthPx = strokeWidthPx
            )
        }
    }

    // Подпись вида связи показываем всегда (а не только если задан текст) — теперь это не
    // произвольная строка, а короткая метка перечисления RelationshipType. Тап по ней открывает
    // BottomSheet смены вида/удаления связи (см. onRelationshipBadgeClick), кроме как во время
    // самого связывания — тогда onRelationshipClick приходит null и подпись не реагирует на тап.
    relationships.forEach { relationship ->
        val from = tablesById[relationship.fromTableId] ?: return@forEach
        val to = tablesById[relationship.toTableId] ?: return@forEach
        val fromColumnIndex = from.columns.indexOfFirst { it.id == relationship.fromColumnId }
        val toColumnIndex = to.columns.indexOfFirst { it.id == relationship.toColumnId }
        if (fromColumnIndex == -1 || toColumnIndex == -1) return@forEach
        val fromWidth = tableWidths[from.id] ?: TableCardMinWidth
        val midX = (from.x + fromWidth.value + to.x) / 2f
        val midY = (from.y + columnCenterOffsetY(fromColumnIndex).value +
                to.y + columnCenterOffsetY(toColumnIndex).value) / 2f + 12f
        RelationLabel(
            text = relationship.type.shortLabel,
            onClick = onRelationshipClick?.let { onClick -> { onClick(relationship.id) } },
            modifier = Modifier.offset(x = midX.dp, y = midY.dp)
        )
    }
}

@Composable
private fun RelationLabel(text: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, KeyframeDBExtendedTheme.colors.accentSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun TableCard(
    table: TableEntity,
    width: Dp,
    accentColor: Color,
    isSelected: Boolean,
    isDragging: Boolean,
    isEditingAttributes: Boolean,
    isLinkSource: Boolean,
    // Идёт ли вообще режим связывания сейчас (не обязательно с источником внутри ЭТОЙ таблицы) —
    // от него зависит, тапабельны ли атрибуты карточки.
    isLinking: Boolean,
    // Тапабельны ли атрибуты именно этой таблицы прямо сейчас. Ложно только на шаге выбора
    // источника, когда он запущен из меню конкретной, ДРУГОЙ таблицы (см. isChoosingSource
    // в DiagramCanvas) — тогда карточка притушена и её атрибуты не реагируют на тап.
    isLinkableForColumnTap: Boolean,
    // Атрибут-источник связи, если он лежит именно в этой таблице — подсвечиваем его строку
    // отдельно от остальных, чтобы было видно, что уже выбрано.
    linkingSourceColumnId: String?,
    onTap: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDeleteColumn: (columnId: String) -> Unit,
    onColumnClick: (columnId: String) -> Unit,
    onColumnLinkTap: (columnId: String) -> Unit,
    onAddColumnClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardScale by animateFloatAsState(targetValue = if (isDragging) 1.06f else 1f, label = "tableScale")
    // Инлайн-редактирование (крестики на атрибутах + плюсик снизу) показываем только когда
    // пользователь явно включил его кнопкой "Редактировать" в плавающем меню —
    // не просто по факту выделения таблицы, и не во время перетаскивания.
    val showEditAffordances = isEditingAttributes && !isDragging
    // Строки атрибутов реагируют на тап для связывания только пока идёт само связывание,
    // эта таблица сейчас разрешена для выбора (см. isLinkableForColumnTap выше) и режим
    // редактирования атрибутов не включён параллельно — тогда приоритет остаётся за ним
    // (та же логика приоритета, что и раньше была у тапа по всей карточке).
    val allowColumnLinkTaps = isLinking && isLinkableForColumnTap && !showEditAffordances
    // Пока идёт связывание и эта таблица сейчас не выбираема (см. isLinkableForColumnTap) —
    // притушиваем всю карточку, чтобы сразу было видно, где сейчас можно тапнуть по атрибуту.
    val cardAlpha = if (isLinking && !isLinkableForColumnTap && !showEditAffordances) 0.45f else 1f

    // pointerInput ниже с ключом table.id живёт, пока карточка на экране, и не перезапускается
    // при каждой рекомпозиции — поэтому обычные лямбда-параметры внутри него могут "протухнуть"
    // и не увидеть новое поведение (например, переключение в режим связывания на лету).
    // rememberUpdatedState гарантирует, что жест всегда вызывает самую свежую версию колбэка.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    Column(
        modifier = modifier
            .offset(x = table.x.dp, y = table.y.dp)
            .alpha(cardAlpha)
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .scale(cardScale)
                .shadow(
                    elevation = when {
                        isDragging -> 10.dp
                        // Лёгкая тень, пока включён режим редактирования атрибутов или таблица —
                        // источник связи: сигнал пользователю, что с ней сейчас можно работать иначе.
                        isEditingAttributes || isLinkSource -> 4.dp
                        else -> 0.dp
                    },
                    shape = RoundedCornerShape(13.dp)
                )
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (isDragging || isEditingAttributes || isLinkSource) 1.5.dp else 1.dp,
                    color = when {
                        isDragging || isEditingAttributes -> MaterialTheme.colorScheme.primary
                        // Отдельный акцентный цвет для источника связи — чтобы не путать с обычным
                        // выделением/редактированием, пока пользователь выбирает вторую таблицу.
                        isLinkSource -> KeyframeDBExtendedTheme.colors.accent2
                        else -> MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(13.dp)
                )
                .pointerInput(table.id) {
                    // Отдельный детектор простого тапа — выделяет таблицу без запуска drag,
                    // чтобы плавающее меню могло появиться сразу по короткому нажатию.
                    detectTapGestures(onTap = { currentOnTap() })
                }
                .pointerInput(table.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { currentOnDragStart() },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragCancel() }
                    ) { change, dragAmount ->
                        change.consume()
                        with(density) {
                            currentOnDrag(dragAmount.x.toDp().value, dragAmount.y.toDp().value)
                        }
                    }
                }
        ) {
            // Раньше высота заголовка подстраивалась под контент (padding(vertical = 9.dp) +
            // естественная высота строки текста) и ПОЧТИ совпадала с номинальными
            // TableHeaderDots * DotGridStep (32.dp), которыми считает геометрию tableHeight/
            // columnCenterOffsetY ниже — пока связи упирались в центр всей карточки, разница в
            // несколько dp была незаметна. Теперь, когда линия идёт в конкретную строку
            // атрибута, та же разница в заголовке сдвигает буквально ВСЕ строки вниз на
            // одну и ту же величину — и чем ниже строка, тем заметнее итоговое расхождение
            // между нарисованной линией и реальной строкой (см. баг-репорт пользователя:
            // линия к последнему атрибуту визуально утыкалась в предыдущий). Фиксируем высоту
            // заголовка в точности равной тому, что уже закладывает геометрия — так же, как
            // PDF-экспорт (см. drawTableCard в PdfDiagramExporter.kt) всегда рисовал его ровно
            // в headerHeight = GridStepPt * TableHeaderDots, без подгона под контент.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DotGridStep * TableHeaderDots)
                    .background(accentColor),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = table.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // По той же причине (см. комментарий у заголовка выше) высота строки атрибута тоже
            // зафиксирована в DotGridStep * TableRowDots, а не подстраивается под контент —
            // иначе снова разъедется с tableHeight/columnCenterOffsetY. Разделитель между
            // строками раньше рисовался отдельным HorizontalDivider() — своей композицией со
            // своей высотой (пусть и в 1.dp, но она тоже накапливалась от строки к строке и
            // сдвигала геометрию). Теперь это нижняя линия самой строки (см. showDividerBelow
            // в FieldRow) — нулевой высоты, рисуется поверх уже выделенного под строку места.
            table.columns.forEachIndexed { index, column ->
                key(column.id) {
                    FieldRow(
                        column = column,
                        isEditable = showEditAffordances,
                        isLinkable = allowColumnLinkTaps,
                        isLinkSource = linkingSourceColumnId == column.id,
                        showDividerBelow = index != table.columns.lastIndex,
                        onClick = {
                            if (showEditAffordances) onColumnClick(column.id) else onColumnLinkTap(column.id)
                        },
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
    // Тапабельна ли строка для выбора атрибута в режиме связывания — независимо от isEditable,
    // который отвечает за режим редактирования атрибутов (кнопка "Редактировать" в меню).
    isLinkable: Boolean,
    // Этот атрибут уже выбран источником текущей создаваемой связи — подсвечиваем строку,
    // чтобы было видно, что именно от него сейчас тянется связь.
    isLinkSource: Boolean,
    // Рисовать ли тонкую линию-разделитель у нижнего края строки — true для всех строк,
    // кроме последней (см. комментарий у вызова FieldRow в TableCard про то, почему это
    // теперь часть самой строки, а не отдельный HorizontalDivider() между строками).
    showDividerBelow: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dividerColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DotGridStep * TableRowDots)
            .then(
                if (isLinkSource) {
                    Modifier.background(KeyframeDBExtendedTheme.colors.accent2Soft)
                } else {
                    Modifier
                }
            )
            .then(if (isEditable || isLinkable) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                if (showDividerBelow) {
                    Modifier.drawBehind {
                        drawLine(
                            color = dividerColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp),
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
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
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
            contentDescription = "Увеличить",
            onClick = onZoomInClick
        )
        ToolbarIconButton(
            icon = Icons.Default.ZoomOut,
            contentDescription = "Уменьшить",
            onClick = onZoomOutClick
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
    onConfirm: (newName: String) -> Unit,
    onColorChange: (TableColor) -> Unit
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

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Цвет таблицы",
                style = MaterialTheme.typography.bodySmall,
                color = KeyframeDBExtendedTheme.colors.inkMuted
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TableColor.values().forEach { option ->
                    ColorSwatch(
                        color = option.toComposeColor(),
                        isSelected = table.color == option,
                        onClick = { onColorChange(option) }
                    )
                }
            }

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

/** Кружок-образец акцентного цвета таблицы; выбранный отмечен галочкой поверх заливки. */
@Composable
private fun ColorSwatch(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Простая шторка переименования — только имя, без цвета (в отличие от таблицы, у схемы его нет). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameProjectSheet(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var draftName by remember(currentName) { mutableStateOf(currentName) }

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
            SheetHeader(icon = Icons.Default.Edit, title = "Название схемы", subtitle = "Текущее имя: $currentName")

            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                label = { Text("Название схемы") },
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

/** Короткая подпись конкретного атрибута для шапки RelationshipSheet — "таблица.атрибут". */
private fun attributeLabel(tableName: String, columnName: String): String =
    "$tableName.${columnName.ifBlank { "…" }}"

/**
 * Один BottomSheet на оба случая: создание новой связи (isNew = true, снизу кнопка "Создать
 * связь", закрытие без подтверждения просто отменяет её) и редактирование уже существующей
 * (isNew = false, вид связи меняется сразу по тапу на чип, снизу — кнопка удаления).
 * fromLabel/toLabel — уже готовые подписи конкретных атрибутов ("таблица.атрибут"), а не
 * просто имена таблиц: связь теперь строится между атрибутами, а не таблицами целиком.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationshipSheet(
    fromLabel: String,
    toLabel: String,
    type: RelationshipType,
    isNew: Boolean,
    onTypeChange: (RelationshipType) -> Unit,
    onDismiss: () -> Unit,
    onConfirmNew: () -> Unit = {},
    onDelete: () -> Unit = {}
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
            SheetHeader(
                icon = Icons.Default.Link,
                title = if (isNew) "Новая связь" else "Связь",
                subtitle = "$fromLabel → $toLabel"
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Вид связи",
                style = MaterialTheme.typography.bodySmall,
                color = KeyframeDBExtendedTheme.colors.inkMuted
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RelationshipType.values().forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { onTypeChange(option) },
                        label = { Text(option.shortLabel) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isNew) {
                Button(onClick = onConfirmNew, modifier = Modifier.fillMaxWidth()) {
                    Text("Создать связь")
                }
            } else {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Удалить связь")
                }
            }
        }
    }
}
