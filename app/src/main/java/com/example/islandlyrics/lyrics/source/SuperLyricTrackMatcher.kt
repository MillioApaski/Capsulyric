package com.example.islandlyrics.lyrics.source

/** Validates a SuperLyric publisher against ONE coherent pair of MediaSession fields. */
internal object SuperLyricTrackMatcher {
    enum class MatchPath { PARSED, RAW, MISMATCH, PACKAGE_MISMATCH }

    data class Result(val path: MatchPath) {
        val accepted: Boolean get() = path == MatchPath.PARSED || path == MatchPath.RAW
    }

    private val trailingAnnotation = Regex("""\s*[（(]([^（）()]+)[）)]\s*$""")
    private val hanCharacter = Regex("[\\u3400-\\u9FFF]")
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
        (title.isBlank() || candidateTitle.isBlank() || titleMatches(title, candidateTitle)) &&
            (artist.isBlank() || candidateArtist.isBlank() || normalize(artist).equals(normalize(candidateArtist), ignoreCase = true))

    internal fun titleMatches(first: String, second: String): Boolean {
        val left = normalize(first)
        val right = normalize(second)
        if (left.equals(right, ignoreCase = true)) return true
        // Only a single *trailing* explanatory CJK annotation may be omitted. A title's
        // actual '(feat. ...)', '(Live)', '(Remix)', etc. must remain part of its identity.
        return removeExplanatorySuffix(left)?.equals(right, ignoreCase = true) == true ||
            removeExplanatorySuffix(right)?.equals(left, ignoreCase = true) == true
    }

    private fun removeExplanatorySuffix(title: String): String? {
        val match = trailingAnnotation.find(title) ?: return null
        val annotation = match.groupValues[1]
        if (!hanCharacter.containsMatchIn(annotation) || versionMarker.containsMatchIn(annotation)) return null
        return title.substring(0, match.range.first).trimEnd().takeIf { it.isNotBlank() }
    }

    private fun normalize(value: String) = value.trim().replace(Regex("""\s+"""), " ")
}
