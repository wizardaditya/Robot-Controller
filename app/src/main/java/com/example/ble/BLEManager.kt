package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

// Nordic UART Service standard UUIDs
val UART_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
val RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Write
val TX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // Notify

// Client Characteristic Configuration Descriptor UUID for enabling notifications
val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

enum class BLEStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class RoboDevice(val name: String, val address: String, val device: BluetoothDevice? = null)

data class RobotTelemetry(
    val batteryVoltage: Float = 12.0f,
    val batteryPercentage: Int = 100,
    val motorLeftRPM: Int = 0,
    val motorRightRPM: Int = 0,
    val temperatureCelsius: Float = 32.5f,
    val rssi: Int = -50,
    val activeCommand: String = "STOP",
    val speedScale: Int = 100,
    val botStatus: String = "Ready"
)

class BLEManager(private val context: Context) {
    private val TAG = "BLEManager"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val _status = MutableStateFlow(BLEStatus.DISCONNECTED)
    val status = _status.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage = _errorMessage.asStateFlow()

    private val _devices = MutableStateFlow<List<RoboDevice>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _telemetry = MutableStateFlow(RobotTelemetry())
    val telemetry = _telemetry.asStateFlow()

    private var scanCallback: ScanCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)
    private var simulationJob: Job? = null

    // Fallback simulation state for local testing/emulator
    private var isSimulated = true
    private var currentSpeed = 100

    init {
        // Start simulation updater to populate realistic dashboard measurements
        startSimulation()
    }

    private fun startSimulation() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            while (true) {
                delay(800)
                if (isSimulated) {
                    val currentTele = _telemetry.value
                    var targetLeft = 0
                    var targetRight = 0

                    when (currentTele.activeCommand) {
                        "FORWARD" -> {
                            targetLeft = (currentSpeed * 2.5 + Random.nextInt(-5, 5)).toInt()
                            targetRight = (currentSpeed * 2.5 + Random.nextInt(-5, 5)).toInt()
                        }
                        "BACKWARD" -> {
                            targetLeft = -(currentSpeed * 2.5 + Random.nextInt(-5, 5)).toInt()
                            targetRight = -(currentSpeed * 2.5 + Random.nextInt(-5, 5)).toInt()
                        }
                        "LEFT" -> {
                            targetLeft = -(currentSpeed * 1.5).toInt()
                            targetRight = (currentSpeed * 1.5).toInt()
                        }
                        "RIGHT" -> {
                            targetLeft = (currentSpeed * 1.5).toInt()
                            targetRight = -(currentSpeed * 1.5).toInt()
                        }
                        "FORWARD_LEFT" -> {
                            targetLeft = (currentSpeed * 1.0).toInt()
                            targetRight = (currentSpeed * 2.5).toInt()
                        }
                        "FORWARD_RIGHT" -> {
                            targetLeft = (currentSpeed * 2.5).toInt()
                            targetRight = (currentSpeed * 1.0).toInt()
                        }
                        "BACKWARD_LEFT" -> {
                            targetLeft = -(currentSpeed * 1.0).toInt()
                            targetRight = -(currentSpeed * 2.5).toInt()
                        }
                        "BACKWARD_RIGHT" -> {
                            targetLeft = -(currentSpeed * 2.5).toInt()
                            targetRight = -(currentSpeed * 1.0).toInt()
                        }
                        "KICK" -> {
                            targetLeft = 350
                            targetRight = 350
                        }
                        "STOP", "EMERGENCY_STOP" -> {
                            targetLeft = 0
                            targetRight = 0
                        }
                    }

                    // Simulated battery drain and variable motor RPM values
                    val batteryDrain = if (targetLeft != 0) 0.01f else 0.001f
                    val nextVolts = (currentTele.batteryVoltage - batteryDrain).coerceAtLeast(9.6f)
                    val percent = (((nextVolts - 9.6f) / (12.6f - 9.6f)) * 100).toInt().coerceIn(0, 100)

                    val finalLeft = (currentTele.motorLeftRPM * 0.6 + targetLeft * 0.4).toInt()
                    val finalRight = (currentTele.motorRightRPM * 0.6 + targetRight * 0.4).toInt()

                    _telemetry.value = currentTele.copy(
                        batteryVoltage = nextVolts,
                        batteryPercentage = percent,
                        motorLeftRPM = finalLeft,
                        motorRightRPM = finalRight,
                        temperatureCelsius = (30.0f + (currentSpeed.toFloat() / 150f) * 4.5f) + Random.nextFloat(),
                        rssi = if (_status.value == BLEStatus.CONNECTED) -55 + Random.nextInt(-10, 10) else -100,
                        speedScale = currentSpeed,
                        botStatus = if (currentTele.activeCommand == "EMERGENCY_STOP") "HALTED (E-STOP)" else if (_status.value == BLEStatus.CONNECTED) "Active (BLE)" else "Active (Simulated)"
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _status.value = BLEStatus.ERROR
            _errorMessage.value = "Bluetooth is disabled or unsupported"
            return
        }

        _devices.value = emptyList()
        _status.value = BLEStatus.SCANNING

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let {
                    try {
                        val device = it.device
                        val name = try {
                            device.name ?: "Unknown Robot"
                        } catch (e: SecurityException) {
                            "Unknown Robot"
                        }
                        val address = device.address

                        // Filter devices that match robo soccer bots
                        val lowerName = name.lowercase()
                        if (lowerName.contains("robo") || lowerName.contains("soccer") || lowerName.contains("bot") || lowerName.contains("esp32") || lowerName.contains("a5x")) {
                            val currentList = _devices.value
                            if (currentList.none { d -> d.address == address }) {
                                _devices.value = currentList + RoboDevice(name, address, device)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onScanResult", e)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _status.value = BLEStatus.ERROR
                _errorMessage.value = "BLE Scan failed: Code $errorCode"
            }
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(UART_SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothAdapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)
            // Stop scanning automatically after 10 seconds to conserve battery
            mainHandler.postDelayed({
                if (_status.value == BLEStatus.SCANNING) {
                    stopScan()
                }
            }, 10000)
        } catch (e: SecurityException) {
            _status.value = BLEStatus.ERROR
            _errorMessage.value = "Missing Bluetooth permissions: " + e.localizedMessage
        } catch (e: Exception) {
            _status.value = BLEStatus.ERROR
            _errorMessage.value = "Scanning startup error: " + e.localizedMessage
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (scanCallback != null && bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            try {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException stopping scan", e)
            }
            scanCallback = null
        }
        if (_status.value == BLEStatus.SCANNING) {
            _status.value = BLEStatus.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String) {
        stopScan()
        _status.value = BLEStatus.CONNECTING
        _errorMessage.value = ""

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            _status.value = BLEStatus.ERROR
            _errorMessage.value = "Failed to locate remote device"
            return
        }

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            _status.value = BLEStatus.ERROR
            _errorMessage.value = "Permission denied: " + e.localizedMessage
        } catch (e: Exception) {
            _status.value = BLEStatus.ERROR
            _errorMessage.value = "Connection failed: " + e.localizedMessage
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during disconnect", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during disconnect", e)
        }

        try {
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during close", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during close", e)
        }

        bluetoothGatt = null
        rxCharacteristic = null
        _status.value = BLEStatus.DISCONNECTED
        isSimulated = true // Fall back to interactive simulation
    }

    fun setSpeed(speed: Int) {
        currentSpeed = speed
        _telemetry.value = _telemetry.value.copy(speedScale = speed)
        sendCommand("V$speed")
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(cmd: String) {
        Log.d(TAG, "Sending Command: $cmd")
        
        // Extract base command and speed part from formatted string e.g. "FL:180"
        val baseCmd = if (cmd.contains(":")) cmd.split(":")[0] else cmd
        if (cmd.contains(":")) {
            val speedPart = cmd.split(":")[1].toIntOrNull()
            if (speedPart != null) {
                currentSpeed = speedPart
            }
        }

        // Update local telemetry command tracking
        val uiCmdMap = mapOf(
            "F" to "FORWARD",
            "B" to "BACKWARD",
            "L" to "LEFT",
            "R" to "RIGHT",
            "FL" to "FORWARD_LEFT",
            "FR" to "FORWARD_RIGHT",
            "BL" to "BACKWARD_LEFT",
            "BR" to "BACKWARD_RIGHT",
            "S" to "STOP",
            "K" to "KICK",
            "E" to "EMERGENCY_STOP"
        )
        val uiCmd = uiCmdMap[baseCmd] ?: baseCmd
        _telemetry.value = _telemetry.value.copy(activeCommand = uiCmd, speedScale = currentSpeed)

        if (_status.value == BLEStatus.CONNECTED && rxCharacteristic != null) {
            val bytes = cmd.toByteArray(Charsets.UTF_8)
            rxCharacteristic?.let { char ->
                char.value = bytes
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                try {
                    bluetoothGatt?.writeCharacteristic(char)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied writing characteristic", e)
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.")
                // Discover services
                try {
                    gatt?.discoverServices()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission error", e)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
                _status.value = BLEStatus.DISCONNECTED
                rxCharacteristic = null
                isSimulated = true
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(UART_SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
                    val txChar = service.getCharacteristic(TX_CHAR_UUID)

                    if (txChar != null) {
                        // Enable notifications for serial incoming telemetry data
                        try {
                            gatt.setCharacteristicNotification(txChar, true)
                            val descriptor = txChar.getDescriptor(CCCD_DESCRIPTOR_UUID)
                            if (descriptor != null) {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Permission denied set notifications", e)
                        }
                    }

                    _status.value = BLEStatus.CONNECTED
                    isSimulated = false // Stop client simulation, use real telemetry feeds
                } else {
                    Log.e(TAG, "UART Service not found in device attributes.")
                    _errorMessage.value = "Hardware mismatch: Missing custom UART services"
                    _status.value = BLEStatus.ERROR
                    disconnect()
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
                _status.value = BLEStatus.ERROR
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.let {
                if (it.uuid == TX_CHAR_UUID) {
                    val incoming = it.value?.toString(Charsets.UTF_8) ?: ""
                    parseIncomingTelemetry(incoming)
                }
            }
        }
    }

    private fun parseIncomingTelemetry(data: String) {
        // Expected format from ESP32: "V:<volts> M1:<rpm> M2:<rpm> T:<temp>"
        // e.g., "V:11.4 M1:180 M2:185 T:34.2"
        try {
            Log.d(TAG, "Incoming telemetry string: $data")
            var volts = _telemetry.value.batteryVoltage
            var mLeft = _telemetry.value.motorLeftRPM
            var mRight = _telemetry.value.motorRightRPM
            var temp = _telemetry.value.temperatureCelsius

            val tokens = data.split(" ")
            for (token in tokens) {
                if (token.startsWith("V:")) {
                    volts = token.substring(2).toFloatOrNull() ?: volts
                } else if (token.startsWith("M1:")) {
                    mLeft = token.substring(3).toIntOrNull() ?: mLeft
                } else if (token.startsWith("M2:")) {
                    mRight = token.substring(3).toIntOrNull() ?: mRight
                } else if (token.startsWith("T:")) {
                    temp = token.substring(2).toFloatOrNull() ?: temp
                }
            }

            val percent = (((volts - 9.6f) / (12.6f - 9.6f)) * 100).toInt().coerceIn(0, 100)

            _telemetry.value = _telemetry.value.copy(
                batteryVoltage = volts,
                batteryPercentage = percent,
                motorLeftRPM = mLeft,
                motorRightRPM = mRight,
                temperatureCelsius = temp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ", e)
        }
    }
}
