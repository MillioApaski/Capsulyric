package com.example.islandlyrics.lyrics.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperLyricTrackMatcherTest {
    private fun match(
        title: String,
        artist: String,
        parsedTitle: String,
        parsedArtist: String,
        rawTitle: String = parsedTitle,
        rawArtist: String = parsedArtist,
        publisher: String = "com.netease.cloudmusic",
        session: String = publisher
    ) = SuperLyricTrackMatcher.match(
        publisher, session, title, artist, parsedTitle, parsedArtist, rawTitle, rawArtist
    )

    @Test fun identicalTrack() {
        assertTrue(match("春を連れて", "Kotoha", "春を連れて", "Kotoha").accepted)
    }

    @Test fun chineseTitleTranslation() {
        assertTrue(match(
            "花に雨を、君に歌を", "Kotoha",
            "花に雨を、君に歌を (献花以雨，献你以歌)", "Kotoha"
        ).accepted)
    }

    @Test fun preserveFeatureCreditInsideTitle() {
        assertTrue(match(
            "私はあの花火大会に行かなかった (feat. ANRI Arcane)", "Kotoha",
            "私はあの花火大会に行かなかった (feat. ANRI Arcane) (我没有去那场烟火大会)",
            "Kotoha"
        ).accepted)
    }

    @Test fun rawMetadataRecoversTitleContainingSeparator() {
        val result = match(
            "コトノハ - Kotonoha", "tuki.",
            "コトノハ", "Kotonoha (日剧《毫无疑问是我的丈夫》主题曲)",
            "コトノハ - Kotonoha (日剧《毫无疑问是我的丈夫》主题曲)", "tuki."
        )
        assertEquals(SuperLyricTrackMatcher.MatchPath.RAW, result.path)
    }

    @Test fun differentArtistMustFail() {
        assertFalse(match("春を連れて", "Artist A", "春を連れて", "Artist B").accepted)
    }

    @Test fun neitherPrefixNorUnrelatedTranslationIsEnough() {
        assertFalse(match("春を連れて", "Kotoha", "春を連れて Again (翻译)", "Kotoha").accepted)
        assertFalse(match("Song A", "Singer", "Song B (翻译)", "Singer").accepted)
    }

    @Test fun versionAndFeaturingSuffixesAreNotDiscarded() {
        assertFalse(match("Song", "Singer", "Song (Live)", "Singer").accepted)
        assertFalse(match("Song", "Singer", "Song (feat. Guest)", "Singer").accepted)
        assertFalse(match("Song", "Singer", "Song (伴奏)", "Singer").accepted)
    }

    @Test fun doNotMixParsedTitleWithRawArtist() {
        assertFalse(match("Song", "Singer", "Song", "Other", "Other", "Singer").accepted)
    }

    @Test fun packageMustAgreeEvenWithNoSongIdentity() {
        assertFalse(match("", "", "", "", publisher = "old.player", session = "new.player").accepted)
    }

    @Test fun missingOptionalFieldsPreserveExistingBehavior() {
        assertTrue(match("", "Singer", "Song", "Singer").accepted)
        assertTrue(match("Song", "", "Song", "Singer").accepted)
    }
}
