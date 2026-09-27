package com.parvez.booker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.parvez.booker.ui.screen.BookerLibraryScreen
import com.parvez.booker.ui.theme.BookerTheme

/**
 * Main Activity entry point for Booker app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BookerTheme {
                BookerLibraryScreen()
            }
        }
    }
}