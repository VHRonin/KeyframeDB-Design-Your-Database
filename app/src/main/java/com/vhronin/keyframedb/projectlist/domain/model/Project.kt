package com.vhronin.keyframedb.projectlist.domain.model

data class Project(
    val id: String,
    val name: String,
    val updatedAt: Long, // время последнего сохранения (epoch millis); человекочитаемую метку
    // вроде "2 часа назад" строит presentation-слой (см. formatUpdatedAtLabel в ProjectListScreen)
    val tableCount: Int = 0
)