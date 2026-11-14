package com.example.pixellauncher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KProperty

class PrefState<T : Any>(
    private val sp: SharedPreferences,
    private val key: String,
    default: T
) {
    private val state = mutableStateOf(read(default))

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = value
        val e = sp.edit()
        when (value) {
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Float -> e.putFloat(key, value)
            is String -> e.putString(key, value)
        }
        e.apply()
    }

    @Suppress("UNCHECKED_CAST")
    private fun read(d: T): T = when (d) {
        is Boolean -> sp.getBoolean(key, d)
        is Int -> sp.getInt(key, d)
        is Float -> sp.getFloat(key, d)
        is String -> sp.getString(key, d) ?: d
        else -> d
    } as T
}

class LauncherSettings private constructor(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    var showLabelsHome by PrefState(sp, "labelsHome", true)
    var showLabelsDrawer by PrefState(sp, "labelsDrawer", true)
    var iconScale by PrefState(sp, "iconScale", 1f)
    var gridPreset by PrefState(sp, "gridPreset", 0)
    var iconPack by PrefState(sp, "iconPack", "")
    var themedIcons by PrefState(sp, "themedIcons", false)

    var lockEnabled by PrefState(sp, "lockEnabled", false)
    var clockWeight by PrefState(sp, "clockWeight", 300)
    var clockSize by PrefState(sp, "clockSize", 96)
    var clockHeight by PrefState(sp, "clockHeight", 1f)
    var clockStyle by PrefState(sp, "clockStyle", 1)
    var clockColor by PrefState(sp, "clockColor", 0)

    var homeItems by PrefState(sp, "homeItems", "")
    var dockItems by PrefState(sp, "dockItems", "")
    var initialized by PrefState(sp, "initialized", false)

    val cols: Int get() = if (gridPreset == 0) 4 else 5
    val rows: Int get() = if (gridPreset == 2) 6 else 5

    fun homeList(): List<String> = homeItems.split("\n").filter { it.isNotBlank() }
    fun dockList(): List<String> = dockItems.split("\n").filter { it.isNotBlank() }

    fun addHome(key: String) {
        val l = homeList()
        if (key !in l) homeItems = (l + key).joinToString("\n")
    }

    fun removeHome(key: String) {
        homeItems = homeList().filter { it != key }.joinToString("\n")
    }

    fun addDock(key: String) {
        val l = dockList()
        if (key !in l && l.size < 5) dockItems = (l + key).joinToString("\n")
    }

    fun removeDock(key: String) {
        dockItems = dockList().filter { it != key }.joinToString("\n")
    }

    companion object {
        @Volatile
        private var instance: LauncherSettings? = null

        fun get(ctx: Context): LauncherSettings =
            instance ?: synchronized(this) {
                instance ?: LauncherSettings(ctx.applicationContext).also { instance = it }
            }
    }
}
