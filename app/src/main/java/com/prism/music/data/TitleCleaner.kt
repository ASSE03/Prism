package com.prism.music.data

/**
 * Takes out of a song's title what only describes YouTube's upload, not the song: "(Clip officiel)",
 * "[Official Music Video]", "(Lyrics)", "(Audio officiel)", "| Vidéo officielle", " - Official Video"…
 * Anything else in brackets stays: "(Live à Bercy)", "(feat. X)", "(Remix)", "(Version acoustique)" name the recording.
 * Never leaves a title empty.
 */
object TitleCleaner {
    /** Words that, all together inside brackets or after a dash, only say what kind of upload it is. */
    private val UPLOAD_WORDS = setOf(
        // English
        "official", "officially", "music", "video", "videoclip", "audio", "lyric", "lyrics", "visualizer", "visualiser",
        "hd", "hq", "4k", "8k", "uhd", "mv", "clip", "remastered4k", "full", "new",
        // Français
        "officiel", "officielle", "officiels", "officielles", "vidéo", "vidéoclip", "vidéoclips", "clips", "paroles",
        "musical", "musicale", "audios", "en", "et", "avec", "les", "le", "la", "&", "+",
        // Español / Português / Italiano / Deutsch
        "oficial", "ufficiale", "offizielles", "offizielle", "musikvideo", "letra", "testo",
    )

    /** At least one of these must be there, so "(En)" or "(Le)" on their own aren't taken for upload labels. */
    private val KEY_WORDS = setOf(
        "official", "officially", "video", "videoclip", "audio", "lyric", "lyrics", "visualizer", "visualiser", "hd", "hq", "4k", "8k",
        "uhd", "mv", "clip", "clips", "officiel", "officielle", "officiels", "officielles", "vidéo", "vidéoclip", "vidéoclips",
        "paroles", "oficial", "ufficiale", "offizielles", "offizielle", "musikvideo", "letra", "testo",
    )

    private val BRACKETS = Regex("\\s*[(\\[【「][^)\\]】」]*[)\\]】」]")
    /** " - Clip officiel", " | Official Video", " // Vidéo officielle" at the end. */
    private val TAIL = Regex("\\s+(?:[-–—|/]{1,2})\\s+([^-–—|/]+)$")

    private fun isUploadLabel(text: String): Boolean {
        val words = text.lowercase().trim().split(Regex("[\\s,.:;_/|\\-]+")).filter { it.isNotBlank() }
        return words.isNotEmpty() && words.all { it in UPLOAD_WORDS } && words.any { it in KEY_WORDS }
    }

    fun clean(title: String): String {
        if (title.isBlank()) return title
        var t = title.replace(BRACKETS) { m ->
            val inside = m.value.trim().trim('(', ')', '[', ']', '【', '】', '「', '」')
            if (isUploadLabel(inside)) "" else m.value
        }
        // A tail after a dash or a bar, once or twice ("Titre - Clip officiel | HD").
        repeat(2) {
            val m = TAIL.find(t) ?: return@repeat
            if (isUploadLabel(m.groupValues[1])) t = t.substring(0, m.range.first)
        }
        t = t.replace(Regex("\\s{2,}"), " ").trim().trimEnd('-', '–', '—', '|', '/').trim()
        return t.ifBlank { title }
    }
}
