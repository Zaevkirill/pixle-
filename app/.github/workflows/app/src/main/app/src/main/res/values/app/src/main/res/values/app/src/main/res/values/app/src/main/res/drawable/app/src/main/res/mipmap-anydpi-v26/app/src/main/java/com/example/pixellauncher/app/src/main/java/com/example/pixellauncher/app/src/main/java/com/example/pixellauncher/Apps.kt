package com.example.pixellauncher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class AppInfo(val label: String, val pkg: String, val cls: String) {
    val key: String get() = "$pkg/$cls"
}

data class IconPackInfo(val pkg: String, val label: String)

class AppRepository(private val ctx: Context) {
    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    private var scope: CoroutineScope? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope?.let { refresh(it) }
        }
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        refresh(scope)
    }

    fun stop() {
        runCatching { ctx.unregisterReceiver(receiver) }
    }

    fun refresh(scope: CoroutineScope) {
        scope.launch { apps = withContext(Dispatchers.IO) { query() } }
    }

    private fun query(): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.pkg != ctx.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun defaultDock(list: List<AppInfo>): List<String> {
        val pm = ctx.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        return probes
            .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .mapNotNull { pkg -> list.firstOrNull { it.pkg == pkg }?.key }
            .distinct()
    }
}

fun launchApp(ctx: Context, app: AppInfo) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.pkg, app.cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }
}

fun openAppInfo(ctx: Context, app: AppInfo) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun uninstallApp(ctx: Context, app: AppInfo) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun listIconPacks(ctx: Context): List<IconPackInfo> {
    val pm = ctx.packageManager
    val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME"
    )
    return actions
        .flatMap { pm.queryIntentActivities(Intent(it), 0) }
        .map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

class IconLoader(private val ctx: Context) {
    var version by mutableIntStateOf(0)
        private set

    private val cache = LruCache<String, ImageBitmap>(400)
    private var signature = ""
    private var themed = false
    private var bg = 0
    private var fg = 0
    private var packRes: Resources? = null
    private var packName = ""
    private var packMap: Map<String, String> = emptyMap()

    suspend fun configure(pack: String, themed: Boolean, bg: Int, fg: Int) {
        val sig = "$pack|$themed|$bg|$fg"
        if (sig == signature) return
        val loaded = if (pack.isEmpty()) null else withContext(Dispatchers.IO) {
            runCatching { loadPack(pack) }.getOrNull()
        }
        signature = sig
        this.themed = themed
        this.bg = bg
        this.fg = fg
        packName = if (loaded != null) pack else ""
        packRes = loaded?.first
        packMap = loaded?.second ?: emptyMap()
        cache.evictAll()
        version++
    }

    suspend fun get(app: AppInfo, size: Int): ImageBitmap {
        val k = "${app.key}@$size#$version"
        cache.get(k)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { render(app, size) }
        cache.put(k, bmp)
        return bmp
    }

    private fun loadPack(pkg: String): Pair<Resources, Map<String, String>> {
        val res = ctx.packageManager.getResourcesForApplication(pkg)
        val id = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (id != 0) {
            res.getXml(id)
        } else {
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(res.assets.open("appfilter.xml"), null)
            }
        }
        val map = HashMap<String, String>()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val dr = parser.getAttributeValue(null, "drawable")
                if (comp != null && dr != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                    map[comp.substring(14, comp.length - 1)] = dr
                }
            }
            ev = parser.next()
        }
        return res to map
    }

    private fun render(app: AppInfo, size: Int): ImageBitmap {
        val res = packRes
        if (res != null) {
            val name = packMap[app.key]
            if (name != null) {
                val id = res.getIdentifier(name, "drawable", packName)
                if (id != 0) {
                    val d = ResourcesCompat.getDrawable(res, id, null)
                    if (d != null) return d.toBitmap(size, size).asImageBitmap()
                }
            }
        }
        val pm = ctx.packageManager
        val d: Drawable = runCatching { pm.getActivityIcon(ComponentName(app.pkg, app.cls)) }
            .getOrElse { pm.defaultActivityIcon }
        if (themed && Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) {
            val mono = d.monochrome
            if (mono != null) return themedBitmap(mono, size)
        }
        return d.toBitmap(size, size).asImageBitmap()
    }

    private fun themedBitmap(mono: Drawable, size: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        val m = mono.mutate()
        m.setTint(fg)
        val inset = (size * 0.25f).toInt()
        m.setBounds(-inset, -inset, size + inset, size + inset)
        m.draw(c)
        return bmp.asImageBitmap()
    }
}
