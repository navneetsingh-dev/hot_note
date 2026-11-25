package org.example.project

import org.example.project.p2p.P2pStreamCodec
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class P2pStreamCodecTest {

    @Test
    fun parsesHttpRequestHeadWithQueryAndHeaders() {
        val raw = (
            "POST /api/upload?pin=123456&name=test%20file.pdf HTTP/1.1\r\n" +
                "Host: 192.168.1.10:8765\r\n" +
                "X-Pair-Pin: 123456\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n" +
                "hello"
            ).toByteArray(Charsets.UTF_8)

        val bis = BufferedInputStream(ByteArrayInputStream(raw))
        val head = P2pStreamCodec.readHttpRequestHead(bis)
        assertNotNull(head)
        assertEquals("POST", head.method)
        assertEquals("/api/upload", head.path)
        assertEquals("123456", head.queryParams["pin"])
        assertEquals("test file.pdf", head.queryParams["name"])
        assertEquals("123456", head.headers["x-pair-pin"])

        val body = P2pStreamCodec.readFixedBytes(bis, 5)
        assertEquals("hello", String(body, Charsets.UTF_8))
    }

    @Test
    fun streamsMultipartFileAndComputesAccurateSha256() {
        val boundary = "----HotNoteBoundary123"
        val fileBytes = ByteArray(150_000) { (it % 251).toByte() }
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(fileBytes)
            .joinToString("") { "%02x".format(it) }

        val partPrefix = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"arch_notes.bin\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val partSuffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        val fullBody = partPrefix + fileBytes + partSuffix
        val outputBuffer = ByteArrayOutputStream()

        val result = P2pStreamCodec.streamMultipartFile(
            input = ByteArrayInputStream(fullBody),
            boundary = boundary,
            contentLength = fullBody.size.toLong()
        ) { fileName ->
            assertEquals("arch_notes.bin", fileName)
            outputBuffer
        }

        assertNotNull(result)
        assertEquals("arch_notes.bin", result.first)
        assertEquals(fileBytes.size.toLong(), result.second.bytesWritten)
        assertEquals(expectedHash, result.second.sha256Hex)
        assertEquals(fileBytes.toList(), outputBuffer.toByteArray().toList())
    }
}
