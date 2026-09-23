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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Searching : ConnectionState
    data class Connecting(val deviceName: String) : ConnectionState
    data class Connected(val deviceName: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

class YesoulBike(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _bikeData = MutableStateFlow(BikeData())
    val bikeData: StateFlow<BikeData> = _bikeData.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var scanTimeoutJob: Job? = null
    private var isScanning = false
    private var connectedDeviceName = DEVICE_NAME_PREFIX

    private val scanCallback = object : android.bluetooth.le.ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
            Log.d(TAG, "BLE device name=$name address=${result.device.address}")

            if (name?.startsWith(DEVICE_NAME_PREFIX, ignoreCase = true) == true) {
                stopScanning()
                connectedDeviceName = name
                _connectionState.value = ConnectionState.Connecting(name)
                connectToDevice(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
            stopScanning()
            _connectionState.value = ConnectionState.Error("Bluetooth scan failed ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "connection state status=$status newState=$newState")

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Connecting(connectedDeviceName)
                try {
                    if (!gatt.discoverServices()) {
                        failConnection("Could not start service discovery", gatt)
                    }
                } catch (securityException: SecurityException) {
                    failConnection("Bluetooth permission is required", gatt)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (gatt === bluetoothGatt) {
                    closeGatt(gatt)
                    bluetoothGatt = null
                }
                if (_connectionState.value !is ConnectionState.Error) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(gatt)
                _connectionState.value = ConnectionState.Error("Connection failed ($status)")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("Service discovery failed ($status)", gatt)
                return
            }

            Log.d(TAG, "discovered services=${gatt.services.map { it.uuid }}")
            val service = gatt.getService(FTMS_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(INDOOR_BIKE_DATA_UUID)
            if (service == null || characteristic == null) {
                failConnection("FTMS Indoor Bike Data characteristic not found", gatt)
                return
            }

            enableNotifications(gatt, characteristic)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleBikeData(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleBikeData(characteristic, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Connected(connectedDeviceName)
                Log.d(TAG, "Indoor Bike Data notifications enabled")
            } else {
                failConnection("Could not enable Indoor Bike Data notifications ($status)", gatt)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect() {
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

        _bikeData.value = BikeData()
        _connectionState.value = ConnectionState.Searching
        isScanning = true
        try {
            val filter = android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(FTMS_SERVICE_UUID))
                .build()
            val settings = android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(listOf(filter), settings, scanCallback)
            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(SCAN_TIMEOUT_MILLIS)
                if (_connectionState.value is ConnectionState.Searching) {
                    stopScanning()
                    _connectionState.value = ConnectionState.Error("YESOUL bike not found")
                }
            }
        } catch (securityException: SecurityException) {
            stopScanning()
            _connectionState.value = ConnectionState.Error("Bluetooth permission is required")
        }
    }

    fun close() {
        stopScanning()
        bluetoothGatt?.let(::closeGatt)
        bluetoothGatt = null
        scope.coroutineContext[Job]?.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        try {
            bluetoothGatt = device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
            if (bluetoothGatt == null) {
                _connectionState.value = ConnectionState.Error("Could not open a Bluetooth connection")
            }
        } catch (securityException: SecurityException) {
            _connectionState.value = ConnectionState.Error("Bluetooth permission is required")
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            failConnection("Could not enable Indoor Bike Data notifications", gatt)
            return
        }

        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            failConnection("Notification descriptor not found", gatt)
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
            failConnection("Could not write notification descriptor", gatt)
        }
    }

    private fun handleBikeData(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        if (characteristic.uuid != INDOOR_BIKE_DATA_UUID) return

        Log.d(TAG, "0x2AD2 packet=${value.toHexString()}")
        val parsedData = IndoorBikeDataParser.parse(value)
        if (parsedData == null) {
            Log.w(TAG, "Could not parse 0x2AD2 packet")
        } else {
            _bikeData.value = _bikeData.value.merge(parsedData)
            Log.d(TAG, "parsed BikeData=$parsedData merged=${_bikeData.value}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        if (!isScanning) return
        isScanning = false
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        runCatching { bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    @SuppressLint("MissingPermission")
    private fun failConnection(message: String, gatt: BluetoothGatt) {
        Log.e(TAG, message)
        closeGatt(gatt)
        if (gatt === bluetoothGatt) bluetoothGatt = null
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
        private const val TAG = "YesoulBike"
        private const val DEVICE_NAME_PREFIX = "YESOUL"
        private const val SCAN_TIMEOUT_MILLIS = 10_000L

        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val INDOOR_BIKE_DATA_UUID: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
