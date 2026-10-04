package org.example.project

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import org.example.project.database.DatabaseDriverFactory


class MainActivity : ComponentActivity() {

    // 1. Prepare the permission requester
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            println("Bluetooth permissions granted!")
        } else {
            println("Bluetooth permissions denied. P2P will not work.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Check Android version and request the correct permissions
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            // Android 11 and lower
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        // 3. Launch the pop-up asking the user
        requestPermissionLauncher.launch(permissionsToRequest)

        setContent {
            App(driverFactory = DatabaseDriverFactory(this))
        }
    }
}