package com.example.turbocachecleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.turbocachecleaner.data.Prefs
import com.example.turbocachecleaner.ui.TurboCacheApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the UI can re-check permissions and refresh numbers. */
    private val resumeTick = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        setContent {
            TurboCacheApp(prefs = prefs, resumeTick = resumeTick)
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.value += 1L
    }
}
