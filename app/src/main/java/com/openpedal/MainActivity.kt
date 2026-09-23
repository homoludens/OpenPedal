package com.openpedal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.openpedal.ble.HeartRateMonitor
import com.openpedal.ble.YesoulBike
import com.openpedal.ui.BikeScreen
import com.openpedal.ui.OpenPedalTheme

class MainActivity : ComponentActivity() {
    private val yesoulBike by lazy { YesoulBike(applicationContext) }
    private val heartRateMonitor by lazy { HeartRateMonitor(applicationContext) }
    private var bluetoothPermissionsGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        bluetoothPermissionsGranted = hasBluetoothPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothPermissionsGranted = hasBluetoothPermissions()

        setContent {
            OpenPedalTheme {
                BikeScreen(
                    connectionState = yesoulBike.connectionState,
                    bikeData = yesoulBike.bikeData,
                    heartRateConnectionState = heartRateMonitor.connectionState,
                    heartRateData = heartRateMonitor.heartRateData,
                    heartRateDevices = heartRateMonitor.discoveredDevices,
                    bluetoothPermissionsGranted = bluetoothPermissionsGranted,
                    onConnect = {
                        if (hasBluetoothPermissions()) {
                            yesoulBike.connect()
                        } else {
                            requestBluetoothPermissions()
                        }
                    },
                    onRequestBluetoothPermissions = ::requestBluetoothPermissions,
                    onConnectHeartRate = {
                        if (hasBluetoothPermissions()) {
                            heartRateMonitor.scanForDevices()
                        } else {
                            requestBluetoothPermissions()
                        }
                    },
                    onCancelHeartRateScan = heartRateMonitor::cancelScan,
                    onHeartRateDeviceSelected = heartRateMonitor::connect,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bluetoothPermissionsGranted = hasBluetoothPermissions()
    }

    override fun onDestroy() {
        yesoulBike.close()
        heartRateMonitor.close()
        super.onDestroy()
    }

    private fun requestBluetoothPermissions() {
        permissionLauncher.launch(requiredBluetoothPermissions())
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return requiredBluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
