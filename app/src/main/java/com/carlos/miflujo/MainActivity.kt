package com.carlos.miflujo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.carlos.miflujo.ui.MiFlujoApp
import com.carlos.miflujo.ui.theme.MiFlujoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiFlujoTheme {
                MiFlujoApp()
            }
        }
    }
}
