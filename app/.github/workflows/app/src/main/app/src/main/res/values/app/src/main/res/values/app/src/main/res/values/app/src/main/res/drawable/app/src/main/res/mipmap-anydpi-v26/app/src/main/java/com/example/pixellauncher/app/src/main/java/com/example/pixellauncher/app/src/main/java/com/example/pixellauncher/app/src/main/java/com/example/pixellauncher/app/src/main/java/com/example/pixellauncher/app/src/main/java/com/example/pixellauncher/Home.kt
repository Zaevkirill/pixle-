@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.pixellauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun LauncherRoot(s: LauncherSettings, repo: AppRepository, icons: IconLoader, homeSignal: Int) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    var progress by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var searchSignal by remember { mutableIntStateOf(0) }

    val apps = repo.apps
    val byKey = remember(apps) { apps.associateBy { it.key } }

    fun settle(target: Float) {
        job?.cancel()
        job = scope.launch {
            animate(
                progress, target,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) { v, _ -> progress = v }
        }
    }

    fun drag(delta: Float) {
        job?.cancel()
        progress = (progress + delta).coerceIn(0f, 1f)
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && progress > 0f && progress < 1f) {
                    drag(-available.y / screenH)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.Drag) {
                    drag(-available.y / screenH)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (progress > 0.001f && progress < 0.999f) settle(if (progress > 0.8f) 1f else 0f)
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(s.iconPack, s.themedIcons, scheme.primaryContainer, scheme.onPrimaryContainer) {
        icons.configure(
            s.iconPack, s.themedIcons,
            scheme.primaryContainer.toArgb(), scheme.onPrimaryContainer.toArgb()
        )
    }

    LaunchedEffect(apps) {
        if (apps.isNotEmpty() && !s.initialized) {
            s.dockItems = repo.defaultDock(apps).joinToString("\n")
            s.initialized = true
        }
    }

    LaunchedEffect(homeSignal) {
        showSettings = false
        if (progress > 0f) settle(0f)
    }

    BackHandler(enabled = showSettings || progress > 0.01f) {
        if (showSettings) showSettings = false else settle(0f)
    }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            s = s,
            icons = icons,
            byKey = byKey,
            onDrag = { dy -> drag(-dy / screenH) },
            onDragEnd = { settle(if (progress > 0.2f) 1f else 0f) },
            onSearch = { searchSignal++; settle(1f) },
            onSettings = { showSettings = true },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - progress * 0.9f
                    val sc = 1f - 0.06f * progress
                    scaleX = sc
                    scaleY = sc
                }
        )

        if (progress > 0.02f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { settle(0f) }
            )
        }

        AppDrawer(
            apps = apps,
            icons = icons,
            s = s,
            searchSignal = searchSignal,
            open = progress > 0.02f,
            connection = connection,
            onClose = { settle(0f) },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - progress) * screenH
                    alpha = if (progress <= 0.001f) 0f else 1f
                }
        )

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntOffset.VisibilityThreshold
                )
            ) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            SettingsScreen(s = s, onBack = { showSettings = false })
        }
    }
}

@Composable
fun HomeScreen(
    s: LauncherSettings,
    icons: IconLoader,
    byKey: Map<String, AppInfo>,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val items = s.homeList().mapNotNull { byKey[it] }
    val dock = s.dockList().mapNotNull { byKey[it] }
    val perPage = s.cols * s.rows
    val pageCount = max(1, (items.size + perPage - 1) / perPage)
    val pager = rememberPagerState(pageCount = { pageCount })

    Column(
        modifier
            .systemBarsPadding()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { _, dy -> onDrag(dy) }
            }
    ) {
        AtAGlance(Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))

        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onSettings() }) }
        ) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                for (r in 0 until s.rows) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until s.cols) {
                            val idx = page * perPage + r * s.cols + c
                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                items.getOrNull(idx)?.let { app ->
                                    AppIcon(
                                        app, icons, s, s.showLabelsHome, Color.White,
                                        listOf(
                                            "Убрать с рабочего стола" to { s.removeHome(app.key) },
                                            "О приложении" to { openAppInfo(ctx, app) },
                                            "Удалить" to { uninstallApp(ctx, app) }
                                        )
                                    ) { launchApp(ctx, app) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pageCount > 1) {
            Row(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp)) {
                repeat(pageCount) { i ->
                    val active = pager.currentPage == i
                    val w by animateDpAsState(
                        targetValue = if (active) 18.dp else 6.dp,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                        label = "dot"
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (active) 0.95f else 0.5f))
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dock.forEach { app ->
                AppIcon(
                    app, icons, s, false, Color.White,
                    listOf(
                        "Убрать из дока" to { s.removeDock(app.key) },
                        "О приложении" to { openAppInfo(ctx, app) },
                        "Удалить" to { uninstallApp(ctx, app) }
                    )
                ) { launchApp(ctx, app) }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)
                .height(52.dp)
                .clip(CircleShape)
                .background(scheme.surface.copy(alpha = 0.92f))
                .clickable(onClick = onSearch)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Поиск приложений", color = scheme.onSurfaceVariant, fontSize = 16.sp)
        }
    }
}
