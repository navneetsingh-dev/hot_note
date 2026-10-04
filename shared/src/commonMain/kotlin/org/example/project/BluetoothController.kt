package org.example.project

import androidx.compose.runtime.Composable

// 1. Changed to an interface
interface BluetoothController {
    fun isBluetoothEnabled(): Boolean
    fun startDiscovery()
    fun startServer()
    fun connectToDevice(deviceAddress: String)
}

// 2. The Composable bridge remains exactly the same
@Composable
expect fun rememberBluetoothController(): BluetoothController