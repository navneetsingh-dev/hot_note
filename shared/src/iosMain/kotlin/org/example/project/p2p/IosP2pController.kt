package org.example.project.p2p

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.database.NoteEntity
import org.example.project.database.NoteRepository
import org.example.project.model.NoteTransferPayload

class IosP2pController : P2pController {
    override val serverState: StateFlow<P2pServerState> = MutableStateFlow(P2pServerState()).asStateFlow()
    override val bluetoothState: StateFlow<BluetoothState> = MutableStateFlow(BluetoothState(isSupported = false)).asStateFlow()
    override val discoveredLanPeers: StateFlow<List<P2pPeer>> = MutableStateFlow<List<P2pPeer>>(emptyList()).asStateFlow()
    override val transfers: StateFlow<List<P2pTransferItem>> = MutableStateFlow<List<P2pTransferItem>>(emptyList()).asStateFlow()

    override fun startLanServer() {}
    override fun stopLanServer() {}
    override fun regeneratePin() {}
    override fun setRequirePin(required: Boolean) {}
    override fun startLanDiscovery() {}
    override fun stopLanDiscovery() {}
    override fun sendNoteToLanPeer(address: String, port: Int, pin: String, note: NoteEntity) {}
    override fun sendFileToLanPeer(address: String, port: Int, pin: String, file: PickedFile) {}
    override fun startBluetoothServer() {}
    override fun stopBluetoothServer() {}
    override fun startBluetoothDiscovery() {}
    override fun refreshPairedBluetoothDevices() {}
    override fun sendNoteViaBluetooth(deviceAddress: String, note: NoteEntity) {}
    override fun sendFileViaBluetooth(deviceAddress: String, file: PickedFile) {}
    override fun openReceivedFile(filePath: String, mimeType: String): Boolean = false
    override fun dispose() {}
}

@Composable
actual fun rememberP2pController(
    repository: NoteRepository,
    onNoteReceived: (NoteTransferPayload) -> Unit
): P2pController {
    return remember { IosP2pController() }
}

@Composable
actual fun rememberFilePicker(
    onFilePicked: (PickedFile) -> Unit
): () -> Unit {
    return {}
}
