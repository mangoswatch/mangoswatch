package org.mangoswatch.domain.parser.handshake.quic

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class QuicParserTest {

    @Test
    fun testLooksLikeQuicInitialValid() {
        val buf = ByteArray(1200)
        buf[0] = 0xC0.toByte() // long header, type Initial
        buf[1] = 0x00
        buf[2] = 0x00
        buf[3] = 0x00
        buf[4] = 0x01 // version 1
        assertTrue(looksLikeQuicInitial(buf, 0, 1200))
    }

    @Test
    fun testLooksLikeQuicInitialInvalidLength() {
        val buf = ByteArray(1000)
        buf[0] = 0xC0.toByte()
        buf[1] = 0x00
        buf[2] = 0x00
        buf[3] = 0x00
        buf[4] = 0x01
        assertFalse(looksLikeQuicInitial(buf, 0, 1000))
    }

    @Test
    fun testLooksLikeQuicInitialInvalidHeader() {
        val buf = ByteArray(1200)
        buf[0] = 0x80.toByte() // wrong header flag
        assertFalse(looksLikeQuicInitial(buf, 0, 1200))
    }

    @Test
    fun testDeriveClientInitialKeys() {
        val dcid = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val keys = deriveClientInitialKeys(dcid)
        assertNotNull(keys)
        assertEquals(16, keys.key.size)
        assertEquals(12, keys.iv.size)
        assertEquals(16, keys.hp.size)
    }

    @Test
    fun testDeriveClientInitialKeysRfc9001Vector() {
        val dcidHex = "8394c8f03e515708"
        val expectedKeyHex = "1f369613dd76d5467730efcbe3b1a22d"
        val expectedIvHex = "fa044b2f42a3fd3b46fb255c"
        val expectedHpHex = "9f50449e04a0e810283a1e9933adedd2"

        val dcid = dcidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val keys = deriveClientInitialKeys(dcid)

        assertEquals(expectedKeyHex, keys.key.joinToString("") { "%02x".format(it) })
        assertEquals(expectedIvHex, keys.iv.joinToString("") { "%02x".format(it) })
        assertEquals(expectedHpHex, keys.hp.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun testOpenQuicPacketCorrupted() {
        val buf = ByteArray(1200)
        buf[0] = 0xC0.toByte()
        buf[1] = 0x00
        buf[2] = 0x00
        buf[3] = 0x00
        buf[4] = 0x01 // version 1
        buf[5] = 0x08 // DCID len = 8
        // Corrupted payload / invalid MAC
        val opened = openQuicPacket(buf, 0, 1200, null, null) { _, _ -> }
        assertNull(opened)
    }

    @Test
    fun testOpenQuicPacketValid() {
        // Construct a syntactically and cryptographically valid QUIC Initial packet
        val dcid = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val scid = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val keys = deriveClientInitialKeys(dcid)

        val plaintextPayload = byteArrayOf(0x00, 0x00, 0x00) // PADDING frames
        val pn = 0
        val pnLen = 1

        val hdrUnprotected = mutableListOf<Byte>()
        hdrUnprotected.add((0xC0 or (pnLen - 1)).toByte()) // First byte (Initial)
        hdrUnprotected.addAll(listOf(0, 0, 0, 1)) // Version 1
        hdrUnprotected.add(dcid.size.toByte())
        hdrUnprotected.addAll(dcid.toList())
        hdrUnprotected.add(scid.size.toByte())
        hdrUnprotected.addAll(scid.toList())
        hdrUnprotected.add(0) // Token length = 0

        val payloadLen = pnLen + plaintextPayload.size + 16 // PN + plaintext + GCM tag
        hdrUnprotected.add(payloadLen.toByte()) // Length as varint (single byte)

        val pnOff = hdrUnprotected.size
        hdrUnprotected.add(pn.toByte()) // Packet number

        val headerBytes = hdrUnprotected.toByteArray()

        // Encrypt payload with AES-GCM
        val nonce = keys.iv.copyOf()
        nonce[12 - 1] = (nonce[12 - 1].toInt() xor pn).toByte()
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.key, "AES"), GCMParameterSpec(128, nonce))
        gcm.updateAAD(headerBytes)
        val ciphertext = gcm.doFinal(plaintextPayload)

        val fullPacket = headerBytes + ciphertext

        // Apply Header Protection (AES-ECB)
        val sampleOff = pnOff + 4
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.hp, "AES"))
        val mask = ecb.doFinal(fullPacket, sampleOff, 16)

        val protectedPacket = fullPacket.copyOf()
        protectedPacket[0] = (protectedPacket[0].toInt() xor (mask[0].toInt() and 0x0F)).toByte()
        for (i in 0 until pnLen) {
            protectedPacket[pnOff + i] = (protectedPacket[pnOff + i].toInt() xor mask[1 + i].toInt()).toByte()
        }

        var updateCalled = false
        val opened = openQuicPacket(protectedPacket, 0, protectedPacket.size, null, null) { d, k ->
            assertArrayEquals(dcid, d)
            assertNotNull(k)
            updateCalled = true
        }

        assertNotNull(opened)
        assertTrue(updateCalled)
        assertArrayEquals(plaintextPayload, opened?.frames)
    }
}
