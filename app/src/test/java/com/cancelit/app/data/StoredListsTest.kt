package com.cancelit.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredListsTest {

    @Test
    fun stringsRoundTripWhateverTheyHold() {
        val words = listOf("a|b", "line\nbreak", "quote\"", "::;;", "\u0001")
        val raw = encodeList(words) { it }
        assertEquals(words, decodeList(raw) { array, i -> array.getString(i) })
    }

    @Test
    fun recordsRoundTrip() {
        val events = listOf(ActivityRecord(1L, "YouTube", "locked down"), ActivityRecord(2L, "Study mode", "On track"))
        val raw = encodeList(events) { JSONArray().put(it.time).put(it.label).put(it.detail) }
        val back = decodeList(raw) { array, i ->
            array.getJSONArray(i).let { ActivityRecord(it.getLong(0), it.getString(1), it.getString(2)) }
        }
        assertEquals(events, back)
    }

    @Test
    fun missingOrUnreadableDataIsAnEmptyList() {
        assertEquals(emptyList<String>(), decodeList(null) { array, i -> array.getString(i) })
        assertEquals(emptyList<String>(), decodeList("") { array, i -> array.getString(i) })
        assertEquals(emptyList<String>(), decodeList("anime\nporn") { array, i -> array.getString(i) })
    }

    @Test
    fun badEntriesAreSkipped() {
        val read = decodeList("""[["x","y"],"oops",["z","w"]]""") { array, i -> array.getJSONArray(i).getString(0) }
        assertEquals(listOf("x", "z"), read)
    }
}
