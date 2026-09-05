package com.vhronin.keyframedb.projectlist.domain.model

data class Project(
    val id: String,
    val name: String,
    val updatedAtLabel: String, // человекочитаемая метка вроде "2 часа назад";
    // позже заменить на реальный timestamp + форматирование в presentation-слое
    val tableCount: Int = 0
)