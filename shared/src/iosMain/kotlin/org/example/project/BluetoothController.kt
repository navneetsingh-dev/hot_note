package org.example.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class IosBluetoothController : BluetoothController {
    override fun isBluetoothEnabled(): Boolean = false
    override fun startDiscovery() {}
    override fun startServer() {}
    override fun connectToDevice(deviceAddress: String) {}
}

@Composable
actual fun rememberBluetoothController(): BluetoothController {
    return remember { IosBluetoothController() }
}
