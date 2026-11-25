package org.example.project

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class AndroidBluetoothController(
    context: Context,
    private val requestPermissions: () -> Unit,
    private val requestEnableBluetooth: () -> Unit
) : BluetoothController {

    private val appContext: Context = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val appUuid: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private val appName = "HotNoteP2P"

    override fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    private fun hasPermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }
        if (!isBluetoothEnabled()) {
            requestEnableBluetooth()
            return
        }
        adapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    override fun startServer() {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }
        if (!isBluetoothEnabled()) {
            requestEnableBluetooth()
            return
        }
        thread {
            var serverSocket: BluetoothServerSocket? = null
            var clientSocket: BluetoothSocket? = null
            try {
                serverSocket = adapter?.listenUsingRfcommWithServiceRecord(appName, appUuid)
                clientSocket = serverSocket?.accept()
            } catch (_: IOException) {
            } finally {
                runCatching { clientSocket?.close() }
                runCatching { serverSocket?.close() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun connectToDevice(deviceAddress: String) {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }
        if (!isBluetoothEnabled()) {
            requestEnableBluetooth()
            return
        }
        thread {
            var socket: BluetoothSocket? = null
            try {
                val device = adapter?.getRemoteDevice(deviceAddress)
                socket = device?.createRfcommSocketToServiceRecord(appUuid)
                adapter?.cancelDiscovery()
                socket?.connect()
            } catch (_: IOException) {
            } finally {
                runCatching { socket?.close() }
            }
        }
    }
}

@Composable
actual fun rememberBluetoothController(): BluetoothController {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {}
    )
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {}
    )
    return remember {
        AndroidBluetoothController(
            context = context,
            requestPermissions = {
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    )
                } else {
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                }
                permissionLauncher.launch(permissionsToRequest)
            },
            requestEnableBluetooth = {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        )
    }
}
