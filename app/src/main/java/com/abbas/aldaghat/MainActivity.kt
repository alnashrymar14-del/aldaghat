package com.abbas.aldaghat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.abbas.aldaghat.ui.AlDaghatApp
import com.abbas.aldaghat.ui.theme.AlDaghatTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AlDaghatApplication).container
        setContent {
            AlDaghatTheme {
                // The app is Arabic-only, so the layout direction is RTL regardless of device locale.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AlDaghatApp(container = container)
                }
            }
        }
    }
}
