package org.example.project.p2p

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow
import org.example.project.database.NoteEntity
import org.example.project.database.NoteRepository
import org.example.project.model.NoteTransferPayload

interface P2pController {
    val serverState: StateFlow<P2pServerState>
    val bluetoothState: StateFlow<BluetoothState>
    val discoveredLanPeers: StateFlow<List<P2pPeer>>
    val transfers: StateFlow<List<P2pTransferItem>>

    fun startLanServer()
    fun stopLanServer()
    fun regeneratePin()
    fun setRequirePin(required: Boolean)

    fun startLanDiscovery()
    fun stopLanDiscovery()

    fun sendNoteToLanPeer(address: String, port: Int, pin: String, note: NoteEntity)
    fun sendFileToLanPeer(address: String, port: Int, pin: String, file: PickedFile)

    fun startBluetoothServer()
    fun stopBluetoothServer()
    fun startBluetoothDiscovery()
    fun refreshPairedBluetoothDevices()
    fun sendNoteViaBluetooth(deviceAddress: String, note: NoteEntity)
    fun sendFileViaBluetooth(deviceAddress: String, file: PickedFile)

    fun openReceivedFile(filePath: String, mimeType: String): Boolean
    fun dispose()
}

@Composable
expect fun rememberP2pController(
    repository: NoteRepository,
    onNoteReceived: (NoteTransferPayload) -> Unit
): P2pController

@Composable
expect fun rememberFilePicker(
    onFilePicked: (PickedFile) -> Unit
): () -> Unit
