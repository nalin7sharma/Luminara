package com.luminara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.luminara.app.ui.LuminaraApp
import com.luminara.app.ui.theme.LuminaraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuminaraTheme {
                LuminaraApp()
            }
        }
    }
}
