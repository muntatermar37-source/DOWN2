package com.vdl.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LibItem(val uri: String, val title: String, val platform: String, val vid: String, val date: Long)
data class VideoEntry(val id: String, val url: String, val title: String, val duration: Int, val thumb: String)
data class Account(val name: String, val url: String, val updated: Long)

object Store {
    private lateinit var sp: SharedPreferences
    val library = MutableStateFlow<List<LibItem>>(emptyList())
    val accounts = MutableStateFlow<List<Account>>(emptyList())

    fun init(c: Context) {
        sp = c.getSharedPreferences("vdl", Context.MODE_PRIVATE)
        library.value = readArr("lib").map {
            LibItem(it.getString("uri"), it.optString("title"), it.optString("platform"), it.optString("vid"), it.optLong("date"))
        }
        accounts.value = readArr("acc").map { Account(it.getString("name"), it.getString("url"), it.optLong("updated")) }
    }

    private fun readArr(key: String): List<JSONObject> {
        val a = try { JSONArray(sp.getString(key, "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun writeArr(key: String, items: List<JSONObject>) {
        sp.edit().putString(key, JSONArray(items).toString()).apply()
    }

    private fun persistLib() = writeArr("lib", library.value.map {
        JSONObject().put("uri", it.uri).put("title", it.title).put("platform", it.platform).put("vid", it.vid).put("date", it.date)
    })

    private fun persistAcc() = writeArr("acc", accounts.value.map {
        JSONObject().put("name", it.name).put("url", it.url).put("updated", it.updated)
    })

    fun addLib(item: LibItem) {
        library.value = listOf(item) + library.value
        persistLib()
    }

    fun removeLib(uri: String) {
        library.value = library.value.filter { it.uri != uri }
        persistLib()
    }

    fun addAccount(a: Account) {
        if (accounts.value.none { it.name.equals(a.name, true) }) {
            accounts.value = accounts.value + a
            persistAcc()
        }
    }

    fun removeAccount(name: String) {
        accounts.value = accounts.value.filter { it.name != name }
        sp.edit().remove("vid_$name").apply()
        persistAcc()
    }

    fun saveVideos(a: Account, list: List<VideoEntry>) {
        writeArr("vid_${a.name}", list.map {
            JSONObject().put("id", it.id).put("url", it.url).put("title", it.title).put("d", it.duration).put("t", it.thumb)
        })
        accounts.value = accounts.value.map { if (it.name == a.name) it.copy(updated = System.currentTimeMillis()) else it }
        persistAcc()
    }

    fun videosOf(name: String): List<VideoEntry> = readArr("vid_$name").map {
        VideoEntry(it.getString("id"), it.getString("url"), it.optString("title"), it.optInt("d"), it.optString("t"))
    }
}
