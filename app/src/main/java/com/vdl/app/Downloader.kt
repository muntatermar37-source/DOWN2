package com.vdl.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** state: 0 انتظار، 1 جاري، 2 تم، 3 فشل */
data class Task(val key: String, val url: String, val title: String, val progress: Float = 0f, val state: Int = 0, val msg: String = "")

object Downloader {
    val tasks = MutableStateFlow<List<Task>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sem = Semaphore(2)

    fun isActive(url: String) = tasks.value.any { it.url == url && it.state < 2 }

    fun clearFinished() { tasks.update { l -> l.filter { it.state < 2 } } }

    fun enqueue(ctx: Context, url: String, vid: String = "", title: String = "") {
        if (isActive(url)) return
        val app = ctx.applicationContext
        val key = UUID.randomUUID().toString()
        tasks.update { it + Task(key, url, title) }
        scope.launch { sem.withPermit { run(app, key, url, vid, title) } }
    }

    private fun upd(key: String, f: (Task) -> Task) {
        tasks.update { l -> l.map { if (it.key == key) f(it) else it } }
    }

    private suspend fun waitReady() {
        while (!App.ready.value) {
            App.initError.value?.let { throw IllegalStateException("فشل تهيئة المحرك: $it") }
            delay(300)
        }
    }

    private suspend fun run(ctx: Context, key: String, url: String, vid: String, title0: String) {
        upd(key) { it.copy(state = 1) }
        val dir = File(ctx.cacheDir, "dl/$key").apply { mkdirs() }
        try {
            waitReady()
            var title = title0
            var id = vid
            if (title.isBlank() || id.isBlank()) {
                try {
                    val info = YoutubeDL.getInstance().getInfo(url)
                    if (title.isBlank()) title = info.title ?: ""
                    if (id.isBlank()) id = info.id ?: ""
                } catch (_: Exception) { }
            }
            if (id.isBlank()) id = key.take(8)
            if (title.isNotBlank()) upd(key) { it.copy(title = title) }

            val req = YoutubeDLRequest(url)
            req.addOption("-o", dir.absolutePath + "/%(id)s.%(ext)s")
            req.addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
            req.addOption("--merge-output-format", "mp4")
            req.addOption("--no-playlist")
            req.addOption("--no-mtime")
                        val ck = File(ctx.filesDir, "cookies.txt")
            if (ck.exists()) req.addOption("--cookies", ck.absolutePath)
                        var attempt = 0
            while (true) {
                try {
                    YoutubeDL.getInstance().execute(req, key) { p, _, _ ->
                        upd(key) { it.copy(progress = (p / 100f).coerceIn(0f, 1f)) }
                    }
                    break
                } catch (e: Exception) {
                    attempt++
                    if (attempt >= 3) throw e
                    delay(2000)
                }
            }

            val f = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() } ?: throw IllegalStateException("لم يتم العثور على الملف المنزّل")
            val uri = saveToGallery(ctx, f, title.ifBlank { id }, id)
            Store.addLib(LibItem(uri.toString(), title.ifBlank { id }, platformOf(url), id, System.currentTimeMillis()))
            upd(key) { it.copy(state = 2, progress = 1f) }
        } catch (e: Exception) {
            upd(key) { it.copy(state = 3, msg = (e.message ?: "خطأ").takeLast(250)) }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun saveToGallery(ctx: Context, f: File, title: String, id: String): Uri {
        val ext = f.extension.lowercase().ifEmpty { "mp4" }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"
        val clean = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ").trim().take(60)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$clean [$id].$ext")
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoDownloader")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val r = ctx.contentResolver
        val uri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("تعذر الحفظ في الاستوديو")
        r.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        r.update(uri, values, null, null)
        return uri
    }

    fun platformOf(url: String): String {
        val u = url.lowercase()
        return when {
            "tiktok" in u -> "TikTok"
            "instagram" in u -> "Instagram"
            "youtu" in u -> "YouTube"
            "twitter.com" in u || "x.com" in u -> "X"
            "facebook" in u || "fb.watch" in u -> "Facebook"
            else -> runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
        }
    }
        private fun fetchSecUid(profileUrl: String): String? = try {
        val c = java.net.URL(profileUrl).openConnection() as java.net.HttpURLConnection
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        c.connectTimeout = 15000
        c.readTimeout = 15000
        val html = c.inputStream.bufferedReader().readText()
        Regex("\"secUid\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
    } catch (e: Exception) { null }
    /** يجلب قائمة فيديوهات حساب تيك توك (بدون تنزيل) */
    suspend fun fetchAccount(url: String): List<VideoEntry> = withContext(Dispatchers.IO) {
        waitReady()
        val req = YoutubeDLRequest(url)
        req.addOption("--flat-playlist")
        req.addOption("-j")
        val ck = File(App.instance.filesDir, "cookies.txt")
        if (ck.exists()) req.addOption("--cookies", ck.absolutePath)
                val out = try {
            YoutubeDL.getInstance().execute(req, "acc-" + UUID.randomUUID()).out
        } catch (e: Exception) {
            val sec = fetchSecUid(url) ?: throw e
            val r2 = YoutubeDLRequest("tiktokuser:$sec")
            r2.addOption("--flat-playlist")
            r2.addOption("-j")
            YoutubeDL.getInstance().execute(r2, "acc-" + UUID.randomUUID()).out
        }
        val list = out.lines().filter { it.trim().startsWith("{") }.mapNotNull { line ->
            try {
                val o = JSONObject(line)
                val id = o.optString("id")
                var u = o.optString("url")
                if (u.isBlank()) u = o.optString("webpage_url")
                if (id.isBlank() || !u.startsWith("http")) return@mapNotNull null
                var thumb = o.optString("thumbnail")
                val th = o.optJSONArray("thumbnails")
                if (thumb.isBlank() && th != null && th.length() > 0) thumb = th.getJSONObject(th.length() - 1).optString("url")
                var t = o.optString("title")
                if (t.isBlank()) t = o.optString("description")
                VideoEntry(id, u, t, o.optDouble("duration", 0.0).toInt(), thumb)
            } catch (e: Exception) { null }
        }
        if (list.isEmpty()) throw IllegalStateException("لم يتم العثور على فيديوهات. تأكد من الحساب أو حدّث المحرك من الإعدادات")
        list
    }
}
