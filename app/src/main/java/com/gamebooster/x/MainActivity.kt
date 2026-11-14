package com.gamebooster.x

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gamebooster.x.ui.dashboard.DashboardRoute
import com.gamebooster.x.ui.theme.GameBoosterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameBoosterTheme {
                DashboardRoute()
            }
        }
    }
}
