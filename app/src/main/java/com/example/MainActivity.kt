package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ble.BLEManager
import com.example.ui.screens.RoboControllerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Suppress initial action bars, expand views behind system drawing bands
        enableEdgeToEdge()
        
        setContent {
            var isDarkMode by remember { mutableStateOf(false) } // Default light theme
            val bleManager = remember { BLEManager(applicationContext) }

            MyApplicationTheme(darkTheme = isDarkMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        RoboControllerScreen(
                            bleManager = bleManager,
                            isDarkMode = isDarkMode,
                            onToggleDark = { isDarkMode = !isDarkMode }
                        )
                    }
                }
            }
        }
    }
}


