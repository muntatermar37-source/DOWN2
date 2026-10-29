package com.vdl.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun parseAccount(input: String): Account? {
    val m = Regex("@([A-Za-z0-9._]+)").find(input)
    val name = (m?.groupValues?.get(1) ?: input.trim().removePrefix("@")).trim()
    if (name.isEmpty() || name.contains(" ") || name.contains("/")) return null
    return Account(name, "https://www.tiktok.com/@$name", 0)
}

@Composable
fun AccountsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by Store.accounts.collectAsState()
    val lib by Store.library.collectAsState()
    val tasks by Downloader.tasks.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableIntStateOf(0) }
    var ver by remember { mutableIntStateOf(0) }
    val df = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }

    val acc = accounts.find { it.name == selected } ?: accounts.firstOrNull()
    val videos = remember(acc?.name, ver) { acc?.let { Store.videosOf(it.name) } ?: emptyList() }
    val done = lib.associateBy { it.vid }
    val remaining = videos.filter { done[it.id] == null }
    val shown = when (filter) {
        1 -> videos.filter { done[it.id] != null }
        2 -> remaining
        else -> videos
    }

    fun refresh(a: Account) {
        if (loading) return
        scope.launch {
            loading = true; error = null
            try {
                Store.saveVideos(a, Downloader.fetchAccount(a.url))
                ver++
            } catch (e: Exception) {
                error = (e.message ?: "خطأ").takeLast(300)
            }
            loading = false
        }
    }

    if (showAdd) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("إضافة حساب تيك توك") },
            text = {
                OutlinedTextField(text, { text = it }, label = { Text("الرابط أو @اسم_المستخدم") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = parseAccount(text)
                    if (a != null) {
                        Store.addAccount(a); selected = a.name; showAdd = false; refresh(a)
                    }
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎵 تيك توك", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("إضافة حساب") }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(accounts, key = { it.name }) { a ->
                val thumb = remember(a.name, a.updated) { Store.videosOf(a.name).firstOrNull()?.thumb }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selected = a.name }) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Card2)
                            .border(if (a.name == acc?.name) 3.dp else 0.dp, Purple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!thumb.isNullOrBlank()) AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(a.name.take(1).uppercase(), fontSize = 24.sp)
                    }
                    Text("@${a.name}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(70.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (acc == null) {
            Text("أضف حساب تيك توك حتى تظهر فيديوهاته هنا", color = Color.Gray)
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("@${acc.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { Store.removeAccount(acc.name); selected = null }) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B))
            }
        }
        val upd = if (acc.updated > 0) df.format(Date(acc.updated)) else "-"
        Text("${videos.size} فيديو • منزّل ${videos.size - remaining.size} • آخر تحديث $upd", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = remaining.isNotEmpty(),
                onClick = { remaining.forEach { Downloader.enqueue(ctx, it.url, it.id, it.title) } }
            ) { Text("تنزيل الباقي (${remaining.size})") }
            OutlinedButton(enabled = !loading, onClick = { refresh(acc) }) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp)); Text("تحديث")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf("الكل", "المنزّلة", "غير المنزّلة").forEachIndexed { i, l ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l, fontSize = 12.sp) })
            }
        }
        error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp) }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(105.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(shown, key = { it.id }) { v ->
                val d = done[v.id]
                val busy = tasks.any { it.url == v.url && it.state < 2 }
                VideoCard(v, d, busy) {
                    if (d != null) play(ctx, d.uri) else Downloader.enqueue(ctx, v.url, v.id, v.title)
                }
            }
        }
    }
}

@Composable
fun VideoCard(v: VideoEntry, done: LibItem?, busy: Boolean, onClick: () -> Unit) {
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp))
                .background(Card2).clickable(onClick = onClick)
        ) {
            val model: Any? = if (v.thumb.isNotBlank()) v.thumb else done?.uri
            if (model != null) AsyncImage(model, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                when {
                    done != null -> Surface(color = Green, shape = RoundedCornerShape(50)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp)); Text("تشغيل", fontSize = 11.sp)
                        }
                    }
                    busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> Surface(color = Color(0x99000000), shape = CircleShape) {
                        Icon(Icons.Default.Download, null, Modifier.padding(6.dp).size(18.dp))
                    }
                }
            }
            if (v.duration > 0) Surface(
                color = Color(0x99000000), shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
            ) { Text(fmtDur(v.duration), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        }
        Text(v.title.ifBlank { "(بدون وصف)" }, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
    }
}
