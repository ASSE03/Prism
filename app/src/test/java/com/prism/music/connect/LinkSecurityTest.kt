package com.prism.music.connect

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * The Prism link's security (1.8.0, plan §4): X25519 against RFC 7748's own numbers, pairing by code (a wrong code is
 * refused and nothing is kept), every message sealed (a captured session shows nothing readable), and a replayed or
 * altered message refused.
 */
class LinkSecurityTest {
    private fun unhex(s: String) = LinkKeys.unhex(s)!!

    @Test fun x25519MatchesRfc7748() {
        // RFC 7748 §5.2, the first test vector.
        val out = X25519.scalarMult(
            unhex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4"),
            unhex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c"),
        )
        assertEquals("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552", LinkKeys.hex(out))
        // RFC 7748 §6.1: Alice and Bob.
        val alice = unhex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bob = unhex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", LinkKeys.hex(X25519.publicKey(alice)))
        assertEquals("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f", LinkKeys.hex(X25519.publicKey(bob)))
        val shared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
        assertEquals(shared, LinkKeys.hex(X25519.shared(alice, X25519.publicKey(bob))!!))
        assertEquals(shared, LinkKeys.hex(X25519.shared(bob, X25519.publicKey(alice))!!))
        // A key that would make the secret all zeros (a point of small order) is refused.
        assertNull(X25519.shared(alice, ByteArray(32)))
    }

    @Test fun sealedMessagesOpenOnlyOnceAndInOrder() {
        val a = LinkKeys.nonce() + LinkKeys.nonce()
        val b = LinkKeys.nonce() + LinkKeys.nonce()
        val phone = SealedChannel(sendKey = a, receiveKey = b)
        val tv = SealedChannel(sendKey = b, receiveKey = a)
        val one = phone.seal("first".toByteArray())
        val two = phone.seal("second".toByteArray())
        // Out of order: refused.
        assertNull(tv.open(two))
        assertArrayEquals("first".toByteArray(), tv.open(one))
        // Played again: refused.
        assertNull(tv.open(one))
        // Altered: refused (and the real one still opens after).
        assertNull(tv.open(two.copyOf().also { it[3] = (it[3].toInt() xor 1).toByte() }))
        assertArrayEquals("second".toByteArray(), tv.open(two))
        // The other way uses the other key.
        assertArrayEquals("back".toByteArray(), phone.open(tv.seal("back".toByteArray())))
    }

    @Test fun eachCodeBitIsCommittedBeforeItsShown() {
        val own = X25519.publicKey(X25519.newPrivateKey())
        val other = X25519.publicKey(X25519.newPrivateKey())
        val n = LinkKeys.nonce()
        val c = Passkey.commit(n, "c", own, other, 1)
        assertTrue(Passkey.opens(c, n, "c", own, other, 1))
        // The wrong bit, another number, other keys or the other side's role don't open it.
        assertTrue(!Passkey.opens(c, n, "c", own, other, 0))
        assertTrue(!Passkey.opens(c, LinkKeys.nonce(), "c", own, other, 1))
        assertTrue(!Passkey.opens(c, n, "c", other, own, 1))
        assertTrue(!Passkey.opens(c, n, "s", own, other, 1))
        // Twenty bits cover every six-digit code.
        assertTrue(999_999 < (1 shl Passkey.ROUNDS))
    }

    // ------------------------------------------------------------ Two Prisms over a real connection

    private class Device(override val name: String, override val device: String, override val isTv: Boolean = false) : HandoffLocal {
        val secrets = ConcurrentHashMap<String, String>()
        @Volatile var code: String? = null
        @Volatile var open = true
        val signIns = CopyOnWriteArrayList<Map<String, String>>()
        val keys = CopyOnWriteArrayList<RemoteKey>()
        override fun secretFor(device: String) = secrets[device]
        override fun savePairing(device: String, name: String, secret: String) { secrets[device] = secret }
        override fun forget(device: String) { secrets.remove(device) }
        override fun showCode(code: String?, from: String) = Unit
        override fun pairingCode(from: String): String? = if (open) code else null
        override fun snapshot(): HandoffSnapshot? = null
        override fun takeOver(s: HandoffSnapshot, from: String): String? = null
        override fun stepAside() = Unit
        override fun remote(key: RemoteKey, value: Long): String? { keys += key; return null }
        override fun nowPlaying() = RemoteNow("Glass Horizon", "Mira Vale", null, true, 84_000, 221_000, 40)
        override fun signIn(values: Map<String, String>, account: String, from: String): String? { signIns += values; return null }
    }

    /** Everything that crossed the wire, both ways. */
    private class Wire {
        val bytes = ByteArrayOutputStream()
        fun tap(s: Socket): Socket = object : Socket() {
            override fun getInputStream(): InputStream = object : FilterInputStream(s.getInputStream()) {
                override fun read(): Int = super.read().also { if (it >= 0) synchronized(bytes) { bytes.write(it) } }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) synchronized(bytes) { bytes.write(b, off, it) } }
            }
            override fun getOutputStream(): OutputStream = object : FilterOutputStream(s.getOutputStream()) {
                override fun write(b: Int) { synchronized(bytes) { bytes.write(b) }; out.write(b) }
                override fun write(b: ByteArray, off: Int, len: Int) { synchronized(bytes) { bytes.write(b, off, len) }; out.write(b, off, len) }
            }
            override fun setSoTimeout(timeout: Int) { s.soTimeout = timeout }
            override fun getInetAddress(): InetAddress? = s.inetAddress
            override fun close() = s.close()
        }
        override fun toString() = synchronized(bytes) { bytes.toString(Charsets.UTF_8) }
    }

    private fun <T> session(receiver: Device, wire: Wire? = null, run: (HLink) -> T): T {
        val server = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val t = thread { runCatching { server.accept().use { s -> HLink(s).use { HandoffPeer(receiver).serve(it) } } } }
        try {
            val socket = Socket(InetAddress.getLoopbackAddress(), server.localPort)
            return HLink(wire?.tap(socket) ?: socket).use(run)
        } finally {
            t.join(10_000)
            server.close()
        }
    }

    @Test fun aPhoneSignsInATvAndNothingIsReadableOnTheWire() {
        val tv = Device("Living room TV", "tv-1", isTv = true).apply { code = "482913" }
        val phone = Device("Alex's Pixel", "phone-1")
        val cookie = "SAPISID=secret-sapisid-value; SID=very-secret-sid"
        val wire = Wire()
        // Pair (the code typed on the phone), then the sign-in, on one connection.
        val r = session(tv, wire) { link -> HandoffPeer(phone).request(link, HandoffRequest.SignIn(mapOf("cookie" to "s:$cookie"), "alex@example.com")) { tv.code } }
        assertEquals(HandoffResult.Done(), r)
        assertEquals("s:$cookie", tv.signIns.single()["cookie"])
        // Both worked out the same key; it never crossed the wire.
        val key = phone.secrets["tv-1"]
        assertNotNull(key)
        assertEquals(key, tv.secrets["phone-1"])
        val seen = wire.toString()
        assertTrue("the key went across", !seen.contains(key!!))
        // Neither the code nor anything of the sign-in is readable.
        for (secret in listOf("482913", "SAPISID", "secret-sapisid-value", "very-secret-sid", "alex@example.com", "sign_in", "cookie")) {
            assertTrue("\"$secret\" readable on the wire", !seen.contains(secret))
        }

        // Later: the phone as the remote, no code, and what's playing comes back.
        val wire2 = Wire()
        assertEquals(HandoffResult.Done(), session(tv, wire2) { link -> HandoffPeer(phone).request(link, HandoffRequest.Remote(RemoteKey.TOGGLE)) { error("no code") } })
        assertEquals(RemoteKey.TOGGLE, tv.keys.single())
        val now = session(tv) { link -> HandoffPeer(phone).request(link, HandoffRequest.Now) { error("no code") } } as HandoffResult.Playing
        assertEquals("Glass Horizon", now.now?.title)
        assertTrue(!wire2.toString().contains("TOGGLE"))
    }

    @Test fun aWrongCodeIsRefusedAndNothingIsKept() {
        val tv = Device("TV", "tv-1", isTv = true).apply { code = "482913" }
        val phone = Device("Phone", "phone-1")
        val r = session(tv) { link -> HandoffPeer(phone).request(link, HandoffRequest.Pair) { "482914" } }
        assertTrue("$r", r is HandoffResult.Failed)
        assertTrue(phone.secrets.isEmpty() && tv.secrets.isEmpty())
        // A TV whose pairing screen is closed takes no pairing at all.
        tv.open = false
        val closed = session(tv) { link -> HandoffPeer(phone).request(link, HandoffRequest.Pair) { "482913" } }
        assertTrue("$closed", closed is HandoffResult.Failed)
        assertTrue(tv.secrets.isEmpty())
    }

    @Test fun aKeyFromBeforeOrMadeUpIsRefused() {
        val tv = Device("TV", "tv-1", isTv = true).apply { code = "111222" }
        val phone = Device("Phone", "phone-1")
        assertEquals(HandoffResult.Done(), session(tv) { link -> HandoffPeer(phone).request(link, HandoffRequest.Pair) { tv.code } })
        // The phone's key swapped for another: the TV refuses it, and the phone forgets the pairing (to pair again).
        phone.secrets["tv-1"] = LinkKeys.hex(LinkKeys.nonce() + LinkKeys.nonce())
        assertEquals(HandoffResult.Unpaired, session(tv) { link -> HandoffPeer(phone).request(link, HandoffRequest.Remote(RemoteKey.NEXT)) { null } })
        assertTrue(phone.secrets.isEmpty())
        assertTrue(tv.keys.isEmpty())
        // A version 1 Prism (no keys in its hello) is told both need the latest.
        val old = session(tv) { link ->
            link.send(HMsg.Hello("Old phone", "old-1", version = 1))
            link.read()
        }
        assertTrue("$old", old is HMsg.Denied)
    }

    @Test fun aReplayedSessionGetsNowhere() {
        val tv = Device("TV", "tv-1", isTv = true).apply { code = "123456" }
        val phone = Device("Phone", "phone-1")
        assertEquals(HandoffResult.Done(), session(tv) { link -> HandoffPeer(phone).request(link, HandoffRequest.Pair) { tv.code } })
        // Record a whole remote press, then play the caller's lines back to the TV on a new connection.
        val wire = Wire()
        session(tv, wire) { link -> HandoffPeer(phone).request(link, HandoffRequest.Remote(RemoteKey.NEXT)) { null } }
        assertEquals(1, tv.keys.size)
        val callerLines = wire.toString().lines().filter { it.contains("\"t\":\"hello\"") || it.contains("\"t\":\"auth\"") }
        assertEquals(2, callerLines.size)
        // The hello again (same key and number), then the old proof: the TV's fresh challenge makes the old proof
        // wrong, so it never gets as far as a press.
        val answer = session(tv) { link ->
            callerLines.forEach { line -> link.send(HWire.decode(line)!!) }
            link.read(); link.read()
        }
        assertTrue("$answer", answer is HMsg.Denied)
        assertEquals("the replayed press did nothing", 1, tv.keys.size)
    }
}
