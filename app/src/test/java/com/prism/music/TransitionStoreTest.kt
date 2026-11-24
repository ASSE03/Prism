package com.prism.music

import android.os.Looper
import androidx.room.Room
import com.prism.music.data.TransitionStore
import com.prism.music.data.db.PrismDatabase
import com.prism.music.data.db.TransitionEntity
import com.prism.music.playback.Transition
import com.prism.music.playback.TransitionStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 1.8.8 Mix it yourself (plan-mix.md §4.2, C1): the transitions table, its store, and the yearly prune. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TransitionStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PrismDatabase::class.java).allowMainThreadQueries().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 1_000_000_000_000L
    private val store = TransitionStore(db.transitions(), scope) { clock }

    @After fun close() { scope.cancel(); db.close() }

    private val t = Transition(170_000, 12_000, 8_000, TransitionStyle.GENTLE, snap = true)

    @Test fun savedComesBackForThatPairInThatOrderOnly() = runBlocking {
        store.save("PL1", "a", "b", t)
        assertEquals(t, store.get("PL1", "a", "b"))
        assertNull("the other way round is another pair", store.get("PL1", "b", "a"))
        assertNull("another playlist has its own", store.get("PL2", "a", "b"))
        // Saved again, it's changed in place (one row).
        store.save("PL1", "a", "b", t.copy(lengthMs = 0))
        assertEquals(0L, store.get("PL1", "a", "b")!!.lengthMs)
        assertEquals(1, db.transitions().count())
    }

    @Test fun resetClearsTheRow() = runBlocking {
        store.save("PL1", "a", "b", t)
        store.reset("PL1", "a", "b")
        assertNull(store.get("PL1", "a", "b"))
        assertEquals(0, db.transitions().count())
    }

    @Test fun aDeletedPlaylistTakesItsTransitionsWithIt() = runBlocking {
        store.save("PLyt", "a", "b", t)
        store.save("prism:1", "a", "b", t)
        store.save("PLother", "a", "b", t)
        // A YouTube playlist is deleted by its browse id ("VL…"); the queue knew it without.
        store.forgetPlaylist("VLPLyt")
        store.forgetPlaylist("prism:1")
        assertNull(store.get("PLyt", "a", "b"))
        assertNull(store.get("prism:1", "a", "b"))
        assertEquals(t, store.get("PLother", "a", "b"))
    }

    @Test fun rowsUnusedForAYearArePruned() = runBlocking {
        val year = TransitionStore.UNUSED_FOR_MS
        db.transitions().put(TransitionEntity("PL1", "old", "x", 1, 2, 3, "auto", false, changedAt = clock - year - 1, usedAt = 0))
        db.transitions().put(TransitionEntity("PL1", "played", "x", 1, 2, 3, "auto", false, changedAt = clock - 3 * year, usedAt = clock - 10))
        db.transitions().put(TransitionEntity("PL1", "new", "x", 1, 2, 3, "auto", false, changedAt = clock - 10))
        assertEquals(1, store.prune())
        assertNull(store.get("PL1", "old", "x"))
        assertTrue(store.get("PL1", "played", "x") != null && store.get("PL1", "new", "x") != null)
    }

    @Test fun playingOneMarksItUsed() = runBlocking {
        store.save("PL1", "a", "b", t)
        clock += 5_000
        store.used("PL1", "a", "b")
        waitFor { runBlocking { db.transitions().get("PL1", "a", "b")!!.usedAt } == clock }
        // A later edit keeps when it last played.
        store.save("PL1", "a", "b", t.copy(inMs = 1))
        assertEquals(clock, db.transitions().get("PL1", "a", "b")!!.usedAt)
    }

    @Test fun theEngineAsksFromMemoryAndSeesNewRows() = runBlocking {
        store.save("PL1", "a", "b", t)
        // The first ask starts the playlist being read; it isn't waited for.
        assertNull(store.cached("PL1", "a", "b"))
        waitFor { store.cached("PL1", "a", "b") == t }
        assertNull(store.cached("PL1", "b", "a"))
        store.save("PL1", "b", "c", t)
        waitFor { store.cached("PL1", "b", "c") == t }
        store.reset("PL1", "a", "b")
        waitFor { store.cached("PL1", "a", "b") == null }
    }

    @Test fun thePlaylistsPairsInOneQuery() = runBlocking {
        store.save("PL1", "a", "b", t)
        store.save("PL1", "c", "d", t)
        store.save("PL2", "x", "y", t)
        assertEquals(setOf("a" to "b", "c" to "d"), store.pairs("PL1").first())
    }

    /**
     * The editor's Save and Reset each start their own coroutine. A save reads the row, then writes it: a Reset that
     * deleted the row between the two was undone by the save's write, and the row lived on after Reset.
     */
    @Test fun aResetAfterASaveWinsWhateverTheTiming() = runBlocking {
        val real = db.transitions()
        val readDone = kotlinx.coroutines.CompletableDeferred<Unit>()
        val goOn = kotlinx.coroutines.CompletableDeferred<Unit>()
        val gated = object : com.prism.music.data.db.TransitionDao() {
            override fun forPlaylistFlow(playlistId: String) = real.forPlaylistFlow(playlistId)
            override suspend fun forPlaylist(playlistId: String) = real.forPlaylist(playlistId)
            override suspend fun get(playlistId: String, fromId: String, toId: String): TransitionEntity? {
                val row = real.get(playlistId, fromId, toId)
                // The save's read is held here, as a slow database would.
                if (!readDone.isCompleted) { readDone.complete(Unit); goOn.await() }
                return row
            }
            override suspend fun put(t: TransitionEntity) = real.put(t)
            override suspend fun putAll(rows: List<TransitionEntity>) = real.putAll(rows)
            override suspend fun delete(playlistId: String, fromId: String, toId: String) = real.delete(playlistId, fromId, toId)
            override suspend fun deletePlaylist(ids: List<String>) = real.deletePlaylist(ids)
            override suspend fun used(playlistId: String, fromId: String, toId: String, at: Long) = real.used(playlistId, fromId, toId, at)
            override suspend fun pruneBefore(before: Long) = real.pruneBefore(before)
            override suspend fun count() = real.count()
            override suspend fun all() = real.all()
            override suspend fun clear() = real.clear()
        }
        val s = TransitionStore(gated, scope) { clock }
        val save = scope.launch { s.save("PL1", "a", "b", t) }
        readDone.await()
        // Reset is pressed while the save is between its read and its write.
        val reset = scope.launch { s.reset("PL1", "a", "b") }
        kotlinx.coroutines.delay(200)
        goOn.complete(Unit)
        save.join(); reset.join()
        assertNull("reset is the last word", real.get("PL1", "a", "b"))
    }

    /** 1.8.8: the transitions go with a backup's Library as a file, and a restore's file replaces this phone's, once. */
    @Test fun aRestoredFileReplacesTheRowsOnce() = runBlocking {
        store.save("PL1", "a", "b", t)
        val theirs = TransitionEntity("PL9", "x", "y", 1, 2, 3, "cut", false, changedAt = 4, usedAt = 5)
        val f = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "transitions-${System.nanoTime()}.json")
        f.writeText(TransitionStore.exportJson(com.prism.music.data.TransitionsFile(listOf(theirs))))
        store.readPending(f)
        assertEquals(listOf(theirs), store.export().rows)
        assertTrue("read once", !f.exists())
        store.readPending(f)
        assertEquals(listOf(theirs), store.export().rows)
    }

    @Test fun aStyleNoLongerOfferedPlaysAsAutomatic() {
        assertEquals(TransitionStyle.AUTOMATIC, TransitionStyle.of("struck_move"))
        assertEquals(TransitionStyle.AUTOMATIC, TransitionStyle.of(null))
        for (s in TransitionStyle.offered) assertEquals(s, TransitionStyle.of(s.key))
        assertEquals("keys are unique", TransitionStyle.entries.size, TransitionStyle.entries.map { it.key }.toSet().size)
    }

    private fun waitFor(what: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!what()) {
            assertTrue("never came", System.currentTimeMillis() < until)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }
}
