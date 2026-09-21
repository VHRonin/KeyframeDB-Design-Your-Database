package com.vhronin.keyframedb.diagrameditor.domain.model

/**
 * Диаграмма плюс момент её последнего сохранения — для мест, которым нужен список ВСЕХ
 * диаграмм разом (список проектов, генерация имени по умолчанию), а не одна конкретная
 * по id. `updatedAt` — метаданные хранилища (например, mtime файла), поэтому вынесены
 * из самой Diagram: если добавить это поле прямо в Diagram, оно тут же сломает сравнение
 * состояний в автосохранении редактора (см. lastPersistedSnapshot в DiagramEditorViewModel),
 * которое полагается на структурное equals() самой диаграммы.
 */
data class DiagramSummary(
    val diagram: Diagram,
    val updatedAt: Long
)
