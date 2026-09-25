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
import java.io.File

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

    var scaleMode by remember { mutableStateOf(config.scaleMode) }
    var isMuted by remember { mutableStateOf(config.isMuted) }
    var doubleTapAction by remember { mutableStateOf(config.doubleTapAction) }
    var hasCustomVideo by remember { mutableStateOf(config.hasCustomVideo()) }
    var isImporting by remember { mutableStateOf(false) }
    var refreshPreviewTrigger by remember { mutableIntStateOf(0) }

    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    config.saveVideoFromUri(uri)
                }
                isImporting = false
                if (success) {
                    hasCustomVideo = true
                    refreshPreviewTrigger++
                    Toast.makeText(context, "视频导入成功！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "视频导入失败，请重试", Toast.LENGTH_SHORT).show()
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
                            text = "Pixel 动态壁纸",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "专为 Pixel 10 优化的视频动态桌面与锁屏",
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

            // 1. Phone Frame Live Preview (20:9 Pixel aspect ratio)
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
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Mock phone screen box (20:9 ratio approx)
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
                            refreshKey = refreshPreviewTrigger,
                            scaleMode = scaleMode
                        )

                        // Top camera punch-hole indicator
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.8f))
                                .align(Alignment.TopCenter)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (hasCustomVideo) "已载入自定义本地视频" else "正在播放内置示例片段",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 2. Video Select & Actions Card
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
                        text = "视频来源",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isImporting
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在导入视频...")
                        } else {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("从手机相册选择任意视频")
                        }
                    }

                    if (hasCustomVideo) {
                        OutlinedButton(
                            onClick = {
                                File(context.filesDir, WallpaperConfig.VIDEO_FILENAME).delete()
                                config.videoPath = null
                                hasCustomVideo = false
                                refreshPreviewTrigger++
                                Toast.makeText(context, "已恢复内置示例视频", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("恢复默认演示壁纸")
                        }
                    }
                }
            }

            // 3. Scaling & Layout Mode Card
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

            // 4. Audio & Double Tap Gestures Card
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

                    // Audio Mute Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "静音播放 (推荐)",
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

                    // Double Tap Gesture
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

            // 5. Apply Wallpaper Big Button
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
                    text = "设为 Pixel 动态壁纸 (桌面 / 锁屏)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 6. Pixel 10 Tips Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pixel 10 专项特性与省电说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TipItem(
                        icon = Icons.Default.CheckCircle,
                        text = "同时支持「主屏幕」与「主屏幕和锁定屏幕」两种动态显示模式。"
                    )
                    TipItem(
                        icon = Icons.Default.BatteryChargingFull,
                        text = "深度省电：当屏幕关闭或打开其他 App 时，后台视频解码立刻停止，待机功耗趋近于零。"
                    )
                    TipItem(
                        icon = Icons.Default.AutoAwesome,
                        text = "支持 4K/60fps、HDR、HEVC/H.265 及 AV1 格式，依托 Tensor 芯片硬件加速，流畅无发热。"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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

    fun reloadVideo() {
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

                if (config.hasCustomVideo()) {
                    setDataSource(config.videoPath!!)
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
        reloadVideo()
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
    refreshKey: Int,
    scaleMode: ScaleMode
) {
    val context = LocalContext.current
    val controller = remember { PreviewPlayerController(context, config) }

    // When scaleMode changes, update scaling in real-time
    LaunchedEffect(scaleMode) {
        controller.setScale(scaleMode)
    }

    // When refreshKey changes (video chosen or reset), reload immediately!
    LaunchedEffect(refreshKey) {
        controller.reloadVideo()
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

@Composable
fun TipItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
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
