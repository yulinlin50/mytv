package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

internal object IjkTsAudioLanguageDetector {

    private const val PLAYLIST_PROBE_BYTES = 64 * 1024
    private const val TS_PROBE_BYTES = 512 * 1024
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    suspend fun detectAudioLanguages(url: String, headers: Map<String, String> = emptyMap()): List<String?> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val responseBytes = fetchBytes(url, headers, PLAYLIST_PROBE_BYTES)
                if (responseBytes.isEmpty()) return@runCatching emptyList()

                if (looksLikeM3u8(responseBytes)) {
                    val segmentUrl = extractFirstSegmentUrl(url, responseBytes) ?: return@runCatching emptyList()
                    parseAudioLanguagesFromTsData(fetchBytes(segmentUrl, headers, TS_PROBE_BYTES))
                } else {
                    parseAudioLanguagesFromTsData(responseBytes)
                }
            }.getOrDefault(emptyList())
        }
    }

    internal fun parseAudioLanguagesFromTsData(data: ByteArray): List<String?> {
        val packetSize = detectPacketSize(data) ?: return emptyList()
        val patAssembler = PsiSectionAssembler()
        val pmtAssemblers = mutableMapOf<Int, PsiSectionAssembler>()
        var pmtPids = emptySet<Int>()

        var offset = 0
        while (offset + packetSize <= data.size) {
            if (data[offset] != SYNC_BYTE) {
                offset += packetSize
                continue
            }

            val payloadUnitStart = (data[offset + 1].toInt() and 0x40) != 0
            val pid = ((data[offset + 1].toInt() and 0x1F) shl 8) or (data[offset + 2].toInt() and 0xFF)
            val adaptationFieldControl = (data[offset + 3].toInt() ushr 4) and 0x03

            if (adaptationFieldControl == 0 || adaptationFieldControl == 2) {
                offset += packetSize
                continue
            }

            var payloadOffset = offset + 4
            if (adaptationFieldControl == 3) {
                val adaptationLength = data[payloadOffset].toInt() and 0xFF
                payloadOffset += 1 + adaptationLength
            }

            if (payloadOffset >= offset + packetSize) {
                offset += packetSize
                continue
            }

            val payloadEnd = offset + packetSize
            if (pid == PAT_PID) {
                patAssembler.append(data, payloadOffset, payloadEnd, payloadUnitStart)?.let { section ->
                    pmtPids = parsePat(section).toSet()
                }
            } else if (pid in pmtPids) {
                val assembler = pmtAssemblers.getOrPut(pid) { PsiSectionAssembler() }
                assembler.append(data, payloadOffset, payloadEnd, payloadUnitStart)?.let { section ->
                    return parsePmtAudioLanguages(section)
                }
            }

            offset += packetSize
        }

        return emptyList()
    }

    private fun fetchBytes(url: String, headers: Map<String, String>, maxBytes: Int): ByteArray {
        val connection = URL(url).openConnection()
        if (connection is HttpURLConnection) {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
        }
        headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        connection.getInputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (output.size() < maxBytes) {
                val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
                if (read <= 0) break
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun looksLikeM3u8(data: ByteArray): Boolean {
        val text = data
            .copyOf(minOf(data.size, 1024))
            .toString(StandardCharsets.UTF_8)
            .trimStart('\uFEFF', '\u0000', '\r', '\n', ' ', '\t')
        return text.startsWith("#EXTM3U")
    }

    private fun extractFirstSegmentUrl(playlistUrl: String, data: ByteArray): String? {
        val playlistText = data.toString(StandardCharsets.UTF_8)
        val baseUri = URI(playlistUrl)
        return playlistText
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?.let { segment ->
                runCatching { baseUri.resolve(segment).toString() }.getOrNull()
            }
    }

    private fun detectPacketSize(data: ByteArray): Int? {
        val candidates = listOf(188, 192, 204)
        return candidates.firstOrNull { size ->
            data.size >= size * 2 &&
                data[0] == SYNC_BYTE &&
                data.getOrNull(size) == SYNC_BYTE &&
                (data.size < size * 3 || data.getOrNull(size * 2) == SYNC_BYTE)
        }
    }

    private fun parsePat(section: ByteArray): List<Int> {
        if (section.size < 12 || section[0].toInt() != 0x00) return emptyList()

        val sectionLength = ((section[1].toInt() and 0x0F) shl 8) or (section[2].toInt() and 0xFF)
        val sectionEnd = minOf(section.size, 3 + sectionLength) - CRC_LENGTH
        val pmtPids = mutableListOf<Int>()
        var position = 8

        while (position + 4 <= sectionEnd) {
            val programNumber = ((section[position].toInt() and 0xFF) shl 8) or (section[position + 1].toInt() and 0xFF)
            val pid = ((section[position + 2].toInt() and 0x1F) shl 8) or (section[position + 3].toInt() and 0xFF)
            if (programNumber != 0) {
                pmtPids += pid
            }
            position += 4
        }

        return pmtPids
    }

    private fun parsePmtAudioLanguages(section: ByteArray): List<String?> {
        if (section.size < 16 || section[0].toInt() != 0x02) return emptyList()

        val sectionLength = ((section[1].toInt() and 0x0F) shl 8) or (section[2].toInt() and 0xFF)
        val sectionEnd = minOf(section.size, 3 + sectionLength) - CRC_LENGTH
        val programInfoLength = ((section[10].toInt() and 0x0F) shl 8) or (section[11].toInt() and 0xFF)
        val languages = mutableListOf<String?>()
        var position = 12 + programInfoLength

        while (position + 5 <= sectionEnd) {
            val streamType = section[position].toInt() and 0xFF
            val esInfoLength = ((section[position + 3].toInt() and 0x0F) shl 8) or (section[position + 4].toInt() and 0xFF)
            val descriptorStart = position + 5
            val descriptorEnd = minOf(descriptorStart + esInfoLength, sectionEnd)

            val language = extractIso639Language(section, descriptorStart, descriptorEnd)
            if (language != null || isLikelyAudioStream(streamType)) {
                languages += language
            }

            position = descriptorEnd
        }

        return languages
    }

    private fun extractIso639Language(section: ByteArray, start: Int, end: Int): String? {
        var position = start
        while (position + 2 <= end) {
            val tag = section[position].toInt() and 0xFF
            val length = section[position + 1].toInt() and 0xFF
            val descriptorEnd = position + 2 + length
            if (descriptorEnd > end) return null

            if (tag == ISO_639_DESCRIPTOR && length >= 4) {
                val languageBytes = byteArrayOf(section[position + 2], section[position + 3], section[position + 4])
                val language = languageBytes.toString(StandardCharsets.US_ASCII).trim()
                return language.takeIf { it.length == 3 && it.all { ch -> ch.isLetter() } }?.lowercase()
            }

            position = descriptorEnd
        }

        return null
    }

    private fun isLikelyAudioStream(streamType: Int): Boolean {
        return streamType in setOf(
            0x03, // MPEG1 Audio
            0x04, // MPEG2 Audio
            0x0F, // AAC
            0x11, // LATM AAC
            0x81, // AC3
            0x83, // LPCM
            0x84, // G.711
            0x87, // E-AC3
        )
    }

    private class PsiSectionAssembler {
        private var buffer = ByteArrayOutputStream()
        private var expectedLength = -1

        fun append(
            data: ByteArray,
            start: Int,
            end: Int,
            payloadUnitStart: Boolean,
        ): ByteArray? {
            var payloadStart = start
            if (payloadUnitStart) {
                if (payloadStart >= end) return null
                val pointerField = data[payloadStart].toInt() and 0xFF
                payloadStart += 1 + pointerField
                buffer = ByteArrayOutputStream()
                expectedLength = -1
            }

            if (payloadStart >= end) return null

            buffer.write(data, payloadStart, end - payloadStart)
            val sectionBytes = buffer.toByteArray()
            if (sectionBytes.size >= 3 && expectedLength < 0) {
                val sectionLength = ((sectionBytes[1].toInt() and 0x0F) shl 8) or (sectionBytes[2].toInt() and 0xFF)
                expectedLength = 3 + sectionLength
            }

            return if (expectedLength > 0 && sectionBytes.size >= expectedLength) {
                sectionBytes.copyOf(expectedLength)
            } else {
                null
            }
        }
    }

    private const val SYNC_BYTE: Byte = 0x47
    private const val PAT_PID = 0
    private const val ISO_639_DESCRIPTOR = 0x0A
    private const val CRC_LENGTH = 4
}
