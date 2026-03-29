package net.iovxw.alwaysontest

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.iovxw.alwaysontest.ui.theme.AlwaysontestTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val vpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startService(Intent(this, AlwaysOnVpnService::class.java))
            }
        }

        setContent {
            AlwaysontestTheme {
                val serviceRunning by AlwaysOnVpnService.serviceRunning.collectAsState()
                val tunnelEstablished by AlwaysOnVpnService.tunnelEstablished.collectAsState()
                val serviceAlwaysOn by AlwaysOnVpnService.alwaysOn.collectAsState()
                val serviceLockdown by AlwaysOnVpnService.lockdown.collectAsState()
                val startedBySystem by AlwaysOnVpnService.startedBySystem.collectAsState()

                val lifecycle = LocalLifecycleOwner.current.lifecycle

                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            AlwaysOnVpnService.refreshState()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(24.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.title),
                            style = MaterialTheme.typography.headlineMedium
                        )

                        StatusCard(stringResource(R.string.status_service_running), serviceRunning)
                        StatusCard(stringResource(R.string.status_tunnel_established), tunnelEstablished)
                        StatusCard(stringResource(R.string.status_started_by_system), startedBySystem)
                        StatusCard(stringResource(R.string.status_is_always_on), serviceAlwaysOn)
                        StatusCard(stringResource(R.string.status_is_lockdown), serviceLockdown)

                        HorizontalDivider()

                        val confirmedAlwaysOn = serviceRunning && serviceAlwaysOn
                        val likelyAlwaysOn = serviceRunning && startedBySystem
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    confirmedAlwaysOn -> MaterialTheme.colorScheme.tertiaryContainer
                                    likelyAlwaysOn -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.verdict_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        confirmedAlwaysOn ->
                                            stringResource(R.string.verdict_confirmed)
                                        likelyAlwaysOn && !tunnelEstablished ->
                                            stringResource(R.string.verdict_system_no_tunnel)
                                        likelyAlwaysOn ->
                                            stringResource(R.string.verdict_system_not_always_on)
                                        serviceRunning ->
                                            stringResource(R.string.verdict_running_not_system)
                                        else ->
                                            stringResource(R.string.verdict_not_running)
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Standalone: Start service + connect tunnel (above the split buttons)
                        Button(
                            onClick = {
                                AppLog.newGroup()
                                val prepareIntent = VpnService.prepare(this@MainActivity)
                                if (prepareIntent != null) {
                                    AppLog.d(TAG, "Need VPN permission, launching...")
                                    vpnPermissionLauncher.launch(prepareIntent)
                                } else {
                                    AppLog.d(TAG, "Starting service + connecting tunnel...")
                                    startService(Intent(this@MainActivity, AlwaysOnVpnService::class.java).apply {
                                        putExtra("auto_connect", true)
                                    })
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !serviceRunning
                        ) {
                            Text(stringResource(R.string.btn_start_and_connect))
                        }
                        Text(
                            stringResource(R.string.hint_tunnel_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Row 1: Service toggle + Tunnel toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    AppLog.newGroup()
                                    if (serviceRunning) {
                                        AppLog.d(TAG, "Stopping service...")
                                        AlwaysOnVpnService.killService()
                                    } else {
                                        val prepareIntent = VpnService.prepare(this@MainActivity)
                                        AppLog.d(TAG, "prepare() = $prepareIntent")
                                        if (prepareIntent != null) {
                                            vpnPermissionLauncher.launch(prepareIntent)
                                        } else {
                                            AppLog.d(TAG, "Starting service...")
                                            startService(Intent(this@MainActivity, AlwaysOnVpnService::class.java))
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (serviceRunning) stringResource(R.string.btn_stop_service) else stringResource(R.string.btn_start_service))
                            }
                            Button(
                                onClick = {
                                    AppLog.newGroup()
                                    if (tunnelEstablished) {
                                        AppLog.d(TAG, "Disconnecting tunnel...")
                                        AlwaysOnVpnService.disconnectTunnel()
                                    } else {
                                        AppLog.d(TAG, "Connecting tunnel...")
                                        AlwaysOnVpnService.connectTunnel()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = serviceRunning
                            ) {
                                Text(if (tunnelEstablished) stringResource(R.string.btn_disconnect_tunnel) else stringResource(R.string.btn_connect_tunnel))
                            }
                        }

                        // Row 2: Refresh + VPN settings
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    AppLog.newGroup()
                                    AppLog.d(TAG, "Manual refresh")
                                    AlwaysOnVpnService.refreshState()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.btn_refresh))
                            }
                            Button(
                                onClick = {
                                    startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.btn_vpn_settings))
                            }
                        }

                        HorizontalDivider()

                        val logGroups by AppLog.logGroups.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.log_title), style = MaterialTheme.typography.titleSmall)
                            Button(onClick = { AppLog.clear() }) {
                                Text(stringResource(R.string.log_clear))
                            }
                        }
                        if (logGroups.isEmpty()) {
                            Text(
                                stringResource(R.string.log_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            logGroups.asReversed().forEach { group ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            group.timestamp,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        group.lines.forEach { line ->
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(label: String, active: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
            Text(
                if (active) stringResource(R.string.status_yes) else stringResource(R.string.status_no),
                color = if (active)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}