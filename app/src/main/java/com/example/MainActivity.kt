package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AppSettings
import com.example.ui.AiCoderApp
import com.example.ui.theme.AiCoderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AiCoderApplication).container
        setContent {
            val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            AiCoderTheme(themeMode = settings.themeMode) {
                AiCoderApp(container = container)
            }
        }
    }
}
