package com.prism.music.connect

import com.prism.music.R
import com.prism.music.data.model.Song
import com.prism.music.playback.together.fromPeer
import com.prism.music.tr
import com.prism.music.trp

/** This Prism's side of a link: who it is, its pairings, and what it does when asked. */
interface HandoffLocal {
    val name: String
    val device: String
    /** A TV (it says so in its hello: a phone offers it a remote and its sign-in). */
    val isTv: Boolean get() = false
    /** The pairing key shared with [device] (hex), or null. */
    fun secretFor(device: String): String?
    fun savePairing(device: String, name: String, secret: String)
    fun forget(device: String)
    /** Shows [code] on this device for [from] to type in (null: put it away). */
    fun showCode(code: String?, from: String)
    /**
     * The code [from] has to type to pair, shown here; null when this device isn't taking pairings now (a TV only takes
     * them while its pairing screen is open).
     */
    fun pairingCode(from: String): String? = HandoffKeys.newCode().also { showCode(it, from) }
    /** What's playing here, or null. */
    fun snapshot(): HandoffSnapshot?
    /** Plays [s] here; null once it has, else why it couldn't (said to the other device). [from] is the other device's name. */
    fun takeOver(s: HandoffSnapshot, from: String): String?
    /** The other device has it now: fade out and pause. */
    fun stepAside()
    /** A remote's button; null when done, else why not. */
    fun remote(key: RemoteKey, value: Long): String? = tr(R.string.both_devices_need_the_latest_prism)
    /** What's playing here, for a remote. */
    fun nowPlaying(): RemoteNow? = null
    /** Songs from [from] for this queue; null when added, else why not. */
    fun addSongs(songs: List<Song>, next: Boolean, from: String): String? = tr(R.string.both_devices_need_the_latest_prism)
    /** [from]'s YouTube Music sign-in, to use here; null when signed in. */
    fun signIn(values: Map<String, String>, account: String, from: String): String? = tr(R.string.both_devices_need_the_latest_prism)
    /** A Look from [from] to use here; null when it's on. */
    fun useLook(code: String, from: String): String? = tr(R.string.both_devices_need_the_latest_prism)
}

sealed interface HandoffResult {
    /** [note]: something to say (songs that couldn't come). */
    data class Done(val note: String? = null) : HandoffResult
    data class Failed(val reason: String) : HandoffResult
    /** The key this side had was refused: pair again. */
    data object Unpaired : HandoffResult
    data object Cancelled : HandoffResult
    /** What the other side is playing (for [HandoffRequest.Now]). */
    data class Playing(val now: RemoteNow?) : HandoffResult
}

/** What a caller asks of the other side, once the link is up. */
sealed interface HandoffRequest {
    data object Move : HandoffRequest
    data object Pull : HandoffRequest
    data class Remote(val key: RemoteKey, val value: Long = 0) : HandoffRequest
    data object Now : HandoffRequest
    data class Add(val songs: List<Song>, val next: Boolean) : HandoffRequest
    data class SignIn(val values: Map<String, String>, val account: String) : HandoffRequest
    data class Look(val code: String) : HandoffRequest
    /** Only pair (or check the pairing): nothing more. */
    data object Pair : HandoffRequest
}

/**
 * The two ends of a link over one connection (version 2, 1.8.0). Both say hello with a fresh X25519 key. A caller
 * already paired proves it holds the pairing's key for this connection and the receiver proves it back; a new caller
 * asks to pair: the receiving side shows a six-digit code, the listener types it on the caller, and the code is checked
 * in twenty rounds of commitments ([Passkey]) without ever crossing the wire, the pairing's key worked out on both sides
 * from the key agreement. From then on every message is sealed ([SealedChannel]). Then one request ([HandoffRequest]);
 * for a move, the side giving playback up steps aside only once the other says it's playing.
 */
class HandoffPeer(private val local: HandoffLocal, private val clock: () -> Long = System::currentTimeMillis) {
    private val pairAsks = java.util.ArrayDeque<Long>()

    private fun pairAllowed(): Boolean = synchronized(pairAsks) {
        val now = clock()
        while (pairAsks.isNotEmpty() && pairAsks.first < now - 60_000) pairAsks.removeFirst()
        if (pairAsks.size >= PAIR_ASKS_PER_MINUTE) return false
        pairAsks.addLast(now)
        true
    }

    /** Everything both sides said, which every key and proof is bound to. */
    private fun transcript(callerDevice: String, receiverDevice: String, callerKey: String, receiverKey: String, callerNonce: String, receiverNonce: String) =
        "v${Handoff.VERSION}|$callerDevice|$receiverDevice|$callerKey|$receiverKey|$callerNonce|$receiverNonce"

    /** The receiving side, for one connection. Blocking. */
    fun serve(link: HLink) {
        link.timeout(8_000)
        val hello = link.read() as? HMsg.Hello ?: return
        val theirKey = LinkKeys.unhex(hello.key)
        if (hello.version != Handoff.VERSION || theirKey?.size != 32 || hello.nonce.length != 32) {
            link.send(HMsg.Denied(tr(R.string.both_devices_need_the_latest_prism))); return
        }
        val them = hello.name.trim().take(60).ifBlank { tr(R.string.another_device) }
        val mine = X25519.newPrivateKey()
        val myKey = LinkKeys.hex(X25519.publicKey(mine))
        val nonce = LinkKeys.hex(LinkKeys.nonce())
        link.send(HMsg.Challenge(local.name, local.device, nonce, myKey, local.isTv))
        val shared = X25519.shared(mine, theirKey) ?: return
        val t = transcript(hello.device, local.device, hello.key, myKey, hello.nonce, nonce)
        val pairingKey: ByteArray = when (val m = link.read()) {
            is HMsg.Auth -> {
                val key = local.secretFor(hello.device)?.let(LinkKeys::unhex)
                if (key == null || !LinkKeys.same(LinkKeys.proof(key, "c", t).toByteArray(), m.mac.toByteArray())) {
                    Thread.sleep(400)
                    link.send(HMsg.Denied(tr(R.string.isn_t_paired_with_any_more, them, local.name), unpaired = true))
                    return
                }
                key
            }
            HMsg.PairRequest -> {
                if (!pairAllowed()) { link.send(HMsg.Denied(tr(R.string.too_many_pairing_tries_wait_a))); return }
                val code = local.pairingCode(them) ?: run { link.send(HMsg.Denied(tr(R.string.tv_pairing_closed, local.name))); return }
                try {
                    link.send(HMsg.EnterCode)
                    link.timeout(PAIR_WAIT_MS)
                    if (!confirmCode(link, code, caller = false, own = X25519.publicKey(mine), other = theirKey)) {
                        Thread.sleep(700)
                        link.send(HMsg.Denied(tr(R.string.that_code_didn_t_match_try)))
                        return
                    }
                    val key = LinkKeys.pairingKey(shared, t)
                    local.savePairing(hello.device, them, LinkKeys.hex(key))
                    link.send(HMsg.Paired)
                    link.timeout(10_000)
                    // The caller now proves the new key, as on any later connection.
                    val proof = link.read() as? HMsg.Auth ?: return
                    if (!LinkKeys.same(LinkKeys.proof(key, "c", t).toByteArray(), proof.mac.toByteArray())) return
                    key
                } finally {
                    local.showCode(null, them)
                }
            }
            else -> return
        }
        link.send(HMsg.AuthOk(LinkKeys.proof(pairingKey, "s", t)))
        val (toReceiver, toCaller) = LinkKeys.sessionKeys(shared, pairingKey, t)
        link.channel = SealedChannel(sendKey = toCaller, receiveKey = toReceiver)
        link.timeout(20_000)
        when (val cmd = link.read()) {
            is HMsg.Move -> {
                val s = cmd.snapshot.cleaned()
                if (s == null) link.send(HMsg.Denied(tr(R.string.what_s_playing_on_is_only, them, them)))
                else link.send(local.takeOver(s, them)?.let { HMsg.Denied(it) } ?: HMsg.Moved)
            }
            HMsg.Pull -> {
                val s = local.snapshot()
                if (s == null) { link.send(HMsg.Denied(tr(R.string.nothing_s_playing_on, local.name))); return }
                link.send(HMsg.Snap(s))
                if (link.read() == HMsg.Moved) local.stepAside()
            }
            is HMsg.Remote -> link.send(local.remote(cmd.key, cmd.value)?.let { HMsg.Denied(it) } ?: HMsg.Ok)
            HMsg.Now -> link.send(HMsg.NowIs(local.nowPlaying()))
            is HMsg.Add -> {
                val songs = cmd.songs.mapNotNull { it.fromPeer() }.take(HandoffSnapshot.MAX_SONGS)
                link.send(if (songs.isEmpty()) HMsg.Denied(tr(R.string.what_s_playing_on_is_only, them, them)) else local.addSongs(songs, cmd.next, them)?.let { HMsg.Denied(it) } ?: HMsg.Ok)
            }
            is HMsg.SignIn -> link.send(local.signIn(cmd.values, cmd.account.take(200), them)?.let { HMsg.Denied(it) } ?: HMsg.Ok)
            is HMsg.Look -> link.send(local.useLook(cmd.code.take(MAX_LOOK_CODE), them)?.let { HMsg.Denied(it) } ?: HMsg.Ok)
            // Pairing only: done.
            HMsg.Ok -> Unit
            else -> Unit
        }
    }

    /** The calling side of a move or a pull ([command]), as before 1.8.0. */
    fun call(link: HLink, command: HandoffCommand, askCode: (String) -> String?): HandoffResult =
        request(link, if (command == HandoffCommand.MOVE) HandoffRequest.Move else HandoffRequest.Pull, askCode)

    /**
     * The calling side: [request] of the device at the other end of [link]. [askCode] is called (blocking) when they
     * need pairing first, for the code the other device shows; null cancels. Blocking.
     */
    fun request(link: HLink, request: HandoffRequest, askCode: (String) -> String?): HandoffResult {
        link.timeout(8_000)
        val mine = X25519.newPrivateKey()
        val myKey = LinkKeys.hex(X25519.publicKey(mine))
        val nonce = LinkKeys.hex(LinkKeys.nonce())
        if (!link.send(HMsg.Hello(local.name, local.device, Handoff.VERSION, myKey, nonce, local.isTv))) return HandoffResult.Failed(tr(R.string.couldn_t_reach_it))
        val ch = when (val m = link.read()) {
            is HMsg.Challenge -> m
            is HMsg.Denied -> return HandoffResult.Failed(m.reason)
            else -> return HandoffResult.Failed(tr(R.string.it_didn_t_answer))
        }
        val them = ch.name.trim().take(60).ifBlank { tr(R.string.the_other_device) }
        val theirKey = LinkKeys.unhex(ch.key)?.takeIf { it.size == 32 } ?: return HandoffResult.Failed(tr(R.string.both_devices_need_the_latest_prism))
        val shared = X25519.shared(mine, theirKey) ?: return HandoffResult.Failed(tr(R.string.it_didn_t_answer))
        val t = transcript(local.device, ch.device, myKey, ch.key, nonce, ch.nonce)
        var pairingKey = local.secretFor(ch.device)?.let(LinkKeys::unhex)
        if (pairingKey == null) {
            link.send(HMsg.PairRequest)
            when (val r = link.read()) {
                HMsg.EnterCode -> Unit
                is HMsg.Denied -> return HandoffResult.Failed(r.reason)
                else -> return HandoffResult.Failed(tr(R.string.didn_t_answer, them))
            }
            link.timeout(PAIR_WAIT_MS)
            val code = askCode(them)?.filter { it.isDigit() }?.takeIf { it.length == 6 } ?: return HandoffResult.Cancelled
            link.timeout(10_000)
            if (!confirmCode(link, code, caller = true, own = X25519.publicKey(mine), other = theirKey)) {
                return HandoffResult.Failed(tr(R.string.that_code_didn_t_match_try))
            }
            when (val r = link.read()) {
                HMsg.Paired -> Unit
                is HMsg.Denied -> return HandoffResult.Failed(r.reason)
                else -> return HandoffResult.Failed(tr(R.string.didn_t_answer, them))
            }
            pairingKey = LinkKeys.pairingKey(shared, t)
            local.savePairing(ch.device, them, LinkKeys.hex(pairingKey))
        }
        link.send(HMsg.Auth(LinkKeys.proof(pairingKey, "c", t)))
        when (val r = link.read()) {
            is HMsg.AuthOk -> if (!LinkKeys.same(LinkKeys.proof(pairingKey, "s", t).toByteArray(), r.mac.toByteArray())) {
                // Whoever answered doesn't hold the pairing's key: not the device it says it is.
                return HandoffResult.Failed(tr(R.string.didn_t_answer, them))
            }
            is HMsg.Denied -> {
                if (r.unpaired) { local.forget(ch.device); return HandoffResult.Unpaired }
                return HandoffResult.Failed(r.reason)
            }
            else -> return HandoffResult.Failed(tr(R.string.didn_t_answer, them))
        }
        val (toReceiver, toCaller) = LinkKeys.sessionKeys(shared, pairingKey, t)
        link.channel = SealedChannel(sendKey = toReceiver, receiveKey = toCaller)
        link.timeout(20_000)
        return when (request) {
            HandoffRequest.Pair -> { link.send(HMsg.Ok); HandoffResult.Done() }
            HandoffRequest.Move -> {
                val s = local.snapshot() ?: return HandoffResult.Failed(tr(R.string.nothing_s_playing_here))
                val clean = s.cleaned() ?: return HandoffResult.Failed(tr(R.string.this_song_is_a_file_on))
                link.send(HMsg.Move(s))
                when (val r = link.read()) {
                    HMsg.Moved -> {
                        local.stepAside()
                        HandoffResult.Done(s.leftOut(clean).takeIf { it > 0 }?.let { trp(R.plurals.n_songs_stayed_behind, it) })
                    }
                    is HMsg.Denied -> HandoffResult.Failed(r.reason)
                    else -> HandoffResult.Failed(tr(R.string.didn_t_start_playing, them))
                }
            }
            HandoffRequest.Pull -> {
                link.send(HMsg.Pull)
                when (val r = link.read()) {
                    is HMsg.Snap -> {
                        val clean = r.snapshot.cleaned() ?: return HandoffResult.Failed(tr(R.string.what_s_playing_on_is_only, them, them))
                        local.takeOver(clean, them)?.let { return HandoffResult.Failed(it) }
                        link.send(HMsg.Moved)
                        HandoffResult.Done(r.snapshot.leftOut(clean).takeIf { it > 0 }?.let { trp(R.plurals.n_songs_stayed_on, it, it, them) })
                    }
                    is HMsg.Denied -> HandoffResult.Failed(r.reason)
                    else -> HandoffResult.Failed(tr(R.string.didn_t_answer, them))
                }
            }
            HandoffRequest.Now -> {
                link.send(HMsg.Now)
                (link.read() as? HMsg.NowIs)?.let { HandoffResult.Playing(it.now) } ?: HandoffResult.Failed(tr(R.string.didn_t_answer, them))
            }
            is HandoffRequest.Remote -> answer(link, HMsg.Remote(request.key, request.value), them)
            is HandoffRequest.Add -> answer(link, HMsg.Add(request.songs, request.next), them)
            is HandoffRequest.SignIn -> answer(link, HMsg.SignIn(request.values, request.account), them)
            is HandoffRequest.Look -> answer(link, HMsg.Look(request.code), them)
        }
    }

    /** Sends [m] and reads the other side's Ok (Done) or Denied (Failed). */
    private fun answer(link: HLink, m: HMsg, them: String): HandoffResult {
        link.send(m)
        return when (val r = link.read()) {
            HMsg.Ok -> HandoffResult.Done()
            is HMsg.Denied -> HandoffResult.Failed(r.reason)
            else -> HandoffResult.Failed(tr(R.string.didn_t_answer, them))
        }
    }

    /**
     * The code's twenty rounds ([Passkey]): the caller commits first and reveals first, the receiver checks each
     * reveal against its own bit before revealing its own. Each side's commitment covers both public keys, so a
     * stranger in the middle (with keys of its own on each side) is caught in a round with even odds. False as soon as
     * a round fails (or the other side stops answering).
     */
    private fun confirmCode(link: HLink, code: String, caller: Boolean, own: ByteArray, other: ByteArray): Boolean {
        val me = if (caller) "c" else "s"
        val they = if (caller) "s" else "c"
        for (round in 0 until Passkey.ROUNDS) {
            val bit = Passkey.bit(code, round)
            val n = LinkKeys.nonce()
            val myCommit = Passkey.commit(n, me, own, other, bit)
            val theirCommit: String
            if (caller) {
                link.send(HMsg.Commit(myCommit))
                theirCommit = (link.read() as? HMsg.Commit)?.c ?: return false
                link.send(HMsg.Reveal(LinkKeys.hex(n)))
                val revealed = (link.read() as? HMsg.Reveal)?.n?.let(LinkKeys::unhex) ?: return false
                if (!Passkey.opens(theirCommit, revealed, they, other, own, bit)) return false
            } else {
                theirCommit = (link.read() as? HMsg.Commit)?.c ?: return false
                link.send(HMsg.Commit(myCommit))
                val revealed = (link.read() as? HMsg.Reveal)?.n?.let(LinkKeys::unhex) ?: return false
                if (!Passkey.opens(theirCommit, revealed, they, other, own, bit)) return false
                link.send(HMsg.Reveal(LinkKeys.hex(n)))
            }
        }
        return true
    }

    companion object {
        const val PAIR_WAIT_MS = 180_000
        const val PAIR_ASKS_PER_MINUTE = 5
        /** A Look's code is a few kilobytes; anything far longer isn't one. */
        private const val MAX_LOOK_CODE = 64_000
    }
}

enum class HandoffCommand { MOVE, PULL }
