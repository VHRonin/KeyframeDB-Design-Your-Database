package com.vhronin.keyframedb.diagrameditor.data.repository

import android.content.Context
import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Хранит каждую диаграмму в своём JSON-файле во внутреннем хранилище приложения
 * (filesDir/diagrams/<projectId>.json).
 *
 * Полноценная БД (Room) здесь избыточна: нет ни запросов, ни связей между записями —
 * экран редактора всегда загружает и сохраняет один документ (Diagram) целиком, а его
 * структура (списки таблиц/атрибутов/связей) ещё может меняться по ходу разработки —
 * без миграций схемы это проще. Это и есть то хранилище, что и планировалось изначально
 * (см. прежние комментарии в DiagramEditorModule и удалённом FakeDiagramRepository).
 *
 * observeDiagram отдаёт Flow, backed StateFlow-кэшем в памяти на projectId — чтобы после
 * первого чтения с диска повторные обращения (и обновления после save) не дёргали файловую
 * систему заново.
 */
class JsonDiagramRepository(
    private val context: Context
) : DiagramRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // Один мьютекс на все записи, а не по одному на projectId — записи и так редкие
    // (см. debounce в DiagramEditorViewModel), сериализовать их между собой не жалко,
    // а код при этом проще.
    private val writeMutex = Mutex()
    private val cache = ConcurrentHashMap<String, MutableStateFlow<Diagram>>()

    private val diagramsDir: File by lazy {
        File(context.filesDir, "diagrams").apply { mkdirs() }
    }

    override fun observeDiagram(projectId: String): Flow<Diagram> = flow {
        emitAll(cacheFlowFor(projectId))
    }

    override suspend fun saveDiagram(diagram: Diagram) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val file = fileFor(diagram.projectId)
                // Пишем во временный файл и переименовываем поверх целевого — если приложение
                // убьют посреди записи, останется либо старая версия, либо новая, но не
                // повреждённый наполовину файл.
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                tempFile.writeText(json.encodeToString(diagram))
                tempFile.renameTo(file)
            }
        }
        cacheFlowFor(diagram.projectId).value = diagram
    }

    private suspend fun cacheFlowFor(projectId: String): MutableStateFlow<Diagram> {
        cache[projectId]?.let { return it }
        val loaded = withContext(Dispatchers.IO) { readFromDisk(projectId) }
        // computeIfAbsent — на случай, если другой вызов успел создать запись первым, пока
        // этот читал файл с диска: тогда используем уже существующий StateFlow, не свой.
        return cache.computeIfAbsent(projectId) { MutableStateFlow(loaded) }
    }

    private fun readFromDisk(projectId: String): Diagram {
        val file = fileFor(projectId)
        if (!file.exists()) return Diagram(projectId = projectId, projectName = "")
        return runCatching { json.decodeFromString<Diagram>(file.readText()) }
            .getOrElse { Diagram(projectId = projectId, projectName = "") }
    }

    private fun fileFor(projectId: String): File = File(diagramsDir, "$projectId.json")
}
