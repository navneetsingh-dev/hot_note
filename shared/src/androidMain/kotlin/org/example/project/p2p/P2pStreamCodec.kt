package org.example.project.p2p

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Streaming HTTP & Multipart helpers so large file transfers use constant O(1) memory
 * and compute SHA-256 on the fly without buffering whole files in RAM.
 */
object P2pStreamCodec {

    data class HttpRequestHead(
        val method: String,
        val path: String,
        val queryParams: Map<String, String>,
        val headers: Map<String, String>
    )

    data class StreamResult(
        val bytesWritten: Long,
        val sha256Hex: String
    )

    fun readHttpRequestHead(input: BufferedInputStream): HttpRequestHead? {
        val line = readAsciiLine(input) ?: return null
        if (line.isBlank()) return null
        val parts = line.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val rawTarget = parts[1]
        val qIdx = rawTarget.indexOf('?')
        val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
        val queryParams = if (qIdx >= 0 && qIdx + 1 < rawTarget.length) {
            parseQueryString(rawTarget.substring(qIdx + 1))
        } else {
            emptyMap()
        }

        val headers = linkedMapOf<String, String>()
        while (true) {
            val hLine = readAsciiLine(input) ?: break
            if (hLine.isEmpty()) break
            val colonIdx = hLine.indexOf(':')
            if (colonIdx > 0) {
                val key = hLine.substring(0, colonIdx).trim().lowercase()
                val value = hLine.substring(colonIdx + 1).trim()
                headers[key] = value
            }
        }
        return HttpRequestHead(method = method, path = path, queryParams = queryParams, headers = headers)
    }

    fun readAsciiLine(input: InputStream, maxLength: Int = 16384): String? {
        val baos = ByteArrayOutputStream()
        var prevWasCr = false
        while (baos.size() < maxLength) {
            val b = input.read()
            if (b == -1) {
                return if (baos.size() == 0) null else baos.toString(Charsets.UTF_8.name())
            }
            if (b == '\n'.code) {
                val bytes = baos.toByteArray()
                val len = if (prevWasCr && bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) {
                    bytes.size - 1
                } else {
                    bytes.size
                }
                return String(bytes, 0, len, Charsets.UTF_8)
            }
            prevWasCr = (b == '\r'.code)
            baos.write(b)
        }
        return baos.toString(Charsets.UTF_8.name())
    }

    fun parseQueryString(query: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (pair in query.split('&')) {
            if (pair.isBlank()) continue
            val eqIdx = pair.indexOf('=')
            if (eqIdx >= 0) {
                val k = urlDecode(pair.substring(0, eqIdx))
                val v = urlDecode(pair.substring(eqIdx + 1))
                result[k] = v
            } else {
                result[urlDecode(pair)] = ""
            }
        }
        return result
    }

    fun urlDecode(s: String): String {
        return try {
            java.net.URLDecoder.decode(s, "UTF-8")
        } catch (_: Throwable) {
            s
        }
    }

    fun readFixedBytes(input: InputStream, length: Int, maxAllowed: Int = 8 * 1024 * 1024): ByteArray {
        val clamped = length.coerceIn(0, maxAllowed)
        val out = ByteArray(clamped)
        var offset = 0
        while (offset < clamped) {
            val read = input.read(out, offset, clamped - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == clamped) out else out.copyOf(offset)
    }

    fun copyFixedToStream(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: ((Long) -> Unit)? = null
    ): StreamResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        var remaining = totalBytes
        var written = 0L
        while (remaining > 0L) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            remaining -= read
            written += read
            onProgress?.invoke(written)
        }
        output.flush()
        return StreamResult(written, digest.digest().toHex())
    }

    /**
     * Streams the first file part from a multipart/form-data body directly into [output]
     * without buffering the file in memory.
     * Returns Pair(originalFileName, StreamResult) or null if no file part was found.
     */
    fun streamMultipartFile(
        input: InputStream,
        boundary: String,
        contentLength: Long,
        outputProvider: (fileName: String) -> OutputStream
    ): Pair<String, StreamResult>? {
        val bounded = BoundedInputStream(input, contentLength)
        val boundaryBytes = "--$boundary".toByteArray(Charsets.UTF_8)
        val nextBoundaryMarker = "\r\n--$boundary".toByteArray(Charsets.UTF_8)

        // Advance past preamble to first boundary
        while (true) {
            val line = readAsciiLine(bounded) ?: return null
            if (line == "--$boundary") break
            if (line == "--$boundary--") return null
        }

        while (true) {
            // Read part headers
            val partHeaders = linkedMapOf<String, String>()
            while (true) {
                val hLine = readAsciiLine(bounded) ?: return null
                if (hLine.isEmpty()) break
                val colon = hLine.indexOf(':')
                if (colon > 0) {
                    partHeaders[hLine.substring(0, colon).trim().lowercase()] = hLine.substring(colon + 1).trim()
                }
            }

            val disposition = partHeaders["content-disposition"].orEmpty()
            val fileName = extractDispositionParam(disposition, "filename")
            if (fileName != null) {
                val out = outputProvider(fileName)
                val digest = MessageDigest.getInstance("SHA-256")
                var totalWritten = 0L

                // Sliding buffer to detect "\r\n--$boundary" across read chunks
                val tailLen = nextBoundaryMarker.size
                val buf = ByteArray(65536 + tailLen)
                var valid = 0
                out.use { stream ->
                    while (true) {
                        val read = bounded.read(buf, valid, 65536)
                        if (read <= 0) {
                            if (valid > 0) {
                                stream.write(buf, 0, valid)
                                digest.update(buf, 0, valid)
                                totalWritten += valid
                            }
                            break
                        }
                        val total = valid + read
                        val idx = indexOfSubarray(buf, total, nextBoundaryMarker)
                        if (idx >= 0) {
                            if (idx > 0) {
                                stream.write(buf, 0, idx)
                                digest.update(buf, 0, idx)
                                totalWritten += idx
                            }
                            break
                        } else {
                            val safeFlush = (total - tailLen).coerceAtLeast(0)
                            if (safeFlush > 0) {
                                stream.write(buf, 0, safeFlush)
                                digest.update(buf, 0, safeFlush)
                                totalWritten += safeFlush
                                System.arraycopy(buf, safeFlush, buf, 0, total - safeFlush)
                                valid = total - safeFlush
                            } else {
                                valid = total
                            }
                        }
                    }
                    stream.flush()
                }
                return fileName to StreamResult(totalWritten, digest.digest().toHex())
            } else {
                // Skip non-file form field until next boundary
                if (!skipUntilBoundary(bounded, boundaryBytes)) return null
            }
        }
    }

    private fun skipUntilBoundary(input: InputStream, boundaryBytes: ByteArray): Boolean {
        while (true) {
            val line = readAsciiLine(input) ?: return false
            if (line.startsWith(String(boundaryBytes, Charsets.UTF_8))) {
                return !line.endsWith("--")
            }
        }
    }

    private fun extractDispositionParam(disposition: String, key: String): String? {
        val pattern = Regex("""$key="([^"]*)"""")
        return pattern.find(disposition)?.groupValues?.getOrNull(1)
    }

    fun extractBoundary(contentType: String?): String? {
        if (contentType == null) return null
        val match = Regex("""boundary=(?:"([^"]+)"|([^;\s]+))""", RegexOption.IGNORE_CASE).find(contentType)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
            ?: match?.groupValues?.getOrNull(2)?.takeIf { it.isNotEmpty() }
    }

    private fun indexOfSubarray(haystack: ByteArray, haystackLen: Int, needle: ByteArray): Int {
        if (needle.isEmpty() || haystackLen < needle.size) return -1
        outer@ for (i in 0..(haystackLen - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0L) return -1
            val b = delegate.read()
            if (b != -1) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val n = delegate.read(b, off, toRead)
            if (n > 0) remaining -= n
            return n
        }
    }
}
