package com.arabicuieditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arabicuieditor.ui.navigation.AppNavHost
import com.arabicuieditor.ui.theme.ArabicUiEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArabicUiEditorTheme {
                AppNavHost()
            }
        }
    }
}
