package com.example.clientapp.ui.mainscreen

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    isStreaming: Boolean,
    onToggleStreaming: () -> Unit,
    onSwitchCamera: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.weight(1f)) {
            content()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onToggleStreaming) {
                Text(if (isStreaming) "Parar" else "Iniciar")
            }

            if (isStreaming) {
                Button(onClick = onSwitchCamera) {
                    Text("Inverter Câmera")
                }
            }
        }
    }
}