package dev.rizukirr.audx.samples.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
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

        if (recording) {
            LinearProgressIndicator(
                progress = { vm.vadProbability },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Speaking (VAD %.2f)".format(vm.vadProbability))
                Box(
                    Modifier
                        .size(16.dp)
                        .background(
                            color = if (vm.speaking) Color(0xFF2E7D32) else Color(0xFFBDBDBD),
                            shape = CircleShape,
                        )
                )
            }
        }

        // Playback is disabled while recording (state is not Ready then);
        // each button toggles between Play and Stop for its own track.
        val ready = vm.state is UiState.Ready
        Button(onClick = vm::togglePlayRaw, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.playing == Track.RAW) "Stop Raw" else "Play Raw")
        }
        Button(onClick = vm::togglePlayDenoised, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.playing == Track.DENOISED) "Stop Denoised" else "Play Denoised")
        }
        Button(onClick = vm::uploadRaw, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Upload Raw to Server")
        }

        Text(vm.status)
    }
}
