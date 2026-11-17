package com.cancelit.app.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException

// Every list CancelIt saves is one JSON array in one SharedPreferences string, so no label,
// message or keyword can break the format, whatever characters it holds.

/** A list as a JSON array. [write] turns an item into a string, a number, a boolean or a JSONArray. */
internal fun <T> encodeList(items: List<T>, write: (T) -> Any): String =
    JSONArray().apply { items.forEach { put(write(it)) } }.toString()

/** Reads a list saved by [encodeList]. Missing or unreadable data reads as an empty list, and
 *  an entry [read] can't parse is skipped. */
internal fun <T : Any> decodeList(raw: String?, read: (JSONArray, Int) -> T): List<T> {
    if (raw.isNullOrEmpty()) return emptyList()
    val array = try {
        JSONArray(raw)
    } catch (e: JSONException) {
        return emptyList()
    }
    return (0 until array.length()).mapNotNull { i -> runCatching { read(array, i) }.getOrNull() }
}

internal fun <T : Any> SharedPreferences.getList(key: String, read: (JSONArray, Int) -> T): List<T> =
    decodeList(getString(key, null), read)

internal fun <T> SharedPreferences.Editor.putList(key: String, items: List<T>, write: (T) -> Any): SharedPreferences.Editor =
    putString(key, encodeList(items, write))
