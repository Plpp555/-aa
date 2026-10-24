package com.aa.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Probe(val a: Int = 1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column {
                Icon(Icons.Rounded.Home, null)
                Text(Json.encodeToString(Probe.serializer(), Probe()))
            }
        }
    }
}
