package com.ffmathy.xaternity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ffmathy.xaternity.core.spikeGreeting

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpikeScreen() }
    }
}

@Composable
fun SpikeScreen() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) { Text("Phone ${spikeGreeting()}", style = MaterialTheme.typography.displayMedium) }
    }
}
