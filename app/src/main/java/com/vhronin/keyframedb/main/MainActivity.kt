package com.vhronin.keyframedb.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.vhronin.keyframedb.diagrameditor.ui.DiagramEditorScreen
import com.vhronin.keyframedb.navigation.KeyframeDBNavHost
import com.vhronin.keyframedb.projectlist.ui.ProjectListScreen
import com.vhronin.keyframedb.theme.KeyframeDBTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeyframeDBTheme {
                val navController = rememberNavController()
                KeyframeDBNavHost(navController)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MyPreview() {
    KeyframeDBTheme {
        DiagramEditorScreen(projectId = "preview")
    }
}