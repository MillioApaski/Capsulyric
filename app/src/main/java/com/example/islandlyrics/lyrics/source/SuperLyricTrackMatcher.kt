package com.example.islandlyrics.lyrics.source

/** Validates a SuperLyric publisher against ONE coherent pair of MediaSession fields. */
internal object SuperLyricTrackMatcher {
    enum class MatchPath { PARSED, RAW, MISMATCH, PACKAGE_MISMATCH }

    data class Result(val path: MatchPath) {
        val accepted: Boolean get() = path == MatchPath.PARSED || path == MatchPath.RAW
    }

    private val cjkCharacter = Regex("[\\u3040-\\u30FF\\u3400-\\u9FFF]")
    private val latinCharacter = Regex("[A-Za-z]")
    private val versionMarker = Regex(
        """(?i)\b(?:feat\.?|ft\.?|live|remix|mix|version|ver\.?|edit|instrumental|acoustic|cover|demo|radio|explicit|sped|slowed)\b|伴奏|现场|現場|翻唱|纯音乐|純音楽|演奏|重制|重製|版本|完整版"""
    )

    fun match(
        publisherPackage: String,
        sessionPackage: String,
        providedTitle: String,
        providedArtist: String,
        parsedTitle: String,
        parsedArtist: String,
        rawTitle: String,
        rawArtist: String
    ): Result {
        if (publisherPackage.isBlank() || publisherPackage != sessionPackage) {
            return Result(MatchPath.PACKAGE_MISMATCH)
        }
        if (pairMatches(providedTitle, providedArtist, parsedTitle, parsedArtist)) {
            return Result(MatchPath.PARSED)
        }
        // Never mix a title from one source with an artist from another: parser rules can
        // accidentally turn the second half of a real title into a fake artist.
        if (pairMatches(providedTitle, providedArtist, rawTitle, rawArtist)) {
            return Result(MatchPath.RAW)
        }
        return Result(MatchPath.MISMATCH)
    }

    private fun pairMatches(title: String, artist: String, candidateTitle: String, candidateArtist: String): Boolean =
        (title.isBlank() || (candidateTitle.isNotBlank() && titleMatches(title, candidateTitle))) &&
            (artist.isBlank() || (candidateArtist.isNotBlank() && normalize(artist).equals(normalize(candidateArtist), ignoreCase = true)))

    internal fun titleMatches(first: String, second: String): Boolean {
        val left = normalize(first)
        val right = normalize(second)
        if (left.equals(right, ignoreCase = true)) return true
        // A single trailing translation/description may be omitted. Its parentheses
        // can contain nested parentheses (e.g. translated anime theme descriptions).
        // Keep actual title suffixes such as '(feat. ...)', '(Live)', '(Remix)'.
        return removeExplanatorySuffix(left)?.equals(right, ignoreCase = true) == true ||
            removeExplanatorySuffix(right)?.equals(left, ignoreCase = true) == true
    }

    private fun removeExplanatorySuffix(title: String): String? {
        if (title.isEmpty() || (title.last() != ')' && title.last() != '）')) return null

        // Walk backwards to find the opening bracket of the ONE outermost trailing
        // annotation. A flat regex incorrectly rejects '(译名 (主题曲说明))'.
        // Track the bracket type so malformed/mismatched brackets cannot be stripped.
        val expectedOpen = ArrayList<Char>()
        for (index in title.lastIndex downTo 0) {
            when (title[index]) {
                ')' -> expectedOpen.add('(')
                '）' -> expectedOpen.add('（')
                '(', '（' -> {
                    if (expectedOpen.isEmpty() || expectedOpen.removeAt(expectedOpen.lastIndex) != title[index]) {
                        return null
                    }
                    if (expectedOpen.isEmpty()) {
                        val base = title.substring(0, index).trimEnd().takeIf { it.isNotBlank() } ?: return null
                        val annotation = title.substring(index + 1, title.lastIndex).trim()
                        if (annotation.isBlank() || versionMarker.containsMatchIn(annotation)) return null

                        // CJK subtitle/description, or an English alternate title
                        // attached to an original Japanese/Chinese/Korean-script title.
                        // Plain English '(Live)' / '(Acoustic)' and arbitrary prefixes
                        // must never become accepted through fuzzy matching.
                        val explanatory = cjkCharacter.containsMatchIn(annotation) ||
                            (cjkCharacter.containsMatchIn(base) && latinCharacter.containsMatchIn(annotation))
                        return base.takeIf { explanatory }
                    }
                }
            }
        }
        return null
    }

    private fun normalize(value: String) = value.trim().replace(Regex("""\s+"""), " ")
}
