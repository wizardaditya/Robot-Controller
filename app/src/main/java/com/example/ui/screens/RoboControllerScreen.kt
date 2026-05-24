package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.ble.BLEManager
import com.example.ble.BLEStatus
import com.example.ble.RoboDevice
import com.example.ble.RobotTelemetry
import com.example.ui.components.A5XLogo
import com.example.ui.components.JoystickDirection
import com.example.ui.components.RoboJoystick
import com.example.ui.components.TelemetryDashboard

@SuppressLint("InlinedApi")
@Composable
fun RoboControllerScreen(
    bleManager: BLEManager,
    isDarkMode: Boolean,
    onToggleDark: () -> Unit
) {
    val context = LocalContext.current
    val bleStatus by bleManager.status.collectAsState()
    val telemetry by bleManager.telemetry.collectAsState()
    val discoveredDevices by bleManager.devices.collectAsState()
    
    // Core custom interactive configurations for high density design
    var showScanDialog by remember { mutableStateOf(false) }
    var highContrastMode by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var driveControlMode by remember { mutableStateOf("joystick") } // "joystick" or "tactile_grid"
    var sliderSpeed by remember { mutableStateOf(180f) }
    val logHistory = remember { mutableStateListOf<String>() }

    // Unified command launcher piping to localized CLI system logs
    val sendBluetoothCommand: (String) -> Unit = { rawCmd ->
        val fullCmd = if (rawCmd == "F" || rawCmd == "B" || rawCmd == "L" || rawCmd == "R" ||
                         rawCmd == "FL" || rawCmd == "FR" || rawCmd == "BL" || rawCmd == "BR") {
            "$rawCmd:${sliderSpeed.toInt()}"
        } else {
            rawCmd
        }
        bleManager.sendCommand(fullCmd)
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.S", java.util.Locale.US).format(java.util.Date())
        logHistory.add(0, "[$timestamp] Transmitted Command: \"$fullCmd\"")
        if (logHistory.size > 80) {
            logHistory.removeLastOrNull()
        }
    }

    // Modern cyber high-contrast aesthetic colors
    val contrastBgColor = if (highContrastMode) {
        if (isDarkMode) Color.Black else Color.White
    } else {
        if (isDarkMode) Color(0xFF0B0F19) else Color(0xFFF8FAFC)
    }

    val contrastCardColor = if (highContrastMode) {
        if (isDarkMode) Color(0xFF111827) else Color.White
    } else {
        if (isDarkMode) Color(0xFF1E293B) else Color.White
    }

    @Composable
    fun borderStroke(contrast: Boolean): androidx.compose.foundation.BorderStroke? {
        return if (contrast) {
            androidx.compose.foundation.BorderStroke(2.dp, if (isDarkMode) Color.White else Color.Black)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    }

    // Required Android physical bluetooth runtime permissions
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            bleManager.startScan()
            showScanDialog = true
        }
    }

    fun checkAndStartScan() {
        val hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermissions) {
            bleManager.startScan()
            showScanDialog = true
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // Keyboard controls handler with integrated command logging
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(contrastBgColor)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val nativeEvent = keyEvent.nativeKeyEvent
                    if (nativeEvent.repeatCount > 0) {
                        return@onKeyEvent true
                    }
                    val cmd = when (keyEvent.key) {
                        Key.W, Key.DirectionUp -> "F"
                        Key.S, Key.DirectionDown -> "B"
                        Key.A, Key.DirectionLeft -> "L"
                        Key.D, Key.DirectionRight -> "R"
                        Key.Spacebar -> "S"
                        Key.K -> "K"
                        Key.E -> "E"
                        else -> null
                    }
                    if (cmd != null) {
                        sendBluetoothCommand(cmd)
                        true
                    } else false
                } else if (keyEvent.type == KeyEventType.KeyUp) {
                    val isReleaseStop = when (keyEvent.key) {
                        Key.W, Key.DirectionUp, Key.S, Key.DirectionDown,
                        Key.A, Key.DirectionLeft, Key.D, Key.DirectionRight -> true
                        else -> false
                    }
                    if (isReleaseStop) {
                        sendBluetoothCommand("S")
                        true
                    } else false
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cybernetic Header layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(
                        if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Branded cyber logo and system names
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    A5XLogo(size = 38.dp)
                    Column {
                        Text(
                            text = "AIR-A5X DIGITAL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ROBO SOCCER FLT ENGINE v4.2",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Control Hub Row (Connect Robot + Settings Gear icon)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bluetooth interactive connection hub button and state colors
                    Button(
                        onClick = {
                            if (bleStatus == BLEStatus.CONNECTED) {
                                bleManager.disconnect()
                            } else {
                                checkAndStartScan()
                            }
                        },
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("connect_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (bleStatus) {
                                BLEStatus.CONNECTED -> Color(0xFF10B981)
                                BLEStatus.CONNECTING -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when (bleStatus) {
                                    BLEStatus.CONNECTED -> Icons.Default.Check
                                    BLEStatus.CONNECTING -> Icons.Default.Refresh
                                    else -> Icons.Default.Share
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when (bleStatus) {
                                    BLEStatus.CONNECTED -> "CONNECTED"
                                    BLEStatus.CONNECTING -> "LINKING..."
                                    else -> "CONNECT BOT"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Settings Toggle Gear Button beside the Connect button
                    IconButton(
                        onClick = { isSettingsOpen = !isSettingsOpen },
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isSettingsOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .testTag("settings_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isSettingsOpen) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = "System Settings Dashboard",
                            tint = if (isSettingsOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Main Body: Render Settings, or render clean spacious Driving Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isSettingsOpen) {
                    // Settings is opened: Show only the settings layout
                    SettingsScreenContent(
                        bleManager = bleManager,
                        isDarkMode = isDarkMode,
                        telemetry = telemetry,
                        discoveredDevices = discoveredDevices,
                        bleStatus = bleStatus,
                        highContrastMode = highContrastMode,
                        onToggleContrast = { highContrastMode = !highContrastMode },
                        onToggleDark = { onToggleDark() },
                        logHistory = logHistory,
                        onSendCommand = sendBluetoothCommand,
                        sliderSpeed = sliderSpeed,
                        onSpeedChanged = { sliderSpeed = it },
                        driveControlMode = driveControlMode,
                        onControlModeChanged = { driveControlMode = it },
                        onCloseSettings = { isSettingsOpen = false },
                        contrastCardColor = contrastCardColor,
                        borderStroke = borderStroke(highContrastMode),
                        onOpenScanDialog = {
                            bleManager.startScan()
                            showScanDialog = true
                        }
                    )
                } else {
                    // Settings is closed: Show Clean Drive controls + Bottom high speed actions
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Spacious Flight-Control Panel
                        Box(modifier = Modifier.weight(1f)) {
                            SplitCockpitDrivePanel(
                                driveControlMode = driveControlMode,
                                telemetry = telemetry,
                                isDarkMode = isDarkMode,
                                highContrastMode = highContrastMode,
                                onSendCommand = sendBluetoothCommand,
                                sliderSpeed = sliderSpeed,
                                onSpeedChanged = { sliderSpeed = it },
                                contrastCardColor = contrastCardColor,
                                borderStroke = borderStroke(highContrastMode)
                            )
                        }

                        // High-fidelity compact Action Bar settled in the bottom of the screen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(
                                    if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // KICK Button (Compact visual)
                            Button(
                                onClick = { sendBluetoothCommand("K") },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Trigger kick solenoid",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "⚡ KICK",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }
                            }

                            // HALT Button
                            Button(
                                onClick = { sendBluetoothCommand("S") },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxHeight(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "🛑 HALT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // EMERGENCY STOP Button
                            Button(
                                onClick = { sendBluetoothCommand("E") },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxHeight(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Hardware lockout stop",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "EMERGENCY",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bluetooth Scanning & Discovery drawer overlay dialog
        if (showScanDialog) {
            Dialog(onDismissRequest = {
                bleManager.stopScan()
                showScanDialog = false
            }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .fillMaxHeight(0.8f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = borderStroke(highContrastMode)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "ACTIVE SCANNING CORES",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Scan finished. Found ${discoveredDevices.size} match(es)",
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            // Animated scan loop radar bar
                            val infiniteTransition = rememberInfiniteTransition(label = "scan_loop")
                            val progress by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1400, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "radar_prog"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(2.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(2.dp))
                                )
                            }

                            if (discoveredDevices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Searching air bands...\nMake sure ESP32 bot has BLE enabled",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(discoveredDevices) { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.4f
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    bleManager.connectToDevice(item.address)
                                                    showScanDialog = false
                                                }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = item.address,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Tap to pair",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                bleManager.stopScan()
                                showScanDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "CLOSE MONITOR", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreenContent(
    bleManager: BLEManager,
    isDarkMode: Boolean,
    telemetry: RobotTelemetry,
    discoveredDevices: List<RoboDevice>,
    bleStatus: BLEStatus,
    highContrastMode: Boolean,
    onToggleContrast: () -> Unit,
    onToggleDark: () -> Unit,
    logHistory: SnapshotStateList<String>,
    onSendCommand: (String) -> Unit,
    sliderSpeed: Float,
    onSpeedChanged: (Float) -> Unit,
    driveControlMode: String,
    onControlModeChanged: (String) -> Unit,
    onCloseSettings: () -> Unit,
    contrastCardColor: Color,
    borderStroke: androidx.compose.foundation.BorderStroke?,
    onOpenScanDialog: () -> Unit
) {
    var customCommandText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section 1: Dashboard title, Back button and Switching Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = contrastCardColor),
                border = borderStroke
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚙️ SETTINGS BOARD",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = onCloseSettings,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SAVE & DRIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Switching Mode Trigger
                    Text(
                        text = "DRIVE INPUT INTERFACE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(
                                if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair("joystick", "🎮 TWO JOYSTICKS"),
                            Pair("tactile_grid", "🔢 TACTILE GRID")
                        ).forEach { (mode, label) ->
                            val isSelected = driveControlMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                    .clickable { onControlModeChanged(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: SPEED Limit Controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = contrastCardColor),
                border = borderStroke
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚙️ MOTOR speed THROTTLE GEARS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "LIMIT: ${sliderSpeed.toInt()}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("CREEP", 80f, Color(0xFF10B981)),
                            Triple("MID SPEED", 180f, Color(0xFFF59E0B)),
                            Triple("BOOST GEAR", 255f, Color(0xFFEF4444))
                        ).forEach { (label, presetVal, color) ->
                            val isSelected = sliderSpeed.toInt() == presetVal.toInt()
                            Button(
                                onClick = { onSpeedChanged(presetVal) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) color else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Slider(
                        value = sliderSpeed,
                        onValueChange = onSpeedChanged,
                        valueRange = 0f..255f,
                        modifier = Modifier.height(30.dp)
                    )
                }
            }
        }

        // Section 3: Telemetry graphs/metrics (battery voltage, temp celsius, ping rssi latency)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = contrastCardColor),
                border = borderStroke
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "📊 LIVE SENSORS & TELEMETRY DIALS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        TelemetryDashboard(
                            telemetry = telemetry,
                            isDarkMode = isDarkMode
                        )
                    }
                }
            }
        }

        // Section 4: BLE Radio pairing scanner + UART text command logger side-by-side (scroll stacked)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // BLE Deck
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = contrastCardColor),
                    border = borderStroke
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AIR BLE DECK",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (bleStatus == BLEStatus.CONNECTED) Color(0xFF10B981) else Color(0xFFEF4444),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = bleStatus.name,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Scan Button
                            Button(
                                onClick = onOpenScanDialog,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("SCAN ESP32 MATCHES", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "DEVICES DETECTED:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                if (discoveredDevices.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "NO DEVICES FOUND",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(discoveredDevices) { device ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp))
                                                    .clickable { bleManager.connectToDevice(device.address) }
                                                    .padding(6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(device.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    Text(device.address, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                                                }
                                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Theme switches
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = onToggleDark,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (isDarkMode) "LIGHT MODE" else "DARK MODE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                FilledTonalButton(
                                    onClick = onToggleContrast,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (highContrastMode) "NORMAL" else "CONTRAST", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Green CLI Log terminal Console
                Card(
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = contrastCardColor),
                    border = borderStroke
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "UART SERIAL TRANSMIT CONSOLE",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "[CLEAR]",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { logHistory.clear() }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color(0xFF0F172A), shape = RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shape = RoundedCornerShape(10.dp))
                                    .padding(6.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (logHistory.isEmpty()) {
                                        item {
                                            Text(
                                                text = "CONSOLE STANDBY - SEND CODES BELOW",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    } else {
                                        items(logHistory) { log ->
                                            Text(
                                                text = log,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = if (log.contains("CMD_E")) Color(0xFFFF5252) else Color(0xFF22C55E)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("INF" to "I", "RST" to "R", "BST" to "H", "KICK" to "K").forEach { macro ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp))
                                            .clickable { onSendCommand(macro.second) }
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = macro.first,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = customCommandText,
                                    onValueChange = { customCommandText = it.uppercase() },
                                    placeholder = { Text("RAW_CMD", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Button(
                                    onClick = {
                                        if (customCommandText.isNotEmpty()) {
                                            onSendCommand(customCommandText)
                                            customCommandText = ""
                                        }
                                    },
                                    modifier = Modifier.height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("SEND CLI", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Compact visual flight deck card wrapping
@Composable
fun SplitCockpitDrivePanel(
    driveControlMode: String,
    telemetry: com.example.ble.RobotTelemetry,
    isDarkMode: Boolean,
    highContrastMode: Boolean,
    onSendCommand: (String) -> Unit,
    sliderSpeed: Float,
    onSpeedChanged: (Float) -> Unit,
    contrastCardColor: Color,
    borderStroke: androidx.compose.foundation.BorderStroke?
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = contrastCardColor),
        border = borderStroke
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val containerHeight = maxHeight
            val containerWidth = maxWidth

            if (driveControlMode == "joystick") {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Throttle joystick
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "THROTTLE: ${sliderSpeed.toInt()}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        RoboJoystick(
                            size = minOf(containerHeight - 20.dp, containerWidth * 0.48f, 400.dp),
                            isThrottle = true,
                            throttleValue = sliderSpeed,
                            onThrottleChanged = { onSpeedChanged(it) }
                        )
                    }

                    // Separation Line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.8f)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Right Steering joystick
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "STEERING",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        RoboJoystick(
                            size = minOf(containerHeight - 20.dp, containerWidth * 0.48f, 400.dp),
                            onDirectionChanged = { direction ->
                                val cmd = when (direction) {
                                    JoystickDirection.FORWARD -> "F"
                                    JoystickDirection.BACKWARD -> "B"
                                    JoystickDirection.LEFT -> "L"
                                    JoystickDirection.RIGHT -> "R"
                                    JoystickDirection.FORWARD_LEFT -> "FL"
                                    JoystickDirection.FORWARD_RIGHT -> "FR"
                                    JoystickDirection.BACKWARD_LEFT -> "BL"
                                    JoystickDirection.BACKWARD_RIGHT -> "BR"
                                    JoystickDirection.STOP -> "S"
                                }
                                onSendCommand(cmd)
                            }
                        )
                    }
                }
            } else {
                // Tactile arrow grid (HUGE buttons)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(minOf(containerHeight - 8.dp, containerWidth * 0.95f, 420.dp))
                    ) {
                        TactileButtonsGrid(
                            onCommand = onSendCommand,
                            activeCommand = telemetry.activeCommand,
                            highContrastMode = highContrastMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TactileButtonsGrid(
    onCommand: (String) -> Unit,
    activeCommand: String,
    highContrastMode: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: FL, F, FR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ControlButton(
                icon = Icons.Default.KeyboardArrowUp,
                label = "FORWARD_LEFT",
                isActive = activeCommand == "FL",
                onClick = { onCommand("FL") },
                rotation = -45f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                highContrastMode = highContrastMode
            )
            ControlButton(
                icon = Icons.Default.KeyboardArrowUp,
                label = "FORWARD",
                isActive = activeCommand == "F",
                onClick = { onCommand("F") },
                rotation = 0f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                highContrastMode = highContrastMode
            )
            ControlButton(
                icon = Icons.Default.KeyboardArrowUp,
                label = "FORWARD_RIGHT",
                isActive = activeCommand == "FR",
                onClick = { onCommand("FR") },
                rotation = 45f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                highContrastMode = highContrastMode
            )
        }

        // Row 2: L, S, R
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ControlButton(
                icon = Icons.Default.KeyboardArrowUp,
                label = "LEFT",
                isActive = activeCommand == "L",
                onClick = { onCommand("L") },
                rotation = -90f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                highContrastMode = highContrastMode
            )
            ControlButton(
                icon = Icons.Default.Close,
                label = "STOP",
                isActive = activeCommand == "S",
                onClick = { onCommand("S") },
                rotation = 0f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                highContrastMode = highContrastMode
            )
            ControlButton(
                icon = Icons.Default.KeyboardArrowUp,
                label = "RIGHT",
                isActive = activeCommand == "R",
                onClick = { onCommand("R") },
                rotation = 90f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                highContrastMode = highContrastMode
            )
        }

        // Row 3: BL, B, BR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ControlButton(
                icon = Icons.Default.KeyboardArrowUp,
                label = "BACKWARD_LEFT",
                isActive = activeCommand == "BL",
                onClick = { onCommand("BL") },
                rotation = -135f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                highContrastMode = highContrastMode
            )
            ControlButton(
                icon = Icons.Default.KeyboardArrowUp,
                label = "BACKWARD",
                isActive = activeCommand == "B",
                onClick = { onCommand("B") },
                rotation = 180f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                highContrastMode = highContrastMode
            )
            ControlButton(
                icon = Icons.Default.KeyboardArrowUp,
                label = "BACKWARD_RIGHT",
                isActive = activeCommand == "BR",
                onClick = { onCommand("BR") },
                rotation = 135f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                highContrastMode = highContrastMode
            )
        }
    }
}

@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highContrastMode: Boolean = false,
    rotation: Float = 0f
) {
    val containerCol = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val contentCol = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .shadow(if (isActive) 3.dp else 1.dp, RoundedCornerShape(10.dp))
            .background(containerCol, shape = RoundedCornerShape(10.dp))
            .border(
                if (highContrastMode) 2.dp else 1.dp,
                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$label control button"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentCol,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(rotationZ = rotation)
        )
    }
}

@Composable
fun Divider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: Dp = 1.dp
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color = color)
    )
}
