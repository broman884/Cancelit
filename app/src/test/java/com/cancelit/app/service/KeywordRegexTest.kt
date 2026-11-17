package com.cancelit.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordRegexTest {

    private fun matches(keyword: String, typed: String) = keywordRegex(keyword).containsMatchIn(typed)

    @Test
    fun longKeywords_matchAtTheStartOfAWord() {
        assertTrue(matches("anime", "best Anime 2026"))
        assertTrue(matches("anime", "animes like naruto"))
        assertTrue(matches("porn", "pornhub"))
        assertFalse(matches("anime", "brainanime"))
    }

    @Test
    fun shortKeywords_onlyMatchAsWholeWords() {
        assertTrue(matches("ass", "nice ass"))
        assertTrue(matches("cat", "cute cats"))
        assertTrue(matches("sex", "SEX"))
        assertFalse(matches("ass", "math assignment"))
        assertFalse(matches("cat", "category theory"))
        assertFalse(matches("sex", "sussex university"))
    }

    @Test
    fun keywordsAreLiteralText() {
        assertTrue(matches("c++", "learn c++ fast"))
        assertFalse(matches("a.b", "axb"))
    }
}
