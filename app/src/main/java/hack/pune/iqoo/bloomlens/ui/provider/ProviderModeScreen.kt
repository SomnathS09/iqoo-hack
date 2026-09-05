package hack.pune.iqoo.bloomlens.ui.provider

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hack.pune.iqoo.bloomlens.provider.NetworkUtils
import hack.pune.iqoo.bloomlens.provider.ProviderServerService
import hack.pune.iqoo.bloomlens.provider.ProviderStatus
import hack.pune.iqoo.bloomlens.ui.theme.HintGreen
import hack.pune.iqoo.bloomlens.ui.theme.HintPurple
import kotlinx.coroutines.delay

/**
 * Lets the teacher turn this phone into a shared tutor for nearby students: starts/stops
 * [ProviderServerService] and shows the address students should open in their browser.
 */
@Composable
fun ProviderModeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val isRunning by ProviderStatus.isRunning.collectAsState()
    val sessionCount by ProviderStatus.sessionCount.collectAsState()
    var addresses by remember { mutableStateOf(NetworkUtils.localIpv4Addresses()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(isRunning) {
        while (isRunning) {
            addresses = NetworkUtils.localIpv4Addresses()
            delay(3000)
        }
    }

    fun startServing() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ProviderServerService.start(context)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                IconButton(onClick = onClose) { Text("✕") }
                Text(
                    "📡 Provider Mode",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    "Turn this phone into a shared tutor for nearby students - no internet needed, just this phone's Wi-Fi hotspot.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Setup steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        listOf(
                            "1. Turn on this phone's Mobile Hotspot (Settings > Network > Hotspot).",
                            "2. Tap \"Start Serving\" below.",
                            "3. On each student's phone, join this phone's hotspot Wi-Fi.",
                            "4. In their browser, open the address shown below.",
                        ).forEach {
                            Text(it, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Button(
                    onClick = { if (isRunning) ProviderServerService.stop(context) else startServing() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else HintGreen,
                    ),
                ) {
                    Text(if (isRunning) "Stop Serving" else "Start Serving")
                }

                if (isRunning) {
                    Text(
                        "Serving on:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    if (addresses.isEmpty()) {
                        Text(
                            "No Wi-Fi address detected yet - make sure the hotspot is turned on.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        addresses.forEach { addr ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                SelectionContainer {
                                    Text(
                                        "http://${addr.address}:${ProviderStatus.port.value}/",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HintPurple,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🟢", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "$sessionCount student session(s) active right now",
                            color = HintGreen,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
