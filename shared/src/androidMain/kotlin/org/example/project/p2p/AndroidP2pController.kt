package org.example.project.p2p

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.example.project.database.NoteEntity
import org.example.project.database.NoteRepository
import org.example.project.model.NoteTransferPayload
import org.example.project.model.SafeNoteCodec
import org.example.project.model.SimpleJson
import org.example.project.p2p.P2pStreamCodec.toHex
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

class AndroidP2pController(
    context: Context,
    private val repository: NoteRepository,
    private val onNoteReceived: (NoteTransferPayload) -> Unit,
    private val requestBtPermissions: () -> Unit,
    private val requestEnableBt: () -> Unit
) : P2pController {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val receivedDir: File by lazy {
        File(appContext.filesDir, "p2p_received").apply { mkdirs() }
    }

    private val deviceName: String by lazy {
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "HotNote Android" }
    }

    private val appUuid: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private val nsdServiceType = "_hotnote._tcp."

    private val _serverState = MutableStateFlow(
        P2pServerState(pairingPin = generatePin())
    )
    override val serverState: StateFlow<P2pServerState> = _serverState.asStateFlow()

    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val btAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _bluetoothState = MutableStateFlow(
        BluetoothState(
            isSupported = btAdapter != null,
            isEnabled = btAdapter?.isEnabled == true,
            hasPermissions = hasBluetoothPermissions()
        )
    )
    override val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    private val _discoveredLanPeers = MutableStateFlow<List<P2pPeer>>(emptyList())
    override val discoveredLanPeers: StateFlow<List<P2pPeer>> = _discoveredLanPeers.asStateFlow()

    private val _transfers = MutableStateFlow<List<P2pTransferItem>>(emptyList())
    override val transfers: StateFlow<List<P2pTransferItem>> = _transfers.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private var btServerSocket: BluetoothServerSocket? = null
    private var btServerJob: Job? = null

    private val nsdManager: NsdManager? = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var btReceiverRegistered = false

    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val name = try {
                            device.name ?: "Bluetooth Device"
                        } catch (_: SecurityException) {
                            "Bluetooth Device"
                        }
                        val addr = device.address ?: return
                        val peer = P2pPeer(
                            id = "bt_$addr",
                            name = name,
                            address = addr,
                            port = 0,
                            transport = P2pTransport.BLUETOOTH,
                            platform = "Bluetooth"
                        )
                        _bluetoothState.update { state ->
                            val updated = (state.discoveredDevices.filterNot { it.address == addr } + peer)
                            state.copy(discoveredDevices = updated)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _bluetoothState.update { it.copy(isDiscovering = false, statusText = "Bluetooth scan complete") }
                }
            }
        }
    }

    init {
        refreshPairedBluetoothDevices()
    }

    private fun generatePin(): String = Random.nextInt(100000, 999999).toString()

    // ==========================================
    // LAN WIFI P2P HTTP SERVER + NSD
    // ==========================================

    override fun startLanServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch {
            var boundSocket: ServerSocket? = null
            for (candidatePort in 8765..8775) {
                try {
                    boundSocket = ServerSocket(candidatePort)
                    break
                } catch (_: Throwable) {
                }
            }
            if (boundSocket == null) {
                boundSocket = ServerSocket(0)
            }
            serverSocket = boundSocket
            val port = boundSocket.localPort
            val ips = getLocalIpv4Addresses()
            val urls = if (ips.isEmpty()) listOf("http://127.0.0.1:$port") else ips.map { "http://$it:$port" }

            _serverState.update {
                it.copy(
                    isRunning = true,
                    port = port,
                    urls = urls,
                    statusText = "Listening on port $port"
                )
            }
            registerNsdService(port)

            try {
                while (isActive && !boundSocket.isClosed) {
                    val client = boundSocket.accept()
                    launch { handleHttpClient(client) }
                }
            } catch (_: Throwable) {
            } finally {
                unregisterNsdService()
                _serverState.update { it.copy(isRunning = false, urls = emptyList(), statusText = "Server stopped") }
            }
        }
    }

    override fun stopLanServer() {
        unregisterNsdService()
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        _serverState.update { it.copy(isRunning = false, urls = emptyList(), statusText = "Server stopped") }
    }

    override fun regeneratePin() {
        _serverState.update { it.copy(pairingPin = generatePin()) }
    }

    override fun setRequirePin(required: Boolean) {
        _serverState.update { it.copy(requirePin = required) }
    }

    private fun handleHttpClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 60_000
            val input = BufferedInputStream(sock.getInputStream(), 65536)
            val output = BufferedOutputStream(sock.getOutputStream(), 65536)
            val head = P2pStreamCodec.readHttpRequestHead(input) ?: return

            if (head.method == "OPTIONS") {
                writeHttpResponse(output, 204, "text/plain", ByteArray(0))
                return
            }

            if (head.path == "/" && head.method == "GET") {
                val html = buildWebPortalHtml()
                writeHttpResponse(output, 200, "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                return
            }

            if (head.path == "/api/info" && head.method == "GET") {
                val infoJson = SimpleJson.stringify(
                    mapOf(
                        "name" to deviceName,
                        "platform" to "Android",
                        "protocol" to 2,
                        "requirePin" to _serverState.value.requirePin
                    )
                )
                writeHttpResponse(output, 200, "application/json; charset=utf-8", infoJson.toByteArray(Charsets.UTF_8))
                return
            }

            // Verify PIN for all protected endpoints
            val currentState = _serverState.value
            if (currentState.requirePin) {
                val providedPin = head.headers["x-pair-pin"] ?: head.queryParams["pin"] ?: ""
                if (providedPin != currentState.pairingPin) {
                    val err = SimpleJson.stringify(mapOf("error" to "Invalid or missing pairing PIN"))
                    writeHttpResponse(output, 401, "application/json; charset=utf-8", err.toByteArray(Charsets.UTF_8))
                    return
                }
            }

            val peerHost = sock.inetAddress?.hostAddress ?: "Laptop/Peer"

            when {
                head.path == "/api/notes" && head.method == "GET" -> {
                    val notes = kotlinx.coroutines.runBlocking { repository.getAllNotesSnapshot() }
                    val list = notes.map {
                        mapOf(
                            "id" to it.id,
                            "title" to it.title,
                            "preview" to SafeNoteCodec.pagePreviewText(it.contentBlocks),
                            "updatedAt" to it.updatedAt
                        )
                    }
                    writeHttpResponse(output, 200, "application/json; charset=utf-8", SimpleJson.stringify(list).toByteArray(Charsets.UTF_8))
                }

                head.path.startsWith("/api/notes/") && head.method == "GET" -> {
                    val noteId = P2pStreamCodec.urlDecode(head.path.removePrefix("/api/notes/"))
                    val note = kotlinx.coroutines.runBlocking { repository.getNoteById(noteId) }
                    if (note == null) {
                        writeHttpResponse(output, 404, "application/json", """{"error":"Not found"}""".toByteArray())
                        return
                    }
                    val format = head.queryParams["format"]?.lowercase()
                    if (format == "md" || format == "markdown") {
                        val md = SafeNoteCodec.toMarkdown(note.title, note.contentBlocks)
                        val safeName = SafeNoteCodec.sanitizeFileName(note.title) + ".md"
                        writeHttpDownload(output, "text/markdown; charset=utf-8", safeName, md.toByteArray(Charsets.UTF_8))
                    } else {
                        val payload = NoteTransferPayload(
                            id = note.id,
                            title = note.title,
                            contentBlocks = note.contentBlocks,
                            attachmentsJson = note.attachmentsJson,
                            createdAt = note.createdAt,
                            updatedAt = note.updatedAt,
                            senderName = deviceName
                        )
                        val json = SafeNoteCodec.serializeNotePayload(payload)
                        val safeName = SafeNoteCodec.sanitizeFileName(note.title) + ".hotnote.json"
                        if (head.queryParams["download"] == "1") {
                            writeHttpDownload(output, "application/json; charset=utf-8", safeName, json.toByteArray(Charsets.UTF_8))
                        } else {
                            writeHttpResponse(output, 200, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
                        }
                    }
                }

                head.path == "/api/notes" && head.method == "POST" -> {
                    val len = head.headers["content-length"]?.toIntOrNull() ?: 0
                    if (len <= 0 || len > 5 * 1024 * 1024) {
                        writeHttpResponse(output, 400, "application/json", """{"error":"Invalid note payload size"}""".toByteArray())
                        return
                    }
                    val bodyBytes = P2pStreamCodec.readFixedBytes(input, len)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val parsed = SafeNoteCodec.parseNotePayload(bodyStr)
                        ?: run {
                            // Also accept simple {title, contentBlocks/text, senderName}
                            val map = runCatching { SimpleJson.parse(bodyStr) as? Map<*, *> }.getOrNull()
                            val title = map?.get("title")?.toString()?.takeIf { it.isNotBlank() } ?: "Note from $peerHost"
                            val text = map?.get("contentBlocks")?.toString() ?: map?.get("text")?.toString() ?: ""
                            val now = System.currentTimeMillis()
                            NoteTransferPayload(
                                id = map?.get("id")?.toString()?.takeIf { it.isNotBlank() } ?: "note_${now}_${Random.nextInt(1000, 9999)}",
                                title = title,
                                contentBlocks = text,
                                attachmentsJson = "[]",
                                createdAt = now,
                                updatedAt = now,
                                senderName = map?.get("senderName")?.toString() ?: peerHost
                            )
                        }
                    onNoteReceived(parsed)
                    recordTransfer(
                        P2pTransferItem(
                            id = "rx_note_${System.currentTimeMillis()}",
                            title = parsed.title,
                            kind = "NOTE",
                            sizeBytes = bodyBytes.size.toLong(),
                            progress = 1f,
                            direction = TransferDirection.RECEIVED,
                            status = TransferStatus.COMPLETED,
                            peerName = parsed.senderName,
                            message = "Note received"
                        )
                    )
                    writeHttpResponse(output, 200, "application/json", """{"status":"ok"}""".toByteArray())
                }

                head.path == "/api/files" && head.method == "GET" -> {
                    val files = kotlinx.coroutines.runBlocking { repository.getAllSharedFilesSnapshot() }
                    val list = files.map {
                        mapOf(
                            "id" to it.id,
                            "fileName" to it.fileName,
                            "mimeType" to it.mimeType,
                            "sizeBytes" to it.sizeBytes,
                            "direction" to it.direction,
                            "peerName" to it.peerName,
                            "createdAt" to it.createdAt
                        )
                    }
                    writeHttpResponse(output, 200, "application/json; charset=utf-8", SimpleJson.stringify(list).toByteArray(Charsets.UTF_8))
                }

                head.path.startsWith("/api/files/") && head.method == "GET" -> {
                    val fileId = P2pStreamCodec.urlDecode(head.path.removePrefix("/api/files/"))
                    val fileEntity = kotlinx.coroutines.runBlocking { repository.getSharedFileById(fileId) }
                    val file = fileEntity?.let { File(it.filePath) }
                    if (fileEntity == null || file == null || !file.exists()) {
                        writeHttpResponse(output, 404, "application/json", """{"error":"File not found"}""".toByteArray())
                        return
                    }
                    val safeName = SafeNoteCodec.sanitizeFileName(fileEntity.fileName)
                    val headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: ${fileEntity.mimeType}\r\n" +
                        "Content-Length: ${file.length()}\r\n" +
                        "Content-Disposition: attachment; filename=\"$safeName\"\r\n" +
                        "Connection: close\r\n\r\n"
                    output.write(headers.toByteArray(Charsets.UTF_8))
                    FileInputStream(file).use { fis ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val r = fis.read(buf)
                            if (r <= 0) break
                            output.write(buf, 0, r)
                        }
                    }
                    output.flush()
                }

                head.path == "/api/upload" && head.method == "POST" -> {
                    val contentLength = head.headers["content-length"]?.toLongOrNull() ?: -1L
                    if (contentLength <= 0L) {
                        writeHttpResponse(output, 400, "application/json", """{"error":"Content-Length required"}""".toByteArray())
                        return
                    }
                    val contentType = head.headers["content-type"]
                    val boundary = P2pStreamCodec.extractBoundary(contentType)
                    val transferId = "rx_file_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}"

                    var savedFile: File? = null
                    var finalName = "uploaded_file"
                    var streamRes: P2pStreamCodec.StreamResult? = null

                    if (boundary != null) {
                        val mpResult = P2pStreamCodec.streamMultipartFile(input, boundary, contentLength) { origName ->
                            finalName = SafeNoteCodec.sanitizeFileName(origName)
                            val target = uniqueTargetFile(finalName)
                            savedFile = target
                            FileOutputStream(target)
                        }
                        if (mpResult != null) {
                            streamRes = mpResult.second
                        }
                    } else {
                        val rawName = head.headers["x-file-name"]?.let { P2pStreamCodec.urlDecode(it) } ?: "received_${System.currentTimeMillis()}"
                        finalName = SafeNoteCodec.sanitizeFileName(rawName)
                        val target = uniqueTargetFile(finalName)
                        savedFile = target
                        FileOutputStream(target).use { fos ->
                            streamRes = P2pStreamCodec.copyFixedToStream(input, fos, contentLength) { written ->
                                updateTransferProgress(transferId, written.toFloat() / contentLength.toFloat())
                            }
                        }
                    }

                    val fileOut = savedFile
                    val res = streamRes
                    if (fileOut == null || res == null) {
                        writeHttpResponse(output, 400, "application/json", """{"error":"No file found in upload"}""".toByteArray())
                        return
                    }

                    val expectedSha = head.headers["x-file-sha256"]
                    if (!expectedSha.isNullOrBlank() && !expectedSha.equals(res.sha256Hex, ignoreCase = true)) {
                        fileOut.delete()
                        writeHttpResponse(output, 422, "application/json", """{"error":"SHA-256 mismatch"}""".toByteArray())
                        return
                    }

                    val sender = head.headers["x-peer-name"]?.let { P2pStreamCodec.urlDecode(it) } ?: peerHost
                    val mime = head.headers["x-file-mime"] ?: guessMimeType(finalName)
                    val fileId = "file_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
                    kotlinx.coroutines.runBlocking {
                        repository.saveSharedFile(
                            id = fileId,
                            fileName = finalName,
                            filePath = fileOut.absolutePath,
                            mimeType = mime,
                            sizeBytes = res.bytesWritten,
                            sha256 = res.sha256Hex,
                            direction = "RECEIVED",
                            peerName = sender
                        )
                    }

                    recordTransfer(
                        P2pTransferItem(
                            id = transferId,
                            title = finalName,
                            kind = "FILE",
                            sizeBytes = res.bytesWritten,
                            progress = 1f,
                            direction = TransferDirection.RECEIVED,
                            status = TransferStatus.COMPLETED,
                            peerName = sender,
                            message = "Saved to tablet (${formatBytes(res.bytesWritten)})"
                        )
                    )

                    val respJson = SimpleJson.stringify(
                        mapOf("status" to "ok", "id" to fileId, "fileName" to finalName, "sizeBytes" to res.bytesWritten, "sha256" to res.sha256Hex)
                    )
                    writeHttpResponse(output, 200, "application/json", respJson.toByteArray(Charsets.UTF_8))
                }

                else -> {
                    writeHttpResponse(output, 404, "application/json", """{"error":"Endpoint not found"}""".toByteArray())
                }
            }
        }
    }

    private fun uniqueTargetFile(safeName: String): File {
        var candidate = File(receivedDir, safeName)
        if (!candidate.exists()) return candidate
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val ext = if (dot > 0) safeName.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(receivedDir, "${base}_$i$ext")
            i++
        }
        return candidate
    }

    private fun writeHttpResponse(out: OutputStream, code: Int, contentType: String, body: ByteArray) {
        val statusText = when (code) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            422 -> "Unprocessable Entity"
            else -> "Error"
        }
        val headers = "HTTP/1.1 $code $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Headers: Content-Type, X-Pair-Pin, X-File-Name, X-File-Sha256, X-Peer-Name\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun writeHttpDownload(out: OutputStream, contentType: String, fileName: String, body: ByteArray) {
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Content-Disposition: attachment; filename=\"$fileName\"\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    // ==========================================
    // NSD SERVICE REGISTRATION & DISCOVERY
    // ==========================================

    private fun registerNsdService(port: Int) {
        val manager = nsdManager ?: return
        unregisterNsdService()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "HotNote-${Build.MODEL}".take(32)
            serviceType = nsdServiceType
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching { manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun unregisterNsdService() {
        val manager = nsdManager ?: return
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching { manager.unregisterService(listener) }
    }

    override fun startLanDiscovery() {
        val manager = nsdManager ?: return
        stopLanDiscovery()
        _discoveredLanPeers.value = emptyList()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType != nsdServiceType) return
                runCatching {
                    @Suppress("DEPRECATION")
                    manager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = serviceInfo.host?.hostAddress ?: return
                            if (getLocalIpv4Addresses().contains(host) && serviceInfo.port == _serverState.value.port) {
                                return
                            }
                            val peer = P2pPeer(
                                id = "lan_${host}_${serviceInfo.port}",
                                name = serviceInfo.serviceName,
                                address = host,
                                port = serviceInfo.port,
                                transport = P2pTransport.LAN_WIFI,
                                platform = "LAN Peer"
                            )
                            _discoveredLanPeers.update { list ->
                                list.filterNot { it.address == host && it.port == serviceInfo.port } + peer
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                _discoveredLanPeers.update { list -> list.filterNot { it.name == service.serviceName } }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        runCatching { manager.discoverServices(nsdServiceType, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    override fun stopLanDiscovery() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { manager.stopServiceDiscovery(listener) }
    }

    // ==========================================
    // LAN CLIENT (SEND NOTE / FILE TO LAPTOP OR TABLET)
    // ==========================================

    override fun sendNoteToLanPeer(address: String, port: Int, pin: String, note: NoteEntity) {
        val cleanHost = address.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
        if (cleanHost.isBlank()) return
        val transferId = "tx_note_${System.currentTimeMillis()}"
        recordTransfer(
            P2pTransferItem(
                id = transferId,
                title = note.title,
                kind = "NOTE",
                sizeBytes = note.contentBlocks.length.toLong(),
                progress = 0.2f,
                direction = TransferDirection.SENT,
                status = TransferStatus.IN_PROGRESS,
                peerName = "$cleanHost:$port",
                message = "Sending note..."
            )
        )

        scope.launch {
            try {
                val payload = NoteTransferPayload(
                    id = note.id,
                    title = note.title,
                    contentBlocks = note.contentBlocks,
                    attachmentsJson = note.attachmentsJson,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    senderName = deviceName
                )
                val body = SafeNoteCodec.serializeNotePayload(payload).toByteArray(Charsets.UTF_8)
                val conn = (URL("http://$cleanHost:$port/api/notes").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("X-Pair-Pin", pin.trim())
                    setFixedLengthStreamingMode(body.size)
                }
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) {
                    updateTransferStatus(transferId, 1f, TransferStatus.COMPLETED, "Delivered to $cleanHost:$port")
                } else {
                    updateTransferStatus(transferId, 0f, TransferStatus.FAILED, "Peer returned HTTP $code")
                }
            } catch (e: Throwable) {
                updateTransferStatus(transferId, 0f, TransferStatus.FAILED, e.message ?: "Connection failed")
            }
        }
    }

    override fun sendFileToLanPeer(address: String, port: Int, pin: String, file: PickedFile) {
        val cleanHost = address.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
        if (cleanHost.isBlank()) return
        val transferId = "tx_file_${System.currentTimeMillis()}"
        recordTransfer(
            P2pTransferItem(
                id = transferId,
                title = file.name,
                kind = "FILE",
                sizeBytes = file.sizeBytes,
                progress = 0f,
                direction = TransferDirection.SENT,
                status = TransferStatus.IN_PROGRESS,
                peerName = "$cleanHost:$port",
                message = "Streaming file..."
            )
        )

        scope.launch {
            try {
                val conn = (URL("http://$cleanHost:$port/api/upload").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setRequestProperty("X-Pair-Pin", pin.trim())
                    setRequestProperty("X-File-Name", java.net.URLEncoder.encode(file.name, "UTF-8"))
                    setRequestProperty("X-File-Mime", file.mimeType)
                    setRequestProperty("X-Peer-Name", java.net.URLEncoder.encode(deviceName, "UTF-8"))
                    if (file.sizeBytes > 0L) {
                        setFixedLengthStreamingMode(file.sizeBytes)
                    } else {
                        setChunkedStreamingMode(65536)
                    }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var sentBytes = 0L
                openInputStream(file.uriOrPath).use { fis ->
                    conn.outputStream.use { out ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val r = fis.read(buf)
                            if (r <= 0) break
                            out.write(buf, 0, r)
                            digest.update(buf, 0, r)
                            sentBytes += r
                            if (file.sizeBytes > 0L) {
                                updateTransferProgress(transferId, (sentBytes.toFloat() / file.sizeBytes.toFloat()).coerceIn(0f, 0.99f))
                            }
                        }
                        out.flush()
                    }
                }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) {
                    repository.saveSharedFile(
                        id = transferId,
                        fileName = file.name,
                        filePath = file.uriOrPath,
                        mimeType = file.mimeType,
                        sizeBytes = sentBytes,
                        sha256 = digest.digest().toHex(),
                        direction = "SENT",
                        peerName = "$cleanHost:$port"
                    )
                    updateTransferStatus(transferId, 1f, TransferStatus.COMPLETED, "Sent (${formatBytes(sentBytes)})")
                } else {
                    updateTransferStatus(transferId, 0f, TransferStatus.FAILED, "Peer returned HTTP $code")
                }
            } catch (e: Throwable) {
                updateTransferStatus(transferId, 0f, TransferStatus.FAILED, e.message ?: "Upload failed")
            }
        }
    }

    // ==========================================
    // BLUETOOTH RFCOMM P2P (FIXED & FRAMED)
    // ==========================================

    private fun hasBluetoothPermissions(): Boolean {
        val connectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        return connectGranted
    }

    @SuppressLint("MissingPermission")
    override fun refreshPairedBluetoothDevices() {
        val adapter = btAdapter ?: return
        val hasPerm = hasBluetoothPermissions()
        if (!hasPerm) {
            _bluetoothState.update { it.copy(isEnabled = adapter.isEnabled, hasPermissions = false) }
            return
        }
        val bonded = runCatching {
            adapter.bondedDevices.orEmpty().map { dev ->
                P2pPeer(
                    id = "bt_${dev.address}",
                    name = dev.name ?: dev.address,
                    address = dev.address,
                    port = 0,
                    transport = P2pTransport.BLUETOOTH,
                    platform = "Paired BT"
                )
            }
        }.getOrDefault(emptyList())

        _bluetoothState.update {
            it.copy(
                isEnabled = adapter.isEnabled,
                hasPermissions = true,
                pairedDevices = bonded
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun startBluetoothDiscovery() {
        val adapter = btAdapter ?: return
        if (!hasBluetoothPermissions()) {
            requestBtPermissions()
            return
        }
        if (!adapter.isEnabled) {
            requestEnableBt()
            return
        }
        registerBtReceiverIfNeeded()
        refreshPairedBluetoothDevices()
        _bluetoothState.update { it.copy(discoveredDevices = emptyList(), isDiscovering = true, statusText = "Scanning Bluetooth...") }
        runCatching {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        }.onFailure { err ->
            _bluetoothState.update { it.copy(isDiscovering = false, statusText = err.message ?: "Scan failed") }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startBluetoothServer() {
        val adapter = btAdapter ?: return
        if (!hasBluetoothPermissions()) {
            requestBtPermissions()
            return
        }
        if (!adapter.isEnabled) {
            requestEnableBt()
            return
        }
        if (btServerJob?.isActive == true) return
        refreshPairedBluetoothDevices()

        btServerJob = scope.launch {
            var srv: BluetoothServerSocket? = null
            try {
                srv = adapter.listenUsingRfcommWithServiceRecord("HotNoteP2P", appUuid)
                btServerSocket = srv
                _bluetoothState.update { it.copy(isServerRunning = true, statusText = "BT listening for notes & files") }
                while (isActive) {
                    val socket = srv?.accept() ?: break
                    launch { handleBluetoothSocket(socket) }
                }
            } catch (e: Throwable) {
                if (isActive) {
                    _bluetoothState.update { it.copy(statusText = "BT server: ${e.message}") }
                }
            } finally {
                runCatching { srv?.close() }
                btServerSocket = null
                _bluetoothState.update { it.copy(isServerRunning = false, statusText = "BT server stopped") }
            }
        }
    }

    override fun stopBluetoothServer() {
        runCatching { btServerSocket?.close() }
        btServerSocket = null
        btServerJob?.cancel()
        btServerJob = null
        _bluetoothState.update { it.copy(isServerRunning = false, statusText = "BT server stopped") }
    }

    @SuppressLint("MissingPermission")
    private fun handleBluetoothSocket(socket: BluetoothSocket) {
        socket.use { sock ->
            val remoteName = runCatching { sock.remoteDevice?.name }.getOrNull() ?: "Bluetooth Peer"
            val din = DataInputStream(BufferedInputStream(sock.inputStream))
            val magic = ByteArray(4)
            din.readFully(magic)
            if (String(magic, Charsets.UTF_8) != "HNP2") return
            val headerLen = din.readInt().coerceIn(1, 262_144)
            val headerBytes = ByteArray(headerLen)
            din.readFully(headerBytes)
            val header = SimpleJson.parse(String(headerBytes, Charsets.UTF_8)) as? Map<*, *> ?: return

            val type = header["type"]?.toString() ?: "NOTE"
            val payloadSize = (header["sizeBytes"] as? Number)?.toLong() ?: 0L
            val senderName = header["senderName"]?.toString() ?: remoteName

            if (type == "NOTE") {
                if (payloadSize <= 0L || payloadSize > 5 * 1024 * 1024L) return
                val data = ByteArray(payloadSize.toInt())
                din.readFully(data)
                val payload = SafeNoteCodec.parseNotePayload(String(data, Charsets.UTF_8)) ?: return
                onNoteReceived(payload)
                recordTransfer(
                    P2pTransferItem(
                        id = "bt_rx_${System.currentTimeMillis()}",
                        title = payload.title,
                        kind = "NOTE",
                        sizeBytes = payloadSize,
                        progress = 1f,
                        direction = TransferDirection.RECEIVED,
                        status = TransferStatus.COMPLETED,
                        peerName = senderName,
                        message = "Received via Bluetooth"
                    )
                )
            } else if (type == "FILE" && payloadSize >= 0L) {
                val safeName = SafeNoteCodec.sanitizeFileName(header["fileName"]?.toString() ?: "bt_file")
                val target = uniqueTargetFile(safeName)
                val res = FileOutputStream(target).use { fos ->
                    P2pStreamCodec.copyFixedToStream(din, fos, payloadSize)
                }
                val fileId = "bt_file_${System.currentTimeMillis()}"
                kotlinx.coroutines.runBlocking {
                    repository.saveSharedFile(
                        id = fileId,
                        fileName = safeName,
                        filePath = target.absolutePath,
                        mimeType = header["mimeType"]?.toString() ?: guessMimeType(safeName),
                        sizeBytes = res.bytesWritten,
                        sha256 = res.sha256Hex,
                        direction = "RECEIVED",
                        peerName = senderName
                    )
                }
                recordTransfer(
                    P2pTransferItem(
                        id = fileId,
                        title = safeName,
                        kind = "FILE",
                        sizeBytes = res.bytesWritten,
                        progress = 1f,
                        direction = TransferDirection.RECEIVED,
                        status = TransferStatus.COMPLETED,
                        peerName = senderName,
                        message = "Received via Bluetooth"
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun sendNoteViaBluetooth(deviceAddress: String, note: NoteEntity) {
        val adapter = btAdapter ?: return
        if (!hasBluetoothPermissions()) {
            requestBtPermissions()
            return
        }
        val transferId = "bt_tx_note_${System.currentTimeMillis()}"
        recordTransfer(
            P2pTransferItem(
                id = transferId,
                title = note.title,
                kind = "NOTE",
                sizeBytes = note.contentBlocks.length.toLong(),
                progress = 0.2f,
                direction = TransferDirection.SENT,
                status = TransferStatus.IN_PROGRESS,
                peerName = deviceAddress,
                message = "Connecting Bluetooth..."
            )
        )
        scope.launch {
            var socket: BluetoothSocket? = null
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(deviceAddress)
                socket = device.createRfcommSocketToServiceRecord(appUuid)
                socket.connect()

                val payload = NoteTransferPayload(
                    id = note.id,
                    title = note.title,
                    contentBlocks = note.contentBlocks,
                    attachmentsJson = note.attachmentsJson,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    senderName = deviceName
                )
                val payloadBytes = SafeNoteCodec.serializeNotePayload(payload).toByteArray(Charsets.UTF_8)
                val headerBytes = SimpleJson.stringify(
                    mapOf(
                        "type" to "NOTE",
                        "sizeBytes" to payloadBytes.size.toLong(),
                        "senderName" to deviceName
                    )
                ).toByteArray(Charsets.UTF_8)

                val dout = DataOutputStream(BufferedOutputStream(socket.outputStream))
                dout.write("HNP2".toByteArray(Charsets.UTF_8))
                dout.writeInt(headerBytes.size)
                dout.write(headerBytes)
                dout.write(payloadBytes)
                dout.flush()
                updateTransferStatus(transferId, 1f, TransferStatus.COMPLETED, "Sent via Bluetooth")
            } catch (e: Throwable) {
                updateTransferStatus(transferId, 0f, TransferStatus.FAILED, e.message ?: "Bluetooth send failed")
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun sendFileViaBluetooth(deviceAddress: String, file: PickedFile) {
        val adapter = btAdapter ?: return
        if (!hasBluetoothPermissions()) {
            requestBtPermissions()
            return
        }
        val transferId = "bt_tx_file_${System.currentTimeMillis()}"
        recordTransfer(
            P2pTransferItem(
                id = transferId,
                title = file.name,
                kind = "FILE",
                sizeBytes = file.sizeBytes,
                progress = 0f,
                direction = TransferDirection.SENT,
                status = TransferStatus.IN_PROGRESS,
                peerName = deviceAddress,
                message = "Connecting Bluetooth..."
            )
        )
        scope.launch {
            var socket: BluetoothSocket? = null
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(deviceAddress)
                socket = device.createRfcommSocketToServiceRecord(appUuid)
                socket.connect()

                val actualSize = if (file.sizeBytes > 0L) file.sizeBytes else computeSize(file.uriOrPath)
                val headerBytes = SimpleJson.stringify(
                    mapOf(
                        "type" to "FILE",
                        "fileName" to file.name,
                        "mimeType" to file.mimeType,
                        "sizeBytes" to actualSize,
                        "senderName" to deviceName
                    )
                ).toByteArray(Charsets.UTF_8)

                val dout = DataOutputStream(BufferedOutputStream(socket.outputStream))
                dout.write("HNP2".toByteArray(Charsets.UTF_8))
                dout.writeInt(headerBytes.size)
                dout.write(headerBytes)
                val res = openInputStream(file.uriOrPath).use { fis ->
                    P2pStreamCodec.copyFixedToStream(fis, dout, actualSize) { sent ->
                        if (actualSize > 0L) {
                            updateTransferProgress(transferId, (sent.toFloat() / actualSize.toFloat()).coerceIn(0f, 0.99f))
                        }
                    }
                }
                repository.saveSharedFile(
                    id = transferId,
                    fileName = file.name,
                    filePath = file.uriOrPath,
                    mimeType = file.mimeType,
                    sizeBytes = res.bytesWritten,
                    sha256 = res.sha256Hex,
                    direction = "SENT",
                    peerName = deviceAddress
                )
                updateTransferStatus(transferId, 1f, TransferStatus.COMPLETED, "Sent via Bluetooth")
            } catch (e: Throwable) {
                updateTransferStatus(transferId, 0f, TransferStatus.FAILED, e.message ?: "Bluetooth file send failed")
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    // ==========================================
    // HELPERS & LIFECYCLE
    // ==========================================

    private fun registerBtReceiverIfNeeded() {
        if (btReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(btReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(btReceiver, filter)
        }
        btReceiverRegistered = true
    }

    override fun openReceivedFile(filePath: String, mimeType: String): Boolean {
        return try {
            val uri = if (filePath.startsWith("content://")) {
                Uri.parse(filePath)
            } else {
                val f = File(filePath)
                if (!f.exists()) return false
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", f)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun openInputStream(uriOrPath: String): InputStream {
        return if (uriOrPath.startsWith("content://")) {
            appContext.contentResolver.openInputStream(Uri.parse(uriOrPath))
                ?: throw IllegalStateException("Cannot open URI: $uriOrPath")
        } else {
            FileInputStream(File(uriOrPath))
        }
    }

    private fun computeSize(uriOrPath: String): Long {
        if (!uriOrPath.startsWith("content://")) return File(uriOrPath).length()
        var total = 0L
        openInputStream(uriOrPath).use { input ->
            val buf = ByteArray(16384)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                total += r
            }
        }
        return total
    }

    private fun getLocalIpv4Addresses(): List<String> {
        return runCatching {
            val result = mutableListOf<String>()
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { result.add(it) }
                    }
                }
            }
            result.distinct()
        }.getOrDefault(emptyList())
    }

    private fun recordTransfer(item: P2pTransferItem) {
        _transfers.update { list -> (listOf(item) + list).take(30) }
    }

    private fun updateTransferProgress(id: String, progress: Float) {
        _transfers.update { list ->
            list.map { if (it.id == id) it.copy(progress = progress) else it }
        }
    }

    private fun updateTransferStatus(id: String, progress: Float, status: TransferStatus, message: String) {
        _transfers.update { list ->
            list.map { if (it.id == id) it.copy(progress = progress, status = status, message = message) else it }
        }
    }

    private fun guessMimeType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun buildWebPortalHtml(): String {
        val safeName = SafeNoteCodec.escapeHtml(deviceName)
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Hot Note P2P — $safeName</title>
<style>
  :root { --purple:#7719AA; --bg:#F7F5FB; --card:#FFFFFF; --text:#1F1B24; --muted:#6B6575; }
  body { font-family: system-ui, -apple-system, sans-serif; background: var(--bg); color: var(--text); margin:0; padding:24px; }
  .wrap { max-width: 900px; margin: 0 auto; }
  header { background: var(--purple); color:#fff; padding:20px 24px; border-radius:14px; margin-bottom:20px; }
  h1 { margin:0 0 6px 0; font-size:22px; }
  .card { background: var(--card); border-radius:12px; padding:18px; margin-bottom:16px; box-shadow:0 2px 10px rgba(0,0,0,.06); }
  .row { display:flex; gap:10px; flex-wrap:wrap; align-items:center; }
  input, textarea, button { font:inherit; padding:10px 12px; border-radius:8px; border:1px solid #D8D2E3; }
  button { background: var(--purple); color:#fff; border:none; cursor:pointer; font-weight:600; }
  button.sec { background:#EFE8F8; color:var(--purple); }
  .item { display:flex; justify-content:space-between; align-items:center; padding:10px 0; border-bottom:1px solid #EEE9F5; }
  .muted { color:var(--muted); font-size:13px; }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <h1>Hot Note P2P Share — $safeName</h1>
    <div class="muted" style="color:#E9D5FF">Transfer notes &amp; files directly between your Laptop and Tablet over local Wi-Fi</div>
  </header>

  <div class="card">
    <div class="row">
      <label for="pin"><strong>Pairing PIN:</strong></label>
      <input id="pin" type="text" placeholder="Enter 6-digit PIN from tablet" />
      <button onclick="loadAll()">Connect &amp; Refresh</button>
      <span id="status" class="muted"></span>
    </div>
  </div>

  <div class="card">
    <h3>Send File to Tablet</h3>
    <div class="row">
      <input id="fileInput" type="file" />
      <button onclick="uploadFile()">Upload File</button>
      <span id="uploadStatus" class="muted"></span>
    </div>
  </div>

  <div class="card">
    <h3>Send Note to Tablet</h3>
    <input id="noteTitle" type="text" placeholder="Note Title" style="width:100%;box-sizing:border-box;margin-bottom:8px;" />
    <textarea id="noteBody" rows="4" placeholder="Write note content..." style="width:100%;box-sizing:border-box;margin-bottom:8px;"></textarea>
    <button onclick="sendNote()">Send Note</button>
    <span id="noteStatus" class="muted"></span>
  </div>

  <div class="card">
    <h3>Tablet Notes</h3>
    <div id="notesList" class="muted">Enter PIN and click Connect &amp; Refresh</div>
  </div>

  <div class="card">
    <h3>Shared Files</h3>
    <div id="filesList" class="muted">Enter PIN and click Connect &amp; Refresh</div>
  </div>
</div>

<script>
function pinHeader() { return { 'X-Pair-Pin': document.getElementById('pin').value.trim() }; }
function esc(s) {
  const d = document.createElement('div'); d.textContent = s ?? ''; return d.innerHTML;
}
async function loadAll() {
  const st = document.getElementById('status');
  st.textContent = 'Connecting...';
  const pin = encodeURIComponent(document.getElementById('pin').value.trim());
  const nr = await fetch('/api/notes', { headers: pinHeader() });
  if (!nr.ok) { st.textContent = 'Invalid PIN or error (' + nr.status + ')'; return; }
  const notes = await nr.json();
  document.getElementById('notesList').innerHTML = notes.length === 0 ? 'No notes yet.' : notes.map(n =>
    '<div class="item"><div><strong>' + esc(n.title) + '</strong><div class="muted">' + esc(n.preview) + '</div></div>' +
    '<div class="row"><a href="/api/notes/' + encodeURIComponent(n.id) + '?format=md&pin=' + pin + '"><button class="sec">Download .md</button></a>' +
    '<a href="/api/notes/' + encodeURIComponent(n.id) + '?download=1&pin=' + pin + '"><button class="sec">.json</button></a></div></div>'
  ).join('');

  const fr = await fetch('/api/files', { headers: pinHeader() });
  const files = await fr.json();
  document.getElementById('filesList').innerHTML = files.length === 0 ? 'No shared files yet.' : files.map(f =>
    '<div class="item"><div><strong>' + esc(f.fileName) + '</strong><div class="muted">' + esc(f.direction) + ' • ' + f.sizeBytes + ' bytes</div></div>' +
    '<a href="/api/files/' + encodeURIComponent(f.id) + '?pin=' + pin + '"><button class="sec">Download</button></a></div>'
  ).join('');
  st.textContent = 'Connected!';
}
async function uploadFile() {
  const f = document.getElementById('fileInput').files[0];
  if (!f) return;
  const st = document.getElementById('uploadStatus');
  st.textContent = 'Uploading ' + f.name + '...';
  const fd = new FormData(); fd.append('file', f);
  const res = await fetch('/api/upload', { method:'POST', headers: pinHeader(), body: fd });
  st.textContent = res.ok ? 'Uploaded!' : 'Failed (' + res.status + ')';
  if (res.ok) loadAll();
}
async function sendNote() {
  const title = document.getElementById('noteTitle').value.trim() || 'Laptop Note';
  const text = document.getElementById('noteBody').value;
  const st = document.getElementById('noteStatus');
  const res = await fetch('/api/notes', {
    method:'POST',
    headers: { ...pinHeader(), 'Content-Type':'application/json' },
    body: JSON.stringify({ title, contentBlocks: text, senderName: 'Laptop Browser' })
  });
  st.textContent = res.ok ? 'Sent to tablet!' : 'Failed (' + res.status + ')';
  if (res.ok) { document.getElementById('noteTitle').value=''; document.getElementById('noteBody').value=''; loadAll(); }
}
</script>
</body>
</html>
""".trimIndent()
    }

    override fun dispose() {
        stopLanServer()
        stopLanDiscovery()
        stopBluetoothServer()
        if (btReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(btReceiver) }
            btReceiverRegistered = false
        }
        scope.cancel()
    }
}

@Composable
actual fun rememberP2pController(
    repository: NoteRepository,
    onNoteReceived: (NoteTransferPayload) -> Unit
): P2pController {
    val context = LocalContext.current
    val latestOnNoteReceived by rememberUpdatedState(onNoteReceived)

    lateinit var controllerRef: AndroidP2pController

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        controllerRef.refreshPairedBluetoothDevices()
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        controllerRef.refreshPairedBluetoothDevices()
    }

    val controller = remember(repository) {
        AndroidP2pController(
            context = context,
            repository = repository,
            onNoteReceived = { payload -> latestOnNoteReceived(payload) },
            requestBtPermissions = {
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                permissionLauncher.launch(perms)
            },
            requestEnableBt = {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        ).also { controllerRef = it }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.dispose()
        }
    }

    return controller
}

@Composable
actual fun rememberFilePicker(
    onFilePicked: (PickedFile) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val latestCallback by rememberUpdatedState(onFilePicked)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            var name = "selected_file"
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            latestCallback(
                PickedFile(
                    name = name,
                    uriOrPath = uri.toString(),
                    mimeType = mime,
                    sizeBytes = size
                )
            )
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}
