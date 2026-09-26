package com.pixel.videowallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelWallpaperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PixelWallpaperHomeScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelWallpaperHomeScreen() {
    val context = LocalContext.current
    val config = remember { WallpaperConfig(context) }
    val scope = rememberCoroutineScope()

    var wallpaperMode by remember { mutableStateOf(config.wallpaperMode) }
    var previewTarget by remember { mutableStateOf(VideoTarget.LOCK) }
    var scaleMode by remember { mutableStateOf(config.scaleMode) }
    var isMuted by remember { mutableStateOf(config.isMuted) }
    var doubleTapAction by remember { mutableStateOf(config.doubleTapAction) }

    var hasHomeVideo by remember { mutableStateOf(config.hasHomeVideo()) }
    var hasLockVideo by remember { mutableStateOf(config.hasLockVideo()) }

    var isImportingTarget by remember { mutableStateOf<VideoTarget?>(null) }
    var refreshPreviewTrigger by remember { mutableIntStateOf(0) }

    // Picker for Lock Screen Video
    val lockVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isImportingTarget = VideoTarget.LOCK
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    config.saveVideoForTarget(uri, VideoTarget.LOCK)
                }
                isImportingTarget = null
                if (success) {
                    hasLockVideo = true
                    previewTarget = VideoTarget.LOCK
                    refreshPreviewTrigger++
                    Toast.makeText(context, "锁屏专属视频导入成功！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "锁屏视频导入失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Picker for Home Screen Video
    val homeVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isImportingTarget = VideoTarget.HOME
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    config.saveVideoForTarget(uri, VideoTarget.HOME)
                }
                isImportingTarget = null
                if (success) {
                    hasHomeVideo = true
                    previewTarget = VideoTarget.HOME
                    refreshPreviewTrigger++
                    Toast.makeText(context, "桌面主屏视频导入成功！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "桌面视频导入失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pixel 动态壁纸 v1.1",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "锁屏与桌面双视频智能引擎",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Wallpaper Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "壁纸运行模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = wallpaperMode == WallpaperMode.DUAL,
                            onClick = {
                                wallpaperMode = WallpaperMode.DUAL
                                config.wallpaperMode = WallpaperMode.DUAL
                                refreshPreviewTrigger++
                            },
                            label = { Text("独立双视频模式") },
                            leadingIcon = if (wallpaperMode == WallpaperMode.DUAL) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = wallpaperMode == WallpaperMode.UNIFIED,
                            onClick = {
                                wallpaperMode = WallpaperMode.UNIFIED
                                config.wallpaperMode = WallpaperMode.UNIFIED
                                previewTarget = VideoTarget.HOME
                                refreshPreviewTrigger++
                            },
                            label = { Text("单视频统一模式") },
                            leadingIcon = if (wallpaperMode == WallpaperMode.UNIFIED) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = if (wallpaperMode == WallpaperMode.DUAL)
                            "✨ 独立模式：锁屏未解锁时播放【锁屏视频】，指纹/面容解锁瞬间毫秒级自动切换为【桌面视频】！"
                        else
                            "统一模式：主屏幕和锁定屏幕共用同一个动态视频播放。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Interactive Phone Frame Live Preview (20:9 Pixel aspect ratio)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pixel 10 效果实时预览",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Switch preview between Lock Screen and Home Screen
                    if (wallpaperMode == WallpaperMode.DUAL) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(bottom = 12.dp)
                        ) {
                            SegmentedButton(
                                selected = previewTarget == VideoTarget.LOCK,
                                onClick = {
                                    previewTarget = VideoTarget.LOCK
                                    refreshPreviewTrigger++
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                            ) {
                                Text("锁屏效果")
                            }
                            SegmentedButton(
                                selected = previewTarget == VideoTarget.HOME,
                                onClick = {
                                    previewTarget = VideoTarget.HOME
                                    refreshPreviewTrigger++
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                icon = { Icon(Icons.Default.Home, contentDescription = null) }
                            ) {
                                Text("桌面效果")
                            }
                        }
                    }

                    // Mock phone screen box (20:9 ratio)
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(380.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .border(3.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoPreviewSurface(
                            config = config,
                            target = if (wallpaperMode == WallpaperMode.DUAL) previewTarget else VideoTarget.HOME,
                            refreshKey = refreshPreviewTrigger,
                            scaleMode = scaleMode
                        )

                        // Top camera punch-hole
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.8f))
                                .align(Alignment.TopCenter)
                        )

                        // Simulated Lock Screen / Home Screen Overlay
                        if (wallpaperMode == WallpaperMode.DUAL && previewTarget == VideoTarget.LOCK) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 28.dp)
                                    .align(Alignment.TopCenter),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "09:41",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color.White
                                )
                                Text(
                                    text = "轻触锁屏体验",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            // Home Screen simulated dock
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                                    .align(Alignment.BottomCenter),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                repeat(4) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val statusText = if (wallpaperMode == WallpaperMode.DUAL) {
                        if (previewTarget == VideoTarget.LOCK) {
                            if (hasLockVideo) "正在预览：自定义【锁屏专属视频】" else "锁屏预览：内置示例视频 (未设置自定义)"
                        } else {
                            if (hasHomeVideo) "正在预览：自定义【桌面主屏视频】" else "桌面预览：内置示例视频 (未设置自定义)"
                        }
                    } else {
                        if (hasHomeVideo) "正在预览：自定义统一壁纸" else "正在播放内置示例壁纸"
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 3. Video Select Cards
            if (wallpaperMode == WallpaperMode.DUAL) {
                // Lock screen video card
                VideoTargetCard(
                    title = "🔒 锁屏专属动态视频",
                    subtitle = "手机点亮屏幕、等待指纹/面容解锁时播放",
                    hasCustom = hasLockVideo,
                    isImporting = isImportingTarget == VideoTarget.LOCK,
                    onPick = {
                        lockVideoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onReset = {
                        config.clearVideo(VideoTarget.LOCK)
                        hasLockVideo = false
                        refreshPreviewTrigger++
                        Toast.makeText(context, "已恢复锁屏默认示例", Toast.LENGTH_SHORT).show()
                    }
                )

                // Home screen video card
                VideoTargetCard(
                    title = "🏠 桌面主屏动态视频",
                    subtitle = "手机解锁进入桌面主屏幕后无缝播放",
                    hasCustom = hasHomeVideo,
                    isImporting = isImportingTarget == VideoTarget.HOME,
                    onPick = {
                        homeVideoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onReset = {
                        config.clearVideo(VideoTarget.HOME)
                        hasHomeVideo = false
                        refreshPreviewTrigger++
                        Toast.makeText(context, "已恢复桌面默认示例", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // Unified single video card
                VideoTargetCard(
                    title = "🎬 统一动态视频",
                    subtitle = "主屏幕与锁定屏幕同步播放同一段视频",
                    hasCustom = hasHomeVideo,
                    isImporting = isImportingTarget == VideoTarget.HOME,
                    onPick = {
                        homeVideoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onReset = {
                        config.clearVideo(VideoTarget.HOME)
                        hasHomeVideo = false
                        refreshPreviewTrigger++
                        Toast.makeText(context, "已恢复默认示例视频", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 4. Scaling Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "屏幕画面比例适配",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    ScaleModeOption(
                        title = ScaleMode.CENTER_CROP.title,
                        description = ScaleMode.CENTER_CROP.description,
                        selected = scaleMode == ScaleMode.CENTER_CROP,
                        onSelect = {
                            scaleMode = ScaleMode.CENTER_CROP
                            config.scaleMode = ScaleMode.CENTER_CROP
                        }
                    )

                    ScaleModeOption(
                        title = ScaleMode.FIT_CENTER.title,
                        description = ScaleMode.FIT_CENTER.description,
                        selected = scaleMode == ScaleMode.FIT_CENTER,
                        onSelect = {
                            scaleMode = ScaleMode.FIT_CENTER
                            config.scaleMode = ScaleMode.FIT_CENTER
                        }
                    )
                }
            }

            // 5. Audio & Double Tap Gestures Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "声音与桌面手势",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "静音播放",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "保持壁纸静音，避免解锁与划屏时发出声音并更加省电",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMuted,
                            onCheckedChange = { checked ->
                                isMuted = checked
                                config.isMuted = checked
                            }
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "桌面双击手势动作",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    DoubleTapOption(
                        title = "双击切换静音 / 播放声音",
                        selected = doubleTapAction == DoubleTapAction.MUTE_TOGGLE,
                        onSelect = {
                            doubleTapAction = DoubleTapAction.MUTE_TOGGLE
                            config.doubleTapAction = DoubleTapAction.MUTE_TOGGLE
                        }
                    )

                    DoubleTapOption(
                        title = "双击暂停 / 继续播放",
                        selected = doubleTapAction == DoubleTapAction.PLAY_PAUSE,
                        onSelect = {
                            doubleTapAction = DoubleTapAction.PLAY_PAUSE
                            config.doubleTapAction = DoubleTapAction.PLAY_PAUSE
                        }
                    )

                    DoubleTapOption(
                        title = "关闭双击手势",
                        selected = doubleTapAction == DoubleTapAction.NONE,
                        onSelect = {
                            doubleTapAction = DoubleTapAction.NONE
                            config.doubleTapAction = DoubleTapAction.NONE
                        }
                    )
                }
            }

            // 6. Dual-Mode Key Setting Guidance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "双视频生效必看指南",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "点击下方按钮进入系统设置界面时，请务必选择「主屏幕和锁定屏幕」！系统会将本壁纸服务同时赋给两个屏幕，随后我们内置的智能锁屏引擎会自动在锁屏播锁屏视频、解锁后播桌面视频！",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 7. Apply Wallpaper Big Button
            Button(
                onClick = { applyAsLiveWallpaper(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "设为 Pixel 动态壁纸 (选主屏幕和锁屏)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun VideoTargetCard(
    title: String,
    subtitle: String,
    hasCustom: Boolean,
    isImporting: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasCustom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (hasCustom) "已载入自定义" else "默认示例",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (hasCustom) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onPick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isImporting
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在导入视频...")
                } else {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasCustom) "更换该视频" else "从相册选择视频")
                }
            }

            if (hasCustom) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("恢复默认示例", fontSize = 13.sp)
                }
            }
        }
    }
}

class PreviewPlayerController(
    private val context: Context,
    private val config: WallpaperConfig
) : SurfaceHolder.Callback {
    private var mediaPlayer: MediaPlayer? = null
    private var currentHolder: SurfaceHolder? = null
    var scaleMode: ScaleMode = ScaleMode.CENTER_CROP
    var currentTarget: VideoTarget = VideoTarget.HOME

    fun setScale(mode: ScaleMode) {
        scaleMode = mode
        mediaPlayer?.let { player ->
            try {
                val m = if (mode == ScaleMode.CENTER_CROP) {
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                } else {
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                }
                player.setVideoScalingMode(m)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun reloadVideo(target: VideoTarget) {
        currentTarget = target
        val holder = currentHolder ?: return
        if (holder.surface == null || !holder.surface.isValid) return

        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
            } ?: run {
                mediaPlayer = MediaPlayer()
            }

            mediaPlayer?.apply {
                setSurface(holder.surface)
                isLooping = true
                setVolume(0f, 0f)

                if (config.hasVideoForTarget(target)) {
                    val path = config.getVideoPathForTarget(target)!!
                    setDataSource(path)
                } else if (config.hasHomeVideo()) {
                    setDataSource(config.homeVideoPath!!)
                } else {
                    val afd = context.resources.openRawResourceFd(R.raw.sample_wallpaper)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }

                val m = if (scaleMode == ScaleMode.CENTER_CROP) {
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                } else {
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                }
                setVideoScalingMode(m)

                setOnPreparedListener { mp ->
                    mp.start()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        currentHolder = holder
        reloadVideo(currentTarget)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        currentHolder = holder
        setScale(scaleMode)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        currentHolder = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
    }

    fun release() {
        surfaceDestroyed(currentHolder ?: return)
    }
}

@Composable
fun VideoPreviewSurface(
    config: WallpaperConfig,
    target: VideoTarget,
    refreshKey: Int,
    scaleMode: ScaleMode
) {
    val context = LocalContext.current
    val controller = remember { PreviewPlayerController(context, config) }

    LaunchedEffect(scaleMode) {
        controller.setScale(scaleMode)
    }

    LaunchedEffect(target, refreshKey) {
        controller.reloadVideo(target)
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(controller)
            }
        }
    )
}

@Composable
fun ScaleModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DoubleTapOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

fun applyAsLiveWallpaper(context: Context) {
    try {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, VideoLiveWallpaperService::class.java)
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "无法启动壁纸选择器: ${e2.message}", Toast.LENGTH_LONG).show()
        }
    }
}
