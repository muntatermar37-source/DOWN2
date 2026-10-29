package com.vdl.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

object Shared {
    val link = mutableStateOf("")
    val tab = mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val t = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            Regex("https?://\\S+").find(t)?.value?.let {
                Shared.link.value = it
                Shared.tab.intValue = 0
            }
        }
    }
}

val Bg = Color(0xFF140F26)
val Card2 = Color(0xFF2A2145)
val Purple = Color(0xFFA06CD5)
val Green = Color(0xFF1FA463)

@Composable
fun AppRoot() {
    val scheme = darkColorScheme(
        primary = Purple, onPrimary = Color.White, background = Bg, surface = Color(0xFF1E1836),
        surfaceVariant = Card2, onBackground = Color.White, onSurface = Color.White,
        secondaryContainer = Color(0xFF3B2A63)
    )
    val items = listOf(
        "تنزيل" to Icons.Default.Download,
        "تيك توك" to Icons.Default.Person,
        "تنزيلاتي" to Icons.Default.VideoLibrary,
        "الإعدادات" to Icons.Default.Settings
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    NavigationBar {
                        items.forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = Shared.tab.intValue == i,
                                onClick = { Shared.tab.intValue = i },
                                icon = { Icon(icon, null) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (Shared.tab.intValue) {
                        0 -> HomeScreen()
                        1 -> AccountsScreen()
                        2 -> LibraryScreen()
                        else -> SettingsScreen()
                    }
                }
            }
        }
    }
}

fun play(ctx: Context, uri: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (_: Exception) { }
}

fun fmtDur(s: Int) = "%d:%02d".format(s / 60, s % 60)

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val tasks by Downloader.tasks.collectAsState()
    val ready by App.ready.collectAsState()
    val err by App.initError.collectAsState()
    var link by Shared.link

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("تنزيل فيديو من رابط", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("إنستغرام • يوتيوب • تيك توك • X • فيسبوك وغيرها", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = link, onValueChange = { link = it },
            label = { Text("ألصق الرابط هنا") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.text?.let { link = it.toString().trim() }
                }) { Icon(Icons.Default.ContentPaste, null) }
            }
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { Downloader.enqueue(ctx, link.trim()); link = "" },
            enabled = link.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("تنزيل") }
        if (!ready) {
            Spacer(Modifier.height(8.dp))
            Text(err?.let { "فشل تهيئة المحرك: $it" } ?: "جاري تهيئة المحرك (أول مرة تاخذ شوية)…", fontSize = 12.sp, color = Color(0xFFFFB74D))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("التحميلات", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { Downloader.clearFinished() }) { Text("مسح المنتهية") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks.reversed(), key = { it.key }) { t ->
                Card(colors = CardDefaults.cardColors(containerColor = Card2)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(t.title.ifBlank { t.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        when (t.state) {
                            0 -> Text("بالانتظار…", fontSize = 12.sp, color = Color.Gray)
                            1 -> {
                                LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
                                Text("${(t.progress * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray)
                            }
                            2 -> Text("✔ تم الحفظ بالاستوديو", fontSize = 12.sp, color = Green)
                            else -> Text("✘ ${t.msg}", fontSize = 12.sp, color = Color(0xFFFF6B6B))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val lib by Store.library.collectAsState()
    val df = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("تنزيلاتي (${lib.size})", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (lib.isEmpty()) Text("ماكو فيديوهات منزّلة للحين", color = Color.Gray)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lib, key = { it.uri }) { it ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Card2),
                    onClick = { play(ctx, it.uri) }
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = it.uri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 64.dp, height = 90.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${it.platform} • ${df.format(Date(it.date))}", fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(onClick = {
                            try { ctx.contentResolver.delete(Uri.parse(it.uri), null, null) } catch (_: Exception) { }
                            Store.removeLib(it.uri)
                        }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B)) }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rel by remember { mutableStateOf<Updater.Rel?>(null) }
    var appMsg by remember { mutableStateOf("") }
    var engMsg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var prog by remember { mutableFloatStateOf(0f) }
    var engine by remember { mutableStateOf("-") }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            engMsg = try {
                ctx.contentResolver.openInputStream(uri)!!.use { i ->
                    File(ctx.filesDir, "cookies.txt").outputStream().use { o -> i.copyTo(o) }
                }
                "تم استيراد الكوكيز ✔"
            } catch (e: Exception) { "فشل: ${e.message}" }
        }
    }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            engMsg = try {
                ctx.contentResolver.openInputStream(uri)!!.use { i ->
                    File(ctx.filesDir, "cookies.txt").outputStream().use { o -> i.copyTo(o) }
                }
                "تم استيراد الكوكيز ✔"
            } catch (e: Exception) { "فشل: ${e.message}" }
        }
    }

    LaunchedEffect(Unit) {
        engine = withContext(Dispatchers.IO) {
            runCatching { YoutubeDL.getInstance().version(ctx) }.getOrNull() ?: "-"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("الإعدادات", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = Card2)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تحديث التطبيق", fontWeight = FontWeight.Bold)
                Text("الإصدار الحالي: ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = Color.Gray)
                if (appMsg.isNotBlank()) Text(appMsg, fontSize = 13.sp)
                if (prog > 0f && prog < 1f) LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true; prog = 0f
                        try {
                            val r = rel ?: Updater.latest().also { rel = it }
                            if (r == null) {
                                appMsg = "ماكو إصدار منشور على GitHub"
                            } else if (r.code <= BuildConfig.VERSION_CODE) {
                                appMsg = "أنت على آخر إصدار ✔"
                            } else {
                                appMsg = "جاري تنزيل الإصدار ${r.code}…"
                                val f = Updater.download(ctx, r) { prog = it }
                                appMsg = "جاري التثبيت…"
                                Updater.install(ctx, f)
                            }
                        } catch (e: Exception) {
                            appMsg = "فشل: ${e.message}"
                        }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text("فحص وتحديث التطبيق")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Card2)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("محرك التحميل (yt-dlp)", fontWeight = FontWeight.Bold)
                Text("النسخة: $engine", fontSize = 13.sp, color = Color.Gray)
                Text("إذا موقع وقف يحمّل، حدّث المحرك — بدون ما تعيد بناء التطبيق.", fontSize = 12.sp, color = Color.Gray)
                if (engMsg.isNotBlank()) Text(engMsg, fontSize = 13.sp)
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true; engMsg = "جاري تحديث المحرك…"
                        engMsg = try {
                            withContext(Dispatchers.IO) {
                                val r = YoutubeDL.getInstance().updateYoutubeDL(ctx)
                                engine = YoutubeDL.getInstance().version(ctx) ?: "-"
                                "تم الفحص ✔ ($r)"
                            }
                        } catch (e: Exception) { "فشل: ${e.message}" }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("تحديث المحرك")
                }
            }
        }
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("استيراد كوكيز تيك توك (cookies.txt)")
        }
            Text("المستودع: ${BuildConfig.REPO}", fontSize = 12.sp, color = Color.Gray)
        }
    }
