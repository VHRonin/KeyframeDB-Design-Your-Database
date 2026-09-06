package com.vhronin.keyframedb.navigation

sealed class Destination(val name: String) {
    data object ProjectList : Destination("project_list")
    data object DiagramEditor : Destination("diagram_editor?projectId={projectId}") {
        fun createRoute(projectId: String? = null) =
            if (projectId != null) "diagram_editor?projectId=$projectId" else "diagram_editor"
    }
}