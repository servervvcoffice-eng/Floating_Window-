package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.FloatXApp

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val fixedConfig = Configuration(newBase.resources.configuration).apply {
            fontScale = 1.0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                densityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE
            }
        }
        super.attachBaseContext(newBase.createConfigurationContext(fixedConfig))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloatXApp()
        }
    }
}
