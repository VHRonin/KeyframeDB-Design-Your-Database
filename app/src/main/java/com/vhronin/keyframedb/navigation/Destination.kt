package com.vhronin.keyframedb.navigation

sealed class Destination(val name: String) {
    data object ProjectList : Destination("project_list")
    data object DiagramEditor : Destination("diagram_editor/{projectId}") {
        fun createRoute(projectId: String) = "diagram_editor/$projectId"
    }
}