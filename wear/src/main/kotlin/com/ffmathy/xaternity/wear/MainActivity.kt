package com.ffmathy.xaternity.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearSpike() }
    }
}

@Composable
fun WearSpike() {
    MaterialTheme {
        AppScaffold(timeText = { TimeText() }) {
            ScreenScaffold {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Watch") }
            }
        }
    }
}
