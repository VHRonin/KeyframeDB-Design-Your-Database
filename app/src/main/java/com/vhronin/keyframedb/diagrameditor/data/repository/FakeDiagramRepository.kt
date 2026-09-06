package com.vhronin.keyframedb.diagrameditor.data.repository

import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import com.vhronin.keyframedb.diagrameditor.domain.model.Relationship
import com.vhronin.keyframedb.diagrameditor.domain.model.TableColumn
import com.vhronin.keyframedb.diagrameditor.domain.model.TableEntity
import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Временная реализация в памяти — заглушка на этапе, пока не готов
 * data-слой с реальным хранением (JSON-файл диаграммы на диске).
 * projectId пока не используется — всегда отдаём один и тот же набор данных,
 * совпадающий с HTML-мокапом экрана редактора.
 */
class FakeDiagramRepository : DiagramRepository {

    private val diagram = MutableStateFlow(
        Diagram(
            projectId = "1",
            projectName = "e-commerce",
            tables = listOf(
                TableEntity(
                    id = "users",
                    name = "users",
                    x = 14f,
                    y = 18f,
                    columns = listOf(
                        TableColumn(id = "1", name = "id", type = "uuid", isPrimaryKey = true),
                        TableColumn(id = "2", name = "email", type = "varchar"),
                        TableColumn(id = "3", name = "created_at", type = "timestamp")
                    )
                ),
                TableEntity(
                    id = "orders",
                    name = "orders",
                    x = 96f,
                    y = 172f,
                    columns = listOf(
                        TableColumn(id = "1", name = "id", type = "uuid", isPrimaryKey = true),
                        TableColumn(id = "2", name = "user_id", type = "uuid", isForeignKey = true),
                        TableColumn(id = "3", name = "total", type = "decimal")
                    )
                )
            ),
            relationships = listOf(
                Relationship(id = "r1", fromTableId = "users", toTableId = "orders", label = "1 – N")
            )
        )
    )

    override fun observeDiagram(projectId: String): Flow<Diagram> = diagram
}
