package org.example.project.p2p

enum class P2pTransport {
    LAN_WIFI,
    BLUETOOTH
}

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class TransferStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

data class P2pPeer(
    val id: String,
    val name: String,
    val address: String,
    val port: Int = 8765,
    val transport: P2pTransport,
    val platform: String = "Peer"
)

data class P2pTransferItem(
    val id: String,
    val title: String,
    val kind: String, // "NOTE" or "FILE"
    val sizeBytes: Long,
    val progress: Float,
    val direction: TransferDirection,
    val status: TransferStatus,
    val peerName: String,
    val message: String = ""
)

data class P2pServerState(
    val isRunning: Boolean = false,
    val urls: List<String> = emptyList(),
    val port: Int = 8765,
    val pairingPin: String = "000000",
    val requirePin: Boolean = true,
    val statusText: String = "Server stopped"
)

data class BluetoothState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val hasPermissions: Boolean = false,
    val isServerRunning: Boolean = false,
    val isDiscovering: Boolean = false,
    val pairedDevices: List<P2pPeer> = emptyList(),
    val discoveredDevices: List<P2pPeer> = emptyList(),
    val statusText: String = "Bluetooth idle"
)

data class PickedFile(
    val name: String,
    val uriOrPath: String,
    val mimeType: String,
    val sizeBytes: Long
)
