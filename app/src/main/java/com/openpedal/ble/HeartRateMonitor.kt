package com.openpedal.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class HeartRateDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
)

class HeartRateMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _heartRateData = MutableStateFlow(HeartRateData())
    val heartRateData: StateFlow<HeartRateData> = _heartRateData.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<HeartRateDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<HeartRateDevice>> = _discoveredDevices.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDeviceName = "Heart rate monitor"
    private var isScanning = false

    private val scanCallback = object : android.bluetooth.le.ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            val name = result.scanRecord?.deviceName
                ?: runCatching { result.device.name }.getOrNull()
                ?: "Unknown heart rate device"
            val address = result.device.address
            Log.d(TAG, "discovered HR device name=$name address=$address")

            if (_discoveredDevices.value.none { it.address == address }) {
                _discoveredDevices.value += HeartRateDevice(
                    device = result.device,
                    name = name,
                    address = address,
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "HR scan failed: $errorCode")
            stopScanning()
            _connectionState.value = ConnectionState.Error("Heart rate scan failed ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "HR connection state status=$status newState=$newState")

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Connecting(connectedDeviceName)
                try {
                    if (!gatt.discoverServices()) {
                        failConnection("Could not start HR service discovery", gatt)
                    }
                } catch (securityException: SecurityException) {
                    failConnection("Bluetooth permission is required", gatt)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (gatt === bluetoothGatt) {
                    closeGatt(gatt)
                    bluetoothGatt = null
                }
                _heartRateData.value = HeartRateData()
                if (_connectionState.value !is ConnectionState.Error) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(gatt)
                _heartRateData.value = HeartRateData()
                _connectionState.value = ConnectionState.Error("HR connection failed ($status)")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("HR service discovery failed ($status)", gatt)
                return
            }

            Log.d(TAG, "discovered HR services=${gatt.services.map { it.uuid }}")
            val service = gatt.getService(HEART_RATE_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (service == null || characteristic == null) {
                failConnection("Heart Rate Measurement characteristic not found", gatt)
                return
            }

            enableNotifications(gatt, characteristic)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleHeartRate(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleHeartRate(characteristic, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Connected(connectedDeviceName)
                Log.d(TAG, "Heart Rate Measurement notifications enabled")
            } else {
                failConnection("Could not enable HR notifications ($status)", gatt)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun scanForDevices() {
        if (_connectionState.value is ConnectionState.Searching ||
            _connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected
        ) {
            return
        }

        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            _connectionState.value = ConnectionState.Error("Bluetooth is not available")
            return
        }
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Turn on Bluetooth and try again")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = ConnectionState.Error("Bluetooth scanning is not available")
            return
        }

        _heartRateData.value = HeartRateData()
        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.Searching
        isScanning = true
        try {
            val filter = android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build()
            val settings = android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (securityException: SecurityException) {
            stopScanning()
            _connectionState.value = ConnectionState.Error("Bluetooth permission is required")
        }
    }

    fun cancelScan() {
        stopScanning()
        if (_connectionState.value is ConnectionState.Searching) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: HeartRateDevice) {
        stopScanning()
        bluetoothGatt?.let(::closeGatt)
        bluetoothGatt = null
        connectedDeviceName = device.name
        _connectionState.value = ConnectionState.Connecting(device.name)

        try {
            bluetoothGatt = device.device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
            if (bluetoothGatt == null) {
                _connectionState.value = ConnectionState.Error("Could not open the HR connection")
            }
        } catch (securityException: SecurityException) {
            _connectionState.value = ConnectionState.Error("Bluetooth permission is required")
        }
    }

    fun close() {
        stopScanning()
        bluetoothGatt?.let(::closeGatt)
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            failConnection("Could not enable HR notifications", gatt)
            return
        }

        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            failConnection("HR notification descriptor not found", gatt)
            return
        }

        val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
        if (!writeStarted) {
            failConnection("Could not write HR notification descriptor", gatt)
        }
    }

    private fun handleHeartRate(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        if (characteristic.uuid != HEART_RATE_MEASUREMENT_UUID) return

        Log.d(TAG, "0x2A37 packet=${value.toHexString()}")
        val parsedData = HeartRateParser.parse(value)
        if (parsedData == null) {
            Log.w(TAG, "Could not parse 0x2A37 packet")
        } else {
            _heartRateData.value = parsedData
            Log.d(TAG, "parsed BPM=${parsedData.bpm}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        if (!isScanning) return
        isScanning = false
        runCatching { bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    @SuppressLint("MissingPermission")
    private fun failConnection(message: String, gatt: BluetoothGatt) {
        Log.e(TAG, message)
        closeGatt(gatt)
        if (gatt === bluetoothGatt) bluetoothGatt = null
        _heartRateData.value = HeartRateData()
        _connectionState.value = ConnectionState.Error(message)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt) {
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = " ") {
        "%02X".format(it.toInt() and 0xff)
    }

    companion object {
        private const val TAG = "HeartRateMonitor"

        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
