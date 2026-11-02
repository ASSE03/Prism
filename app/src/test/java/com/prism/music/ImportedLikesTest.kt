package com.prism.music

import com.prism.music.data.LibraryRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Imported Liked Songs (1.8.1): liked in Prism, and on the YouTube Music account only when the listener turns that on
 * (owner: "not automatically, but it should be an option"). Kept off the account, a library sync must leave them liked.
 */
class ImportedLikesTest {
    @Test fun syncLeavesImportedLikesKeptOffTheAccount() {
        val before = setOf("unliked1234", "imported123", "local:42", "stillLiked1")
        val remote = setOf("stillLiked1")
        assertEquals(listOf("unliked1234"), LibraryRepository.likesToDrop(before, remote, kept = setOf("imported123")))
    }

    @Test fun withoutKeptLikesSyncIsAsBefore() {
        val before = setOf("a1", "b2", "local:7")
        assertEquals(listOf("a1"), LibraryRepository.likesToDrop(before, setOf("b2"), kept = emptySet()))
    }
}
