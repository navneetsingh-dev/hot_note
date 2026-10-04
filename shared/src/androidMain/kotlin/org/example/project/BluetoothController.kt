package org.example.project

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
    private val context: Context,
    private val requestPermissions: () -> Unit,
    private val requestEnableBluetooth: () -> Unit
) : BluetoothController {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val appUuid: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private val appName = "HotNoteP2P"

    override fun isBluetoothEnabled(): Boolean {
        return adapter?.isEnabled == true
    }

    private fun hasPermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
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
        println("System.out: Searching for nearby devices...")
    }

    @SuppressLint("MissingPermission")
    override fun startServer() {
        if (!hasPermissions()) {
            println("System.out: Requesting Bluetooth permissions from user...")
            requestPermissions()
            return
        }

        if (!isBluetoothEnabled()) {
            println("System.out: Requesting user to turn on Bluetooth...")
            requestEnableBluetooth()
            return
        }

        thread {
            var serverSocket: BluetoothServerSocket? = null
            try {
                serverSocket = adapter?.listenUsingRfcommWithServiceRecord(appName, appUuid)
                println("System.out: Server started. Waiting for connections...")

                val socket: BluetoothSocket? = serverSocket?.accept()

                if (socket != null) {
                    println("System.out: Client connected successfully! Ready to receive notes.")
                    serverSocket?.close() // <-- Fixed null-safety check here
                }
            } catch (e: IOException) {
                println("System.out: Server socket failed: ${e.message}")
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
            try {
                val device = adapter?.getRemoteDevice(deviceAddress)
                val socket: BluetoothSocket? = device?.createRfcommSocketToServiceRecord(appUuid)
                adapter?.cancelDiscovery()

                println("System.out: Attempting to connect to $deviceAddress...")
                socket?.connect()

                println("System.out: Connected to server successfully!")
            } catch (e: IOException) {
                println("System.out: Connection failed: ${e.message}")
            }
        }
    }
}

@Composable
actual fun rememberBluetoothController(): BluetoothController {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.all { it.value }) {
                println("System.out: Permissions granted! Please click the button again.")
            }
        }
    )

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { _ ->
            println("System.out: Bluetooth prompt finished. Please click the button again.")
        }
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