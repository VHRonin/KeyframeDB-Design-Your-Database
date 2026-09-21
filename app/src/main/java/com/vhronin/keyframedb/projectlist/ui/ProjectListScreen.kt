package com.vhronin.keyframedb.projectlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vhronin.keyframedb.theme.KeyframeDBColors
import com.vhronin.keyframedb.projectlist.domain.model.Project
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ProjectListScreen(
    onNavigateToEditor: (String) -> Unit = {},
    viewModel: ProjectListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Список проектов — не живой поток (см. ProjectRepository.observeProjects), поэтому
    // перечитываем его при каждом появлении экрана: и при первом открытии, и при возврате
    // из редактора (попадание сюда снова — это повторный вход в композицию, эффект
    // перезапустится сам).
    LaunchedEffect(Unit) {
        viewModel.refreshProjects()
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is ProjectListNavigationEvent.OpenEditor -> onNavigateToEditor(event.projectId)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::onCreateProjectClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новая схема")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                Text(
                    text = "KeyframeDB",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${uiState.projects.size} схемы",
                    style = MaterialTheme.typography.bodySmall,
                    color = KeyframeDBColors.InkMuted
                )
            }

            SearchField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.visibleProjects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { viewModel.onProjectClick(project) },
                        onDeleteClick = { viewModel.onDeleteProjectClick(project) }
                    )
                }
            }
        }
    }

    uiState.pendingDeleteProject?.let { project ->
        DeleteProjectDialog(
            projectName = project.name,
            onConfirm = viewModel::onDeleteProjectConfirmed,
            onDismiss = viewModel::onDeleteProjectDismiss
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onPrimary)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = KeyframeDBColors.InkFaint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Поиск схемы",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KeyframeDBColors.InkFaint
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProjectThumbnail(tableCount = project.tableCount)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatUpdatedAtLabel(project.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = KeyframeDBColors.InkMuted
            )
        }

        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить схему",
                tint = KeyframeDBColors.InkFaint
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = KeyframeDBColors.InkFaint
        )
    }
}

@Composable
private fun DeleteProjectDialog(
    projectName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить схему?") },
        text = { Text("Схема «$projectName» будет удалена без возможности восстановления.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/**
 * "Изменено N минут/часов/дней назад" из timestamp последнего сохранения. Правильные русские
 * окончания важны — без них "2 часа" выглядело бы так же криво, как "5 часа".
 */
private fun formatUpdatedAtLabel(updatedAt: Long, now: Long = System.currentTimeMillis()): String {
    val diffMs = (now - updatedAt).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    val hours = diffMs / 3_600_000
    val days = diffMs / 86_400_000

    return when {
        minutes < 1 -> "Изменено только что"
        minutes < 60 -> "Изменено ${minutes} ${pluralForm(minutes, "минуту", "минуты", "минут")} назад"
        hours < 24 -> "Изменено ${hours} ${pluralForm(hours, "час", "часа", "часов")} назад"
        days == 1L -> "Изменено вчера"
        days < 7 -> "Изменено ${days} ${pluralForm(days, "день", "дня", "дней")} назад"
        else -> "Изменено ${SimpleDateFormat("d MMMM", Locale("ru")).format(Date(updatedAt))}"
    }
}

/** Русское склонение по числительному: 1 минута, 2 минуты, 5 минут, 11 минут, 21 минута... */
private fun pluralForm(count: Long, one: String, few: String, many: String): String {
    val mod100 = abs(count) % 100
    val mod10 = mod100 % 10
    return when {
        mod100 in 11L..14L -> many
        mod10 == 1L -> one
        mod10 in 2L..4L -> few
        else -> many
    }
}

@Composable
private fun ProjectThumbnail(tableCount: Int) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(KeyframeDBColors.AccentSoft)
    ) {
        val bars = tableCount.coerceIn(1, 3)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(bars) { index ->
                Box(
                    modifier = Modifier
                        .width(if (index == bars - 1) 20.dp else 28.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                )
            }
        }
    }
}
