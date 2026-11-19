package com.memesoundboardx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.memesoundboardx.ui.navigation.AppRoot
import com.memesoundboardx.ui.theme.MemeSoundboardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemeSoundboardTheme {
                AppRoot()
            }
        }
    }
}
