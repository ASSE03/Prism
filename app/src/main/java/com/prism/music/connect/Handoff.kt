package com.prism.music.connect

import com.prism.music.R
import com.prism.music.tr
import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.prism.music.AppContainer
import com.prism.music.data.Notifications
import com.prism.music.playback.toSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.coroutines.resume

/** Another Prism on this network that's available for handoff. */
data class PrismDevice(val name: String, val host: String, val port: Int, val id: String?, val key: String, val paired: Boolean, val tv: Boolean = false)

/** A code this device is showing for another to type in, while it pairs. */
data class ShownCode(val code: String, val from: String)

/**
 * Prism Connect's handoff: what's playing moves from one Prism to another on the same Wi-Fi (phone to tablet, one phone
 * to another), queue, place, shuffle and all; or is brought over from the other one. A device that's "available for
 * handoff" (Settings → Prism Connect) listens and advertises itself as `_prismconnect._tcp`; any Prism can find it. The
 * first time two pair with a code shown on the receiving one; after that they recognise each other. Local network
 * only, no server, and its own protocol (Listen Together's is separate).
 */
class Handoff(private val app: Application, private val c: AppContainer) : HandoffLocal {
    private val prefs = app.getSharedPreferences("connect_handoff", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsd: NsdManager? by lazy { app.getSystemService(NsdManager::class.java) }
    private val peer = HandoffPeer(this)

    private val _devices = MutableStateFlow<List<PrismDevice>>(emptyList())
    val devices: StateFlow<List<PrismDevice>> = _devices.asStateFlow()
    private val _shown = MutableStateFlow<ShownCode?>(null)
    /** The pairing code this device is showing (in the app and as a notification). */
    val shownCode: StateFlow<ShownCode?> = _shown.asStateFlow()
    private val _asking = MutableStateFlow<String?>(null)
    /** The device whose code the listener is being asked for. */
    val askingCodeFor: StateFlow<String?> = _asking.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    /** "Moving to Pixel Tablet…" while a handoff is under way. */
    val busy: StateFlow<String?> = _busy.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    init {
        // 1.8.0: links are encrypted now (version 2); keys from before can't be used for it, so those devices pair again once.
        val old = prefs.all.keys.filter { it.startsWith("s:") }
        if (old.isNotEmpty()) prefs.edit().apply { old.forEach { k -> remove(k); remove("n:" + k.removePrefix("s:")) } }.apply()
    }

    private val _pairings = MutableStateFlow(pairings())
    /** Devices this one is paired with: id → name. */
    val pairedDevices: StateFlow<List<Pair<String, String>>> = _pairings.asStateFlow()

    override val device: String = prefs.getString("me", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("me", it).apply() }

    override val name: String
        get() {
            val label = runCatching { app.applicationInfo.loadLabel(app.packageManager).toString() }.getOrDefault("Prism")
            val who = c.displayName(first = true)
            val base = if (who.isNotBlank()) "$who's ${Build.MODEL}" else Build.MODEL ?: "Prism"
            // Two copies of Prism on one phone (a test copy beside the real one) need telling apart.
            return if (label != "Prism") "$base ($label)" else base
        }

    // ------------------------------------------------------------ Pairings

    private fun pairings(): List<Pair<String, String>> = prefs.all.keys.filter { it.startsWith("k:") }.map { k ->
        val id = k.removePrefix("k:")
        id to (prefs.getString("n:$id", null) ?: tr(R.string.a_prism_device))
    }.sortedBy { it.second.lowercase() }

    override fun secretFor(device: String): String? = prefs.getString("k:$device", null)

    override fun savePairing(device: String, name: String, secret: String) {
        prefs.edit().putString("k:$device", secret).putString("n:$device", name).apply()
        _pairings.value = pairings()
        refreshPaired()
        _pairedWith.tryEmit(name)
    }

    override fun forget(device: String) {
        prefs.edit().remove("k:$device").remove("n:$device").apply()
        _pairings.value = pairings()
        refreshPaired()
    }

    private fun refreshPaired() {
        _devices.value = _devices.value.map { it.copy(paired = it.id != null && secretFor(it.id) != null) }
    }

    override fun showCode(code: String?, from: String) {
        // A TV's code is on its pairing screen already, not in a notification.
        if (isTv) return
        _shown.value = code?.let { ShownCode(it, from) }
        if (code == null) {
            runCatching { androidx.core.app.NotificationManagerCompat.from(app).cancel(Notifications.CONNECT, NOTIFY_ID) }
        } else {
            Notifications.post(
                app, Notifications.CONNECT, NOTIFY_ID, tr(R.string.pairing_code_2, code.take(3), code.drop(3)),
                tr(R.string.wants_to_play_music_on_this, from, from), "prism://connect",
            ) { it.setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH).setTimeoutAfter(HandoffPeer.PAIR_WAIT_MS.toLong()) }
        }
    }

    /** Puts the shown code away (the other device's wait then simply runs out). */
    fun hideCode() {
        _shown.value = null
        runCatching { androidx.core.app.NotificationManagerCompat.from(app).cancel(Notifications.CONNECT, NOTIFY_ID) }
    }

    // ------------------------------------------------------------ The player

    override fun snapshot(): HandoffSnapshot? = runBlocking(Dispatchers.Main) {
        val p = c.player.player ?: return@runBlocking null
        if (p.mediaItemCount == 0) return@runBlocking null
        val songs = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).toSong() }
        if (songs.size != p.mediaItemCount) return@runBlocking null
        // The play order, read off the timeline (it covers every song once, shuffled or not).
        val order = if (!p.shuffleModeEnabled) emptyList() else buildList {
            val tl = p.currentTimeline
            var i = tl.getFirstWindowIndex(true)
            while (i != androidx.media3.common.C.INDEX_UNSET && size < tl.windowCount) {
                add(i); i = tl.getNextWindowIndex(i, androidx.media3.common.Player.REPEAT_MODE_OFF, true)
            }
        }
        HandoffSnapshot(
            songs = songs, index = p.currentMediaItemIndex, positionMs = p.currentPosition, playing = p.playWhenReady,
            shuffle = p.shuffleModeEnabled, order = order, repeat = p.repeatMode, title = c.queue.source.value.title,
        )
    }

    /**
     * Android (15 and later) won't let an app in the background start playing: its request for the speaker is ignored.
     * So music only moves here while Prism is open here, or already playing (it has the speaker then); otherwise the
     * other device is told so and keeps playing, rather than going quiet with nothing playing here either.
     */
    override fun takeOver(s: HandoffSnapshot, from: String): String? {
        val canPlay = runBlocking(Dispatchers.Main) { onScreen() || c.player.isPlaying.value }
        if (s.playing && !canPlay) {
            Log.i(TAG, "turned down a move from $from: not on screen or playing here")
            return tr(R.string.open_prism_on_first_android_only, name)
        }
        runBlocking(Dispatchers.Main) {
            c.player.takeOver(s.songs, s.index, s.positionMs, s.playing, s.order.takeIf { s.shuffle }, s.repeat, s.title.ifBlank { tr(R.string.from, from) })
        }
        if (s.playing) {
            // Started for real (the speaker can still be refused): only then does the other device stop.
            val started = runBlocking { withTimeoutOrNull(START_WAIT_MS) { while (!c.player.isPlaying.value) delay(100); true } } == true
            if (!started) return tr(R.string.couldn_t_start_playing_open_prism, name)
        }
        _events.tryEmit(tr(R.string.playing_here_from, from))
        return null
    }

    private fun onScreen(): Boolean {
        val info = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(info)
        return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    override fun stepAside() {
        runBlocking(Dispatchers.Main) {
            val p = c.player.player ?: return@runBlocking
            if (!p.playWhenReady) return@runBlocking
            // Faded out rather than cut (unless casting, where the phone's own player is silent anyway).
            if (!c.cast.casting) {
                val start = p.volume
                for (step in 1..12) { p.volume = start * (1f - step / 12f); delay(70) }
                p.pause()
                p.volume = start
            } else p.pause()
        }
    }

    // ------------------------------------------------------------ A TV's side (1.8.0)

    override val isTv: Boolean by lazy { com.prism.music.ui.tv.TvDevice.isTv(app) }

    private val tvPairing = PairingCode()
    /** The code the TV's pairing screen shows, while it's open; a TV takes pairings only then. */
    val tvCode: StateFlow<String?> = tvPairing.value
    private val _pairedWith = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** A device that just paired with this one (its name): the TV's pairing screen moves on. */
    val pairedWith = _pairedWith.asSharedFlow()
    private val _signedIn = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** The account a phone just signed this TV in with. */
    val signedIn = _signedIn.asSharedFlow()

    /** The TV's pairing screen opens: a fresh code, shown on it. */
    fun openPairing(): String = tvPairing.open()
    fun closePairing() = tvPairing.close()

    override fun takesPairing(): Boolean = !isTv || tvPairing.value.value != null

    /** A TV: a new code after every try (shown on its screen and in the QR code), none once a phone has paired. */
    override fun pairingEnded(code: String, matched: Boolean): Boolean = if (isTv) tvPairing.ended(code, matched) else matched

    /**
     * The link a TV's pairing screen puts in its QR code: this TV's address and port, its name and the code shown, and
     * whether the phone should then offer its sign-in ([signIn]). A phone's camera opens it in Prism ([pairFromLink]).
     */
    fun pairingLink(code: String, signIn: Boolean): String? {
        val host = Lan.ownAddress() ?: return null
        val port = server?.localPort ?: return null
        return android.net.Uri.Builder().scheme("prism").authority("pair")
            .appendQueryParameter("h", host).appendQueryParameter("p", port.toString())
            .appendQueryParameter("n", name).appendQueryParameter("c", code)
            .apply { if (signIn) appendQueryParameter("s", "1") }
            .build().toString()
    }

    private val _signInAsk = MutableStateFlow<PrismDevice?>(null)
    /** A TV this phone just paired with asked for its sign-in: the phone asks the listener first ([HandoffDialogs]). */
    val signInAsk: StateFlow<PrismDevice?> = _signInAsk.asStateFlow()
    fun askSignIn(d: PrismDevice?) { _signInAsk.value = d }

    /** A pairing link opened on this phone, waiting for the listener's yes ([HandoffDialogs]). */
    data class LinkPair(val name: String, val address: java.net.InetAddress, val port: Int, val code: String, val signIn: Boolean) {
        val shownAddress: String get() = address.hostAddress ?: "?"
    }

    private val _linkPair = MutableStateFlow<LinkPair?>(null)
    val linkPair: StateFlow<LinkPair?> = _linkPair.asStateFlow()

    /**
     * A TV's QR code, scanned on this phone: once the listener says yes to the TV's name and address ([answerLinkPair]),
     * pairs with it (the code comes with the link), then offers the sign-in if it's a TV. Any page or app can open such
     * a link, so nothing happens without that yes, and only to an address on this network (not this phone itself).
     */
    fun pairFromLink(link: android.net.Uri) {
        val host = link.getQueryParameter("h") ?: return
        val port = link.getQueryParameter("p")?.toIntOrNull()?.takeIf { it in 1..65_535 } ?: return
        val code = link.getQueryParameter("c")?.filter { it.isDigit() }?.takeIf { it.length == 6 } ?: return
        val name = link.getQueryParameter("n")?.trim()?.take(60)?.ifBlank { null }
        val signIn = link.getQueryParameter("s") == "1"
        scope.launch {
            // Looked up once, here, and connected to as found (a name can't point elsewhere in between).
            val address = runCatching { java.net.InetAddress.getByName(host) }.getOrNull() ?: return@launch
            if (address.isLoopbackAddress || address.isAnyLocalAddress || !Lan.isLocal(address)) return@launch
            _linkPair.value = LinkPair(name ?: address.hostAddress ?: host, address, port, code, signIn)
        }
    }

    /** The listener's answer to [linkPair]. */
    fun answerLinkPair(yes: Boolean) {
        val ask = _linkPair.value ?: return
        _linkPair.value = null
        if (!yes) return
        scope.launch {
            val socket = runCatching { Socket().apply { connect(InetSocketAddress(ask.address, ask.port), 5_000); tcpNoDelay = true } }.getOrNull()
            val r = socket?.let { s -> HLink(s).use { link -> runCatching { peer.request(link, HandoffRequest.Pair) { ask.code } }.getOrNull() } }
            when (r) {
                is HandoffResult.Done -> {
                    val them = r.peer ?: return@launch
                    _events.tryEmit(tr(R.string.tv_paired_with, them.name))
                    // The sign-in is offered to the device that just proved itself, and only if it says it's a TV.
                    if (ask.signIn && them.tv) {
                        _signInAsk.value = PrismDevice(them.name, ask.shownAddress, ask.port, them.device, "link:${them.device}", true, tv = true)
                    }
                }
                is HandoffResult.Failed -> _events.tryEmit(r.reason)
                else -> _events.tryEmit(tr(R.string.couldn_t_reach_is_prism_open, ask.name))
            }
        }
    }

    /**
     * This phone's YouTube Music sign-in, sent to [d] (after the listener said yes); what to tell them. Only to the
     * device [d] names, paired already and a TV ([HandoffPeer.request]).
     */
    suspend fun sendSignIn(d: PrismDevice): String {
        if (d.id == null) return tr(R.string.tv_sign_in_pair_first, d.name)
        val values = c.settings.forBackup(settings = false, accounts = true).filterKeys { it in SIGN_IN_KEYS }
        val account = c.settings.current.let { it.accountEmail.ifBlank { it.accountName } }
        return when (val r = ask(d, HandoffRequest.SignIn(values, account))) {
            is HandoffResult.Done -> tr(R.string.tv_signed_in_on, d.name)
            is HandoffResult.Failed -> r.reason
            else -> tr(R.string.couldn_t_reach_is_prism_open, d.name)
        }
    }

    /** The look this phone has now, sent to [d] as a Look code; what to tell the listener. */
    suspend fun sendLook(d: PrismDevice): String {
        val look = com.prism.music.data.looks.Look("sent", name, c.settings.lookValues.value, c.looks.currentIcons(), null, System.currentTimeMillis())
        return when (val r = ask(d, HandoffRequest.Look(c.looks.code(look)))) {
            is HandoffResult.Done -> tr(R.string.tv_look_sent, d.name)
            is HandoffResult.Failed -> r.reason
            else -> tr(R.string.couldn_t_reach_is_prism_open, d.name)
        }
    }

    override fun pairingCode(from: String): String? = if (isTv) tvPairing.value.value else HandoffKeys.newCode().also { showCode(it, from) }

    override fun remote(key: RemoteKey, value: Long): String? {
        runBlocking(Dispatchers.Main) {
            val p = c.player
            when (key) {
                RemoteKey.TOGGLE -> p.togglePlay()
                RemoteKey.PLAY -> p.setPlaying(true)
                RemoteKey.PAUSE -> p.setPlaying(false)
                RemoteKey.NEXT -> p.next()
                RemoteKey.PREVIOUS -> p.previous()
                RemoteKey.SEEK_TO -> p.seekTo(value.coerceAtLeast(0))
                RemoteKey.SEEK_BY -> p.seekBy(value)
                RemoteKey.VOLUME_UP, RemoteKey.VOLUME_DOWN -> app.getSystemService(android.media.AudioManager::class.java)?.adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    if (key == RemoteKey.VOLUME_UP) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER,
                    android.media.AudioManager.FLAG_SHOW_UI,
                )
            }
        }
        return null
    }

    override fun nowPlaying(): RemoteNow? = runBlocking(Dispatchers.Main) {
        val song = c.player.currentSong.value ?: return@runBlocking null
        val audio = app.getSystemService(android.media.AudioManager::class.java)
        val volume = audio?.let { a ->
            val max = a.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            a.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100 / max
        } ?: 0
        RemoteNow(song.title, song.artistText, song.thumbnail, c.player.isPlaying.value, c.player.position, c.player.duration, volume)
    }

    override fun addSongs(songs: List<com.prism.music.data.model.Song>, next: Boolean, from: String): String? {
        runBlocking(Dispatchers.Main) {
            val source = com.prism.music.playback.QueueSource(com.prism.music.playback.QueueKind.OTHER, tr(R.string.from, from))
            when {
                c.player.queue.value.isEmpty() -> c.player.playQueue(songs, 0, source)
                next -> c.player.playNext(songs, source)
                else -> c.player.addToQueue(songs, source)
            }
        }
        return null
    }

    /** A phone's sign-in: only a TV takes one (and only the sign-in's own settings; Discord and Last.fm never come). */
    override fun signIn(values: Map<String, String>, account: String, from: String): String? {
        if (!isTv) return tr(R.string.tv_not_a_tv, name)
        val signIn = values.filterKeys { it in SIGN_IN_KEYS }
        if (signIn["cookie"].isNullOrBlank()) return tr(R.string.tv_not_a_tv, name)
        // Another account was signed in here: its library, offline copies and Home go first, as on signing out.
        if (c.settings.current.isLoggedIn) c.library.forgetAccount()
        runBlocking {
            c.settings.fromBackup(signIn, settings = false, accounts = true)
            c.settings.setOnboarded(true)
        }
        c.library.syncInBackground()
        _signedIn.tryEmit(account)
        return null
    }

    /** A Look sent from a phone: a TV uses it (on a phone, Looks are shared as codes and files instead). */
    override fun useLook(code: String, from: String): String? {
        if (!isTv) return tr(R.string.tv_not_a_tv, name)
        val look = c.looks.decode(code) ?: return tr(R.string.that_look_code_couldn_t_be)
        runBlocking { c.looks.apply(look) }
        return null
    }

    // ------------------------------------------------------------ Being available

    @Volatile private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null

    fun setAvailable(on: Boolean) { if (on) startServer() else stopServer() }

    /** The port this device listens on while available (null: not listening). */
    val port: Int? get() = server?.localPort

    /** Connections being served, on threads of their own ([LinkGate]). */
    private val links = LinkGate(MAX_LINKS, MAX_LINKS_PER_ADDRESS) { s ->
        HLink(s.apply { tcpNoDelay = true }).use { link -> runCatching { peer.serve(link) }.onFailure { Log.w(TAG, "handoff from ${link.remote} failed", it) } }
    }

    @Synchronized
    private fun startServer() {
        if (server != null) return
        val ss = runCatching { ServerSocket(PORT) }.getOrNull() ?: runCatching { ServerSocket(0) }.getOrNull() ?: return
        server = ss
        kotlin.concurrent.thread(name = "handoff-accept", isDaemon = true) {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                if (!Lan.isLocal(s.inetAddress)) { runCatching { s.close() }; continue }
                links.take(s)
            }
        }
        register(ss.localPort)
        Log.i(TAG, "available for handoff on ${ss.localPort} as $name")
    }

    @Synchronized
    private fun stopServer() {
        registration?.let { l -> runCatching { nsd?.unregisterService(l) } }
        registration = null
        runCatching { server?.close() }
        server = null
    }

    private fun register(port: Int) {
        val manager = nsd ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "$NAME_PREFIX${name.take(40)}"
            serviceType = SERVICE
            setPort(port)
            setAttribute("id", device)
            setAttribute("v", VERSION.toString())
            if (isTv) setAttribute("tv", "1")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) { Log.w(TAG, "advertising failed: $error") }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        registration = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { registration = null }
    }

    // ------------------------------------------------------------ Finding others

    private var discovery: NsdManager.DiscoveryListener? = null
    private var lookers = 0
    private val resolveLock = Mutex()

    fun startDiscovery() {
        lookers++
        val manager = nsd ?: return
        if (discovery != null) return
        _devices.value = testPeers()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { if (info.serviceType.contains(SERVICE_LABEL)) resolve(info) }
            override fun onServiceLost(info: NsdServiceInfo) { _devices.value = _devices.value.filterNot { it.key == info.serviceName } }
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) { discovery = null }
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
        }
        discovery = listener
        runCatching { manager.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { discovery = null }
    }

    /**
     * Debug builds only: Prisms by address, one "host:port|name|tv" per line in …/files/connect_peers.txt, for the
     * two-emulator tests (emulators can't find each other on a network; adb forwards a port between them).
     */
    private fun testPeers(): List<PrismDevice> {
        if (!com.prism.music.BuildConfig.DEBUG) return emptyList()
        val f = app.getExternalFilesDir(null)?.let { java.io.File(it, "connect_peers.txt") }?.takeIf { it.isFile } ?: return emptyList()
        return f.readLines().mapNotNull { line ->
            val parts = line.trim().split("|")
            val host = parts[0].substringBeforeLast(':').takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val port = parts[0].substringAfterLast(':').toIntOrNull() ?: return@mapNotNull null
            val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: host
            PrismDevice(name, host, port, null, "test:$host:$port", false, tv = parts.getOrNull(2) == "tv")
        }
    }

    fun stopDiscovery() {
        lookers = (lookers - 1).coerceAtLeast(0)
        if (lookers > 0) return
        discovery?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        discovery = null
    }

    private fun resolve(info: NsdServiceInfo) {
        val manager = nsd ?: return
        scope.launch {
            resolveLock.withLock {
                val r = withTimeoutOrNull(6_000) {
                    suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                        runCatching {
                            @Suppress("DEPRECATION")
                            manager.resolveService(info, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(i: NsdServiceInfo, error: Int) { if (cont.isActive) cont.resume(null) }
                                override fun onServiceResolved(i: NsdServiceInfo) { if (cont.isActive) cont.resume(i) }
                            })
                        }.onFailure { if (cont.isActive) cont.resume(null) }
                    }
                } ?: return@withLock
                val id = r.attributes["id"]?.toString(Charsets.UTF_8)
                // Itself (this very app, advertising while it looks).
                if (id == device) return@withLock
                @Suppress("DEPRECATION")
                val all = if (Build.VERSION.SDK_INT >= 34) r.hostAddresses else listOfNotNull(r.host)
                val host = (all.firstOrNull { it is Inet4Address } ?: all.firstOrNull())?.hostAddress ?: return@withLock
                val tv = r.attributes["tv"]?.toString(Charsets.UTF_8) == "1"
                val d = PrismDevice(r.serviceName.removePrefix(NAME_PREFIX), host, r.port, id, r.serviceName, id != null && secretFor(id) != null, tv)
                _devices.value = (_devices.value.filterNot { it.key == d.key } + d).sortedBy { it.name.lowercase() }
            }
        }
    }

    // ------------------------------------------------------------ Moving

    private var code: CompletableDeferred<String?>? = null

    /** The code the listener typed for [askingCodeFor] (null: cancelled). */
    fun submitCode(value: String?) {
        code?.complete(value?.filter { it.isDigit() }?.takeIf { it.length == 6 })
    }

    /** What's playing here goes to [d]. */
    fun moveTo(d: PrismDevice) = run(d, HandoffCommand.MOVE, tr(R.string.moving_to, d.name))

    /** What's playing on [d] comes here. */
    fun bringFrom(d: PrismDevice) = run(d, HandoffCommand.PULL, tr(R.string.bringing_it_over_from, d.name))

    private fun run(d: PrismDevice, command: HandoffCommand, label: String) {
        if (_busy.value != null) return
        _busy.value = label
        scope.launch {
            try {
                val request = if (command == HandoffCommand.MOVE) HandoffRequest.Move else HandoffRequest.Pull
                var result = attempt(d, request)
                // Its key was refused (forgotten over there): pair again, once.
                if (result == HandoffResult.Unpaired) result = attempt(d, request, repair = true)
                when (result) {
                    is HandoffResult.Done -> _events.tryEmit(
                        (if (command == HandoffCommand.MOVE) tr(R.string.playing_on, d.name) else tr(R.string.playing_here)) + (result.note?.let { " · $it" } ?: "")
                    )
                    is HandoffResult.Failed -> _events.tryEmit(result.reason)
                    HandoffResult.Unpaired -> _events.tryEmit(tr(R.string.didn_t_accept_the_pairing_try, d.name))
                    HandoffResult.Cancelled, is HandoffResult.Playing -> Unit
                }
            } finally {
                _busy.value = null
                _asking.value = null
            }
        }
    }

    /**
     * [request] of [d] (a TV's remote button, what it's playing, a sign-in, a Look), pairing first if they aren't yet
     * (the code is asked for as for a move); its answer. Off the main thread.
     */
    suspend fun ask(d: PrismDevice, request: HandoffRequest): HandoffResult = withContext(Dispatchers.IO) {
        val r = attempt(d, request)
        if (r == HandoffResult.Unpaired) attempt(d, request, repair = true) else r
    }

    /** [repair]: pair again though a key is kept (it was refused); the kept key is replaced only once that works. */
    private fun attempt(d: PrismDevice, request: HandoffRequest, repair: Boolean = false): HandoffResult {
        val socket = runCatching { Socket().apply { connect(InetSocketAddress(d.host, d.port), 5_000); tcpNoDelay = true } }.getOrNull()
            ?: return HandoffResult.Failed(tr(R.string.couldn_t_reach_is_prism_open, d.name))
        return HLink(socket).use { link ->
            runCatching {
                peer.request(link, request, HandoffExpect(device = d.id, repair = repair)) { them ->
                    val wait = CompletableDeferred<String?>()
                    code = wait
                    _asking.value = them
                    try { runBlocking { withTimeoutOrNull(HandoffPeer.PAIR_WAIT_MS.toLong() - 5_000) { wait.await() } } } finally { _asking.value = null; code = null }
                }
            }.getOrElse { HandoffResult.Failed(tr(R.string.lost_the_connection_to, d.name)) }
        }
    }

    companion object {
        private const val TAG = "PrismHandoff"
        /** 2 (1.8.0): X25519 + AES-GCM; a version 1 Prism is told both need the latest. */
        const val VERSION = 2
        const val PORT = 47321
        private const val SERVICE_LABEL = "_prismconnect"
        private const val SERVICE = "$SERVICE_LABEL._tcp"
        private const val NAME_PREFIX = "Prism · "
        private const val NOTIFY_ID = 4701
        /** The sign-in's own settings (as the backup's "Accounts" part): all a phone sends a TV. */
        val SIGN_IN_KEYS = setOf("cookie", "visitor_data", "data_sync_id", "auth_user", "account_name", "account_email", "account_avatar")
        /** How long a song gets to start here (it may still be loading) before the move counts as failed. */
        private const val START_WAIT_MS = 12_000L
        /** Connections served at once (a move, a remote's buttons, a pairing), and from one address. */
        private const val MAX_LINKS = 6
        private const val MAX_LINKS_PER_ADDRESS = 3
    }
}
