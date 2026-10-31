package com.prism.music.connect

import com.prism.music.data.model.Song
import com.prism.music.playback.together.fromPeer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.net.Socket
import java.security.SecureRandom

/**
 * What's playing, as it moves from one Prism to another: the whole queue, the song it's on and where, play order
 * (shuffled or not) and repeat. [order] lists queue indices in the order they play (empty when not shuffled).
 */
@Serializable
data class HandoffSnapshot(
    val songs: List<Song>,
    val index: Int,
    val positionMs: Long,
    val playing: Boolean,
    val shuffle: Boolean = false,
    val order: List<Int> = emptyList(),
    val repeat: Int = 0,
    val title: String = "",
) {
    /**
     * Made safe to play here: only YouTube songs ([fromPeer]; files on the other phone can't come along), with the
     * index and play order moved to match. Null when there's nothing left, or the song playing was one that can't come.
     */
    fun cleaned(): HandoffSnapshot? {
        val keep = songs.mapIndexedNotNull { i, s -> s.fromPeer()?.let { i to it } }.take(MAX_SONGS)
        val newIndex = HashMap<Int, Int>()
        keep.forEachIndexed { n, (old, _) -> newIndex[old] = n }
        val at = newIndex[index] ?: return null
        val playOrder = if (shuffle) order.mapNotNull { newIndex[it] }.distinct() else emptyList()
        // The order has to cover the whole queue exactly once; if it doesn't (an older Prism, a bad message), queue order.
        val orderOk = playOrder.size == keep.size
        return copy(
            songs = keep.map { it.second },
            index = at,
            positionMs = positionMs.coerceAtLeast(0),
            shuffle = shuffle && orderOk,
            order = if (shuffle && orderOk) playOrder else emptyList(),
            repeat = repeat.coerceIn(0, 2),
            title = title.take(200),
        )
    }

    /** How many songs the queue had that couldn't come (files on the other phone). */
    fun leftOut(cleaned: HandoffSnapshot): Int = songs.size - cleaned.songs.size

    companion object { const val MAX_SONGS = 2_000 }
}

/** What a TV is playing, for a phone being its remote. */
@Serializable
data class RemoteNow(
    val title: String,
    val artist: String,
    val thumbnail: String? = null,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    /** The TV's volume, 0–100. */
    val volume: Int = 0,
)

/** The phone-as-remote's buttons. */
enum class RemoteKey { TOGGLE, PLAY, PAUSE, NEXT, PREVIOUS, SEEK_TO, SEEK_BY, VOLUME_UP, VOLUME_DOWN }

/**
 * One message per line between two Prisms ([Handoff]). Version 2 (1.8.0): the hello and challenge carry fresh X25519
 * keys; after pairing or proving the pairing, every line is sealed ([SealedChannel]).
 */
@Serializable
sealed class HMsg {
    @Serializable @SerialName("hello")
    data class Hello(val name: String, val device: String, val version: Int = Handoff.VERSION, val key: String = "", val nonce: String = "", val tv: Boolean = false) : HMsg()

    /** The receiving side's answer: who it is, its own fresh key and number. */
    @Serializable @SerialName("challenge")
    data class Challenge(val name: String, val device: String, val nonce: String, val key: String = "", val tv: Boolean = false) : HMsg()

    /** The caller's proof that it holds the pairing's key, for this connection. */
    @Serializable @SerialName("auth")
    data class Auth(val mac: String) : HMsg()

    /** The receiving side's proof back (both prove it: neither can be stood in for). */
    @Serializable @SerialName("auth_ok")
    data class AuthOk(val mac: String) : HMsg()

    @Serializable @SerialName("pair_request")
    data object PairRequest : HMsg()

    /** The receiving side is showing a code: type it on the caller. */
    @Serializable @SerialName("enter_code")
    data object EnterCode : HMsg()

    /** One round of confirming the code: a commitment to a fresh number and one of the code's bits ([Passkey]). */
    @Serializable @SerialName("commit")
    data class Commit(val c: String) : HMsg()

    /** The number behind the last commitment. */
    @Serializable @SerialName("reveal")
    data class Reveal(val n: String) : HMsg()

    /** All twenty rounds held: paired (each side works out the key itself; it's never sent). */
    @Serializable @SerialName("paired")
    data object Paired : HMsg()

    @Serializable @SerialName("ok")
    data object Ok : HMsg()

    @Serializable @SerialName("denied")
    data class Denied(val reason: String, val unpaired: Boolean = false) : HMsg()

    /** Play this here (the caller stops once it's going). */
    @Serializable @SerialName("move")
    data class Move(val snapshot: HandoffSnapshot) : HMsg()

    /** Send me what you're playing (and stop once I've got it). */
    @Serializable @SerialName("pull")
    data object Pull : HMsg()

    @Serializable @SerialName("snapshot")
    data class Snap(val snapshot: HandoffSnapshot) : HMsg()

    /** It's playing on this side now: the other side can stop. */
    @Serializable @SerialName("moved")
    data object Moved : HMsg()

    /** A remote's button ([value]: the place for SEEK_TO, the jump for SEEK_BY, in ms). */
    @Serializable @SerialName("remote")
    data class Remote(val key: RemoteKey, val value: Long = 0) : HMsg()

    /** What are you playing? */
    @Serializable @SerialName("now")
    data object Now : HMsg()

    @Serializable @SerialName("now_is")
    data class NowIs(val now: RemoteNow?) : HMsg()

    /** Songs for the other side's queue: next, or at the end. */
    @Serializable @SerialName("add")
    data class Add(val songs: List<Song>, val next: Boolean) : HMsg()

    /** The phone's YouTube Music sign-in, for the TV (sent only after the listener said so on the phone). */
    @Serializable @SerialName("sign_in")
    data class SignIn(val values: Map<String, String>, val account: String) : HMsg()

    /** A Look, as its code, to use on the other side. */
    @Serializable @SerialName("look")
    data class Look(val code: String) : HMsg()
}

object HWire {
    val json = Json { classDiscriminator = "t"; ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(m: HMsg): String = json.encodeToString(HMsg.serializer(), m)
    fun decode(line: String): HMsg? = runCatching { json.decodeFromString(HMsg.serializer(), line) }.getOrNull()
}

/**
 * A connection between two Prisms: [send] and [read] one message per line. Once [channel] is set every line is sealed
 * ("e:" and the sealed message in Base64); after that a line that isn't, or doesn't open, ends the connection.
 */
class HLink(private val socket: Socket) : Closeable {
    private val reader = com.prism.music.playback.together.LineReader(socket.getInputStream().buffered())
    private val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

    @Volatile var channel: SealedChannel? = null

    val remote: String get() = socket.inetAddress?.hostAddress ?: "?"

    fun timeout(ms: Int) { runCatching { socket.soTimeout = ms } }

    fun send(m: HMsg): Boolean = synchronized(writer) {
        val text = HWire.encode(m)
        val line = channel?.let { "e:" + java.util.Base64.getEncoder().encodeToString(it.seal(text.toByteArray())) } ?: text
        runCatching { writer.write(line); writer.write("\n"); writer.flush() }.isSuccess
    }

    /** The next message it understands; null once the connection's gone (or the wait ran out, or a sealed line failed). */
    fun read(): HMsg? {
        while (true) {
            val line = runCatching { reader.readLine() }.getOrNull() ?: return null
            if (line.isBlank()) continue
            val ch = channel
            if (ch == null) { HWire.decode(line)?.let { return it }; continue }
            if (!line.startsWith("e:")) return null
            val plain = runCatching { java.util.Base64.getDecoder().decode(line.substring(2)) }.getOrNull()?.let(ch::open) ?: return null
            return HWire.decode(plain.decodeToString())
        }
    }

    override fun close() { runCatching { socket.close() } }
}

object HandoffKeys {
    private val random = SecureRandom()

    fun newCode(): String = (100_000 + random.nextInt(900_000)).toString()
}
