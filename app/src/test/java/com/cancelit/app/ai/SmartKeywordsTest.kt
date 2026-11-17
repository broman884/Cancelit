package com.cancelit.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartKeywordsTest {

    @Test
    fun normalize_keepsOnlyWords() {
        assertEquals("attack on titan", SmartKeywords.normalize("Attack-on-Titan!"))
        assertEquals("", SmartKeywords.normalize("  ?! "))
    }

    @Test
    fun worthChecking_skipsEverydayAndLongText() {
        assertFalse(SmartKeywords.worthChecking("ok see you later"))
        assertFalse(SmartKeywords.worthChecking("one two three four five six seven eight nine ten eleven"))
        assertTrue(SmartKeywords.worthChecking("naruto"))
    }

    @Test
    fun parseVerdict_readsTheTopic() {
        val topics = listOf("anime")
        val flagged = SmartKeywords.parseVerdict("""{"what_it_is":"an anime","topic":"anime"}""", topics)
        assertEquals("anime", (flagged as SmartVerdict.Flagged).topic)
        assertSame(SmartVerdict.Allowed, SmartKeywords.parseVerdict("""{"topic":"none of these"}""", topics))
        assertTrue(SmartKeywords.parseVerdict("```json\n{\"topic\":\"Anime\"}\n```", topics) is SmartVerdict.Flagged)
        assertNull(SmartKeywords.parseVerdict("not json", topics))
        assertNull(SmartKeywords.parseVerdict("""{"topic":"cooking"}""", topics))
    }

    @Test
    fun parseTerms_dropsShortCommonAndOwnWords() {
        val terms = SmartKeywords.parseTerms("""{"terms":["Naruto","One Piece","anime","ab","later","JJK","Rule 34"]}""", "anime")
        assertEquals(listOf("naruto", "one piece", "jjk", "rule34"), terms)
        assertNull(SmartKeywords.parseTerms("nope", "anime"))
    }

    @Test
    fun parseTerms_emptyListMeansNothingOfThatKind() {
        assertEquals(emptyList<String>(), SmartKeywords.parseTerms("""{"terms":[]}""", "cricket"))
    }

    @Test
    fun canonical_gluesNumbersToTheWordBefore() {
        assertEquals("rule34", SmartKeywords.canonical("Rule 34"))
        assertEquals("rule34", SmartKeywords.canonical("rule34"))
        assertEquals("csk vs mi", SmartKeywords.canonical("CSK vs MI"))
    }

    @Test
    fun matchRelated_matchesWholeWordsOnly() {
        val terms = mapOf("anime" to listOf("naruto", "one piece", "rule34"))
        assertEquals("anime", SmartKeywords.matchRelated("watching NARUTO now", terms))
        assertEquals("anime", SmartKeywords.matchRelated("One-Piece ep 1", terms))
        assertEquals("anime", SmartKeywords.matchRelated("rule 34 zero two", terms))
        assertNull(SmartKeywords.matchRelated("narutofan", terms))
    }

    @Test
    fun learnableTerm_keepsShortSpecificPhrases() {
        assertEquals("solo leveling", SmartKeywords.learnableTerm("Solo Leveling"))
        assertNull(SmartKeywords.learnableTerm("ok"))
        assertNull(SmartKeywords.learnableTerm("see you later today"))
    }
}
