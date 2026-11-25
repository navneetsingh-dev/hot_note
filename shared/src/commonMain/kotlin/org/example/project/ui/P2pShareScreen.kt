package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.database.NoteEntity
import org.example.project.database.SharedFileEntity
import org.example.project.p2p.P2pController
import org.example.project.p2p.P2pPeer
import org.example.project.p2p.TransferStatus
import org.example.project.p2p.rememberFilePicker

@Composable
fun P2pShareScreen(
    p2pController: P2pController,
    notes: List<NoteEntity>,
    sharedFiles: List<SharedFileEntity>,
    preselectedNoteToShare: NoteEntity? = null,
    onClearPreselectedNote: () -> Unit = {}
) {
    val serverState by p2pController.serverState.collectAsState()
    val btState by p2pController.bluetoothState.collectAsState()
    val lanPeers by p2pController.discoveredLanPeers.collectAsState()
    val transfers by p2pController.transfers.collectAsState()

    var targetHost by remember { mutableStateOf("") }
    var targetPort by remember { mutableStateOf("8765") }
    var targetPin by remember { mutableStateOf("") }

    var showNotePickerForLan by remember { mutableStateOf(preselectedNoteToShare != null) }
    var noteToSend by remember { mutableStateOf(preselectedNoteToShare) }
    var btTargetAddressForNote by remember { mutableStateOf<String?>(null) }
    var btTargetAddressForFile by remember { mutableStateOf<String?>(null) }

    val pickFileForLan = rememberFilePicker { picked ->
        val portNum = targetPort.toIntOrNull() ?: 8765
        if (targetHost.isNotBlank()) {
            p2pController.sendFileToLanPeer(targetHost, portNum, targetPin, picked)
        }
    }

    val pickFileForBt = rememberFilePicker { picked ->
        val addr = btTargetAddressForFile
        if (!addr.isNullOrBlank()) {
            p2pController.sendFileViaBluetooth(addr, picked)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "P2P Laptop ↔ Tablet Share",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Transfer files & notes over local Wi-Fi/Hotspot (Arch/Linux/macOS/Windows) or Bluetooth",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. LAN / Wi-Fi Share Server Card (Tablet -> Laptop Browser or CLI)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("1. Wi-Fi / Hotspot Share Portal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(serverState.statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = serverState.isRunning,
                            onCheckedChange = { enable ->
                                if (enable) p2pController.startLanServer() else p2pController.stopLanServer()
                            }
                        )
                    }

                    if (serverState.isRunning) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = OneNotePurple.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Open on your Laptop browser:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OneNotePurple)
                                serverState.urls.forEach { url ->
                                    Text(url, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (serverState.requirePin) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Pairing PIN: ", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            serverState.pairingPin,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = OneNotePurple
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(onClick = { p2pController.regeneratePin() }) { Text("New PIN") }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Require 6-digit Pairing PIN", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = serverState.requirePin,
                            onCheckedChange = { p2pController.setRequirePin(it) }
                        )
                    }
                }
            }
        }

        // 2. Send to Laptop / Peer over Wi-Fi LAN
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. Send File or Note to Laptop / Peer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { p2pController.startLanDiscovery() }) {
                            Text("Discover LAN")
                        }
                    }

                    if (lanPeers.isNotEmpty()) {
                        Text("Discovered Peers (tap to select):", style = MaterialTheme.typography.labelMedium, color = OneNotePurple)
                        lanPeers.forEach { peer ->
                            PeerRow(peer = peer) {
                                targetHost = peer.address
                                targetPort = peer.port.toString()
                            }
                        }
                    } else {
                        Text(
                            "Run `python3 tools/hotnote_peer.py serve` on your Arch Linux laptop, or enter its LAN IP below:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = targetHost,
                            onValueChange = { targetHost = it },
                            label = { Text("Peer IP (e.g. 192.168.1.50)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.6f)
                        )
                        OutlinedTextField(
                            value = targetPort,
                            onValueChange = { targetPort = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    OutlinedTextField(
                        value = targetPin,
                        onValueChange = { targetPin = it },
                        label = { Text("Peer Pairing PIN (optional if disabled)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = pickFileForLan,
                            enabled = targetHost.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = OneNotePurple),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pick & Send File")
                        }
                        OutlinedButton(
                            onClick = { showNotePickerForLan = true },
                            enabled = targetHost.isNotBlank() && notes.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (noteToSend != null) "Send \"${noteToSend!!.title}\"" else "Send a Note")
                        }
                    }
                }
            }
        }

        // 3. Bluetooth RFCOMM P2P Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("3. Bluetooth P2P (RFCOMM)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(btState.statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = btState.isServerRunning,
                            onCheckedChange = { enable ->
                                if (enable) p2pController.startBluetoothServer() else p2pController.stopBluetoothServer()
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { p2pController.startBluetoothDiscovery() }) {
                            Text(if (btState.isDiscovering) "Scanning..." else "Scan Nearby")
                        }
                        OutlinedButton(onClick = { p2pController.refreshPairedBluetoothDevices() }) {
                            Text("Refresh Paired")
                        }
                    }

                    val allBtDevices = (btState.pairedDevices + btState.discoveredDevices).distinctBy { it.address }
                    if (allBtDevices.isEmpty()) {
                        Text(
                            "No Bluetooth devices listed yet. Tap Scan Nearby or Refresh Paired.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        allBtDevices.forEach { dev ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dev.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(dev.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = {
                                            btTargetAddressForFile = dev.address
                                            pickFileForBt()
                                        }) { Text("File") }
                                        TextButton(
                                            onClick = { btTargetAddressForNote = dev.address },
                                            enabled = notes.isNotEmpty()
                                        ) { Text("Note") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Active Transfers & History
        if (transfers.isNotEmpty()) {
            item {
                Text("Recent Transfers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(transfers, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.direction.name}: ${item.title}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                item.status.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (item.status) {
                                    TransferStatus.COMPLETED -> Color(0xFF217346)
                                    TransferStatus.FAILED -> MaterialTheme.colorScheme.error
                                    TransferStatus.IN_PROGRESS -> OneNotePurple
                                }
                            )
                        }
                        if (item.status == TransferStatus.IN_PROGRESS) {
                            LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                        }
                        Text("${item.peerName} • ${item.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 5. Saved / Shared Files Vault
        if (sharedFiles.isNotEmpty()) {
            item {
                Text("Shared & Received Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(sharedFiles, key = { it.id }) { file ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable {
                        p2pController.openReceivedFile(file.filePath, file.mimeType)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${file.direction} • ${file.sizeBytes} B • ${file.peerName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { p2pController.openReceivedFile(file.filePath, file.mimeType) }) {
                            Text("Open")
                        }
                    }
                }
            }
        }
    }

    // Note Picker Dialog for LAN
    if (showNotePickerForLan) {
        NoteSelectionDialog(
            title = "Send Note to $targetHost",
            notes = notes,
            preselected = noteToSend,
            onDismiss = {
                showNotePickerForLan = false
                noteToSend = null
                onClearPreselectedNote()
            },
            onSelect = { chosen ->
                val portNum = targetPort.toIntOrNull() ?: 8765
                p2pController.sendNoteToLanPeer(targetHost, portNum, targetPin, chosen)
                showNotePickerForLan = false
                noteToSend = null
                onClearPreselectedNote()
            }
        )
    }

    // Note Picker Dialog for Bluetooth
    btTargetAddressForNote?.let { addr ->
        NoteSelectionDialog(
            title = "Send Note via Bluetooth",
            notes = notes,
            preselected = noteToSend,
            onDismiss = { btTargetAddressForNote = null },
            onSelect = { chosen ->
                p2pController.sendNoteViaBluetooth(addr, chosen)
                btTargetAddressForNote = null
            }
        )
    }
}

@Composable
private fun PeerRow(peer: P2pPeer, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = OneNotePurple.copy(alpha = 0.09f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(peer.name, fontWeight = FontWeight.SemiBold)
                Text("${peer.address}:${peer.port}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Use", color = OneNotePurple, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NoteSelectionDialog(
    title: String,
    notes: List<NoteEntity>,
    preselected: NoteEntity?,
    onDismiss: () -> Unit,
    onSelect: (NoteEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.height(280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    val isPre = note.id == preselected?.id
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPre) OneNotePurple.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(note) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(note.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatNoteTimestamp(note.updatedAt), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (preselected != null) {
                Button(
                    onClick = { onSelect(preselected) },
                    colors = ButtonDefaults.buttonColors(containerColor = OneNotePurple)
                ) { Text("Send Selected") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
