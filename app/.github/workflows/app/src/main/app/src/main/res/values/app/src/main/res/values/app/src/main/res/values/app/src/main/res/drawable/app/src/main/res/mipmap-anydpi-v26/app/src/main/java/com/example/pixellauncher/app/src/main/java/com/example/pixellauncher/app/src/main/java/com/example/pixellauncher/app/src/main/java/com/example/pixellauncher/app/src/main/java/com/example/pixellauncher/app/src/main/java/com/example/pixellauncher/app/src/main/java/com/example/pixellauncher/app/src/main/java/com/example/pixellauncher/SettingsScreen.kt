@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.pixellauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(s: LauncherSettings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val packs = remember { listIconPacks(ctx) }

    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) overlayOk = Settings.canDrawOverlays(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .systemBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Настройки лаунчера", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            SectionTitle("Рабочий стол и иконки")
            SwitchRow("Подписи под иконками на рабочем столе", s.showLabelsHome) { s.showLabelsHome = it }
            SwitchRow("Подписи под иконками в меню приложений", s.showLabelsDrawer) { s.showLabelsDrawer = it }

            Text("Размер иконок: ${(s.iconScale * 100).roundToInt()}%")
            Slider(
                value = s.iconScale,
                onValueChange = { s.iconScale = it },
                valueRange = 0.7f..1.3f,
                steps = 11
            )

            Text("Сетка рабочего стола")
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4×5", "5×5", "5×6").forEachIndexed { i, label ->
                    FilterChip(
                        selected = s.gridPreset == i,
                        onClick = { s.gridPreset = i },
                        label = { Text(label) }
                    )
                }
            }

            SwitchRow(
                "Тематические монохромные иконки (Android 13+)",
                s.themedIcons,
                enabled = Build.VERSION.SDK_INT >= 33
            ) { s.themedIcons = it }

            Text("Пак иконок", modifier = Modifier.padding(top = 8.dp))
            RadioRow("Системные иконки", s.iconPack.isEmpty()) { s.iconPack = "" }
            packs.forEach { p -> RadioRow(p.label, s.iconPack == p.pkg) { s.iconPack = p.pkg } }
            if (packs.isEmpty()) {
                Text(
                    "Паки иконок не найдены. Установите пак с поддержкой ADW / Nova / Apex.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            SectionTitle("Система")
            OutlinedButton(onClick = {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Сделать лаунчером по умолчанию") }

            SectionTitle("Экран блокировки")
            SwitchRow("Свой экран блокировки", s.lockEnabled) { on ->
                if (on) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    s.lockEnabled = true
                    ContextCompat.startForegroundService(ctx, Intent(ctx, LockService::class.java))
                } else {
                    s.lockEnabled = false
                    ctx.stopService(Intent(ctx, LockService::class.java))
                }
            }
            Text(
                "Служба слушает выключение экрана и показывает этот экран поверх системного. " +
                    "Системная защита (PIN / отпечаток) сохраняется: она запрашивается при свайпе вверх.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            if (!overlayOk) {
                Text(
                    "Нужно разрешение «Поверх других окон», иначе Android 10+ не даст показать экран из фона.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.padding(top = 6.dp)
                ) { Text("Разрешить поверх других окон") }
            }
            OutlinedButton(
                onClick = {
                    ctx.startActivity(Intent(ctx, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                modifier = Modifier.padding(top = 6.dp)
            ) { Text("Предпросмотр экрана блокировки") }

            SectionTitle("Часы")
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF101418))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) { PixelClock(s) }

            Text("Толщина шрифта: ${s.clockWeight}", modifier = Modifier.padding(top = 12.dp))
            Slider(
                value = s.clockWeight.toFloat(),
                onValueChange = { s.clockWeight = it.roundToInt() },
                valueRange = 100f..900f,
                steps = 7
            )
            Text("Размер: ${s.clockSize} sp")
            Slider(
                value = s.clockSize.toFloat(),
                onValueChange = { s.clockSize = it.roundToInt() },
                valueRange = 60f..160f
            )
            Text("Высота цифр (растяжение): ${(s.clockHeight * 100).roundToInt()}%")
            Slider(
                value = s.clockHeight,
                onValueChange = { s.clockHeight = it },
                valueRange = 0.6f..1.8f
            )

            Text("Стиль")
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.clockStyle == 0, onClick = { s.clockStyle = 0 }, label = { Text("Одна строка") })
                FilterChip(selected = s.clockStyle == 1, onClick = { s.clockStyle = 1 }, label = { Text("Две строки") })
            }

            Text("Цвет (первый — динамический, из обоев)")
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClockPalette.indices.forEach { i ->
                    val c = remember(i) { clockColor(i, ctx) }
                    val selected = s.clockColor == i
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) scheme.primary else scheme.outline,
                                CircleShape
                            )
                            .clickable { s.clockColor = i }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title)
    }
}
