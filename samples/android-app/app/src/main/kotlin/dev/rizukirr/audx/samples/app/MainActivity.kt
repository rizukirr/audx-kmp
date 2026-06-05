package dev.rizukirr.audx.samples.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }
}

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startRecording() else vm.permissionDenied()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = vm.serverUrl,
            onValueChange = { vm.serverUrl = it },
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val recording = vm.state == UiState.Recording
        Button(
            onClick = {
                when {
                    recording -> vm.stopRecording()
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED -> vm.startRecording()
                    else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (recording) "Stop" else "Record")
        }

        val ready = vm.state is UiState.Ready
        Button(onClick = vm::playRaw, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Play Raw")
        }
        Button(onClick = vm::playDenoised, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Play Denoised")
        }
        Button(onClick = vm::uploadRaw, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Upload Raw to Server")
        }

        Text(vm.status)
    }
}
