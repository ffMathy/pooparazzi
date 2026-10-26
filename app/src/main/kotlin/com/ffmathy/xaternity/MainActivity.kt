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
import com.ffmathy.xaternity.core.sample.SampleData

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpikeScreen() }
    }
}

@Composable
fun SpikeScreen() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) { Text(SampleData.BABY_NAME, style = MaterialTheme.typography.displayMedium) }
    }
}
