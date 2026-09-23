package com.openpedal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openpedal.ble.BikeData
import com.openpedal.ble.ConnectionState
import com.openpedal.ble.HeartRateData
import com.openpedal.ble.HeartRateDevice
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@Composable
fun BikeScreen(
    connectionState: StateFlow<ConnectionState>,
    bikeData: StateFlow<BikeData>,
    heartRateConnectionState: StateFlow<ConnectionState>,
    heartRateData: StateFlow<HeartRateData>,
    heartRateDevices: StateFlow<List<HeartRateDevice>>,
    bluetoothPermissionsGranted: Boolean,
    onConnect: () -> Unit,
    onRequestBluetoothPermissions: () -> Unit,
    onConnectHeartRate: () -> Unit,
    onCancelHeartRateScan: () -> Unit,
    onHeartRateDeviceSelected: (HeartRateDevice) -> Unit,
) {
    val state by connectionState.collectAsState()
    val data by bikeData.collectAsState()
    val heartRateState by heartRateConnectionState.collectAsState()
    val heartRate by heartRateData.collectAsState()
    val heartRateDeviceList by heartRateDevices.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(state)
            ConnectionAction(
                state = state,
                permissionsGranted = bluetoothPermissionsGranted,
                onConnect = onConnect,
                onRequestBluetoothPermissions = onRequestBluetoothPermissions,
            )
            Dashboard(
                data = data,
                heartRateState = heartRateState,
                heartRate = heartRate,
                heartRateDevices = heartRateDeviceList,
                bluetoothPermissionsGranted = bluetoothPermissionsGranted,
                onConnectHeartRate = onConnectHeartRate,
                onCancelHeartRateScan = onCancelHeartRateScan,
                onHeartRateDeviceSelected = onHeartRateDeviceSelected,
            )
        }
    }
}

@Composable
private fun Header(state: ConnectionState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "YESOUL Bike",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Indoor cycling data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusLabel(state)
    }
}

@Composable
private fun StatusLabel(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Disconnected -> "Disconnected" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.Searching -> "Searching" to MaterialTheme.colorScheme.primary
        is ConnectionState.Connecting -> "Connecting" to MaterialTheme.colorScheme.primary
        is ConnectionState.Connected -> "Connected" to MaterialTheme.colorScheme.primary
        is ConnectionState.Error -> "Error" to MaterialTheme.colorScheme.error
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun ConnectionAction(
    state: ConnectionState,
    permissionsGranted: Boolean,
    onConnect: () -> Unit,
    onRequestBluetoothPermissions: () -> Unit,
) {
    when {
        !permissionsGranted -> {
            ActionCard(
                message = "Bluetooth access is needed to find your bike.",
                buttonLabel = "ALLOW BLUETOOTH",
                onClick = onRequestBluetoothPermissions,
            )
        }

        state is ConnectionState.Searching || state is ConnectionState.Connecting -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (state is ConnectionState.Searching) {
                        "Searching for bike..."
                    } else {
                        "Connecting to bike..."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state is ConnectionState.Connected -> Unit

        else -> {
            val errorMessage = (state as? ConnectionState.Error)?.message
            ActionCard(
                message = errorMessage,
                buttonLabel = "CONNECT",
                onClick = onConnect,
            )
        }
    }
}

@Composable
private fun ActionCard(
    message: String?,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(12.dp))
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun Dashboard(
    data: BikeData,
    heartRateState: ConnectionState,
    heartRate: HeartRateData,
    heartRateDevices: List<HeartRateDevice>,
    bluetoothPermissionsGranted: Boolean,
    onConnectHeartRate: () -> Unit,
    onCancelHeartRateScan: () -> Unit,
    onHeartRateDeviceSelected: (HeartRateDevice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatInt(data.powerWatts, "W"),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "POWER",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                value = formatDecimal(data.cadenceRpm, 0),
                unit = "rpm",
                label = "CADENCE",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                value = formatDecimal(data.speedKmh, 1),
                unit = "km/h",
                label = "SPEED",
            )
        }

        HeartRateSection(
            state = heartRateState,
            data = heartRate,
            devices = heartRateDevices,
            bluetoothPermissionsGranted = bluetoothPermissionsGranted,
            onConnect = onConnectHeartRate,
            onCancelScan = onCancelHeartRateScan,
            onDeviceSelected = onHeartRateDeviceSelected,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                value = formatDistance(data.distanceMeters),
                unit = "km",
                label = "DISTANCE",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                value = formatDecimal(data.resistance, 1),
                unit = "",
                label = "RESISTANCE",
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                value = formatInt(data.calories, "kcal"),
                unit = "",
                label = "CALORIES",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                value = formatInt(data.averagePowerWatts, "W"),
                unit = "",
                label = "AVERAGE POWER",
            )
        }

        MetricCard(
            modifier = Modifier.fillMaxWidth(),
            value = formatTime(data.elapsedSeconds),
            unit = "",
            label = "TIME",
        )
    }
}

@Composable
private fun HeartRateSection(
    state: ConnectionState,
    data: HeartRateData,
    devices: List<HeartRateDevice>,
    bluetoothPermissionsGranted: Boolean,
    onConnect: () -> Unit,
    onCancelScan: () -> Unit,
    onDeviceSelected: (HeartRateDevice) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Heart rate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state is ConnectionState.Connected) {
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = data.bpm?.toString() ?: "--",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "BPM",
                    modifier = Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (state) {
                ConnectionState.Searching -> {
                    Text(
                        text = "Heart rate devices",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (devices.isEmpty()) {
                        Text(
                            text = "Searching for heart rate monitors...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        devices.forEach { device ->
                            HeartRateDeviceRow(
                                device = device,
                                onClick = { onDeviceSelected(device) },
                            )
                        }
                    }
                    TextButton(onClick = onCancelScan) {
                        Text("CANCEL")
                    }
                }

                is ConnectionState.Connecting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Connecting to ${state.deviceName}...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is ConnectionState.Connected -> {
                    Text(
                        text = state.deviceName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    val errorMessage = (state as? ConnectionState.Error)?.message
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(onClick = onConnect) {
                        Text(
                            if (bluetoothPermissionsGranted) {
                                "CONNECT HR"
                            } else {
                                "ALLOW BLUETOOTH"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartRateDeviceRow(
    device: HeartRateDevice,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    value: String,
    unit: String,
    label: String,
) {
    Card(
        modifier = modifier.height(92.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (unit.isNotEmpty() && value != "--") {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }
    }
}

private fun formatDecimal(value: Double?, decimals: Int): String {
    return value?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: "--"
}

private fun formatInt(value: Int?, unit: String): String {
    return value?.let { if (unit.isEmpty()) it.toString() else "$it $unit" } ?: "--"
}

private fun formatDistance(meters: Int?): String {
    return meters?.let { String.format(Locale.US, "%.2f", it / 1000.0) } ?: "--"
}

private fun formatTime(seconds: Int?): String {
    return seconds?.let {
        val hours = it / 3600
        val minutes = (it % 3600) / 60
        val remainingSeconds = it % 60
        if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
        }
    } ?: "--"
}
