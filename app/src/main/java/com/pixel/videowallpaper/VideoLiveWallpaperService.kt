package com.pixel.videowallpaper

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.io.File

class VideoLiveWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "PixelWallpaperService"
    }

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private lateinit var config: WallpaperConfig
        private lateinit var keyguardManager: KeyguardManager
        private var isEngineVisible = false
        private var isPrepared = false
        private var isKeyguardLocked = true
        private var currentPlayingTarget: VideoTarget? = null
        private var gestureDetector: GestureDetector? = null

        private val lockCheckHandler = Handler(Looper.getMainLooper())
        private val lockCheckRunnable = object : Runnable {
            override fun run() {
                if (isEngineVisible && isKeyguardLocked) {
                    val currentlyLocked = keyguardManager.isKeyguardLocked
                    if (!currentlyLocked) {
                        Log.d(TAG, "Lock polling detected device unlock!")
                        onKeyguardStateChanged(false)
                    } else {
                        lockCheckHandler.postDelayed(this, 180)
                    }
                }
            }
        }

        private val systemEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "Device unlocked broadcast (ACTION_USER_PRESENT)")
                        onKeyguardStateChanged(false)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen off broadcast (ACTION_SCREEN_OFF) -> reset to locked state")
                        onKeyguardStateChanged(true)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen on broadcast (ACTION_SCREEN_ON)")
                        val locked = keyguardManager.isKeyguardLocked
                        onKeyguardStateChanged(locked)
                    }
                }
            }
        }

        private val configReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Configuration changed broadcast received")
                reloadCurrentConfig()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            config = WallpaperConfig(applicationContext)
            keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

            // Initial keyguard lock status
            isKeyguardLocked = keyguardManager.isKeyguardLocked

            // Setup double tap gesture detector
            gestureDetector = GestureDetector(applicationContext, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    handleDoubleTap()
                    return true
                }
            })

            // Register system broadcast receiver (Must use RECEIVER_EXPORTED for system broadcasts on Android 14+)
            val systemFilter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            val appFilter = IntentFilter(WallpaperConfig.ACTION_CONFIG_CHANGED)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(systemEventReceiver, systemFilter, Context.RECEIVER_EXPORTED)
                registerReceiver(configReceiver, appFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(systemEventReceiver, systemFilter)
                registerReceiver(configReceiver, appFilter)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            isKeyguardLocked = keyguardManager.isKeyguardLocked
            val target = resolveDesiredTarget()
            initAndStartPlayer(holder, target)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            applyScalingMode()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            stopLockPolling()
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isEngineVisible = visible

            if (visible) {
                isKeyguardLocked = keyguardManager.isKeyguardLocked
                val desiredTarget = resolveDesiredTarget()
                if (desiredTarget != currentPlayingTarget) {
                    switchVideoTarget(desiredTarget)
                } else {
                    mediaPlayer?.let { player ->
                        if (isPrepared && !player.isPlaying) {
                            try {
                                player.start()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error starting player: ${e.message}")
                            }
                        }
                    }
                }
                if (isKeyguardLocked) {
                    startLockPollingIfNeeded()
                } else {
                    stopLockPolling()
                }
            } else {
                stopLockPolling()
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            player.pause()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pausing player: ${e.message}")
                    }
                }
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            // When home launcher scrolls or receives focus, check if unlocked
            if (isKeyguardLocked && !keyguardManager.isKeyguardLocked) {
                Log.d(TAG, "onOffsetsChanged detected keyguard unlock!")
                onKeyguardStateChanged(false)
            }
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            val desiredTarget = resolveDesiredTarget()
            if (desiredTarget != currentPlayingTarget) {
                switchVideoTarget(desiredTarget)
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            gestureDetector?.onTouchEvent(event)
        }

        override fun onDestroy() {
            super.onDestroy()
            stopLockPolling()
            try {
                unregisterReceiver(systemEventReceiver)
                unregisterReceiver(configReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            releasePlayer()
        }

        private fun startLockPollingIfNeeded() {
            stopLockPolling()
            if (isEngineVisible && isKeyguardLocked && config.wallpaperMode == WallpaperMode.DUAL) {
                lockCheckHandler.postDelayed(lockCheckRunnable, 180)
            }
        }

        private fun stopLockPolling() {
            lockCheckHandler.removeCallbacks(lockCheckRunnable)
        }

        private fun resolveDesiredTarget(): VideoTarget {
            if (config.wallpaperMode == WallpaperMode.UNIFIED) {
                return VideoTarget.HOME
            }
            // Check Android 14+ engine wallpaper flags if decoupled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    val flags = wallpaperFlags
                    if ((flags and android.app.WallpaperManager.FLAG_LOCK) != 0 && (flags and android.app.WallpaperManager.FLAG_SYSTEM) == 0) {
                        return if (config.hasLockVideo()) VideoTarget.LOCK else VideoTarget.HOME
                    } else if ((flags and android.app.WallpaperManager.FLAG_SYSTEM) != 0 && (flags and android.app.WallpaperManager.FLAG_LOCK) == 0) {
                        return VideoTarget.HOME
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            return if (isKeyguardLocked) {
                if (config.hasLockVideo()) VideoTarget.LOCK else VideoTarget.HOME
            } else {
                VideoTarget.HOME
            }
        }

        private fun onKeyguardStateChanged(locked: Boolean) {
            isKeyguardLocked = locked
            if (!locked) {
                stopLockPolling()
            } else {
                startLockPollingIfNeeded()
            }
            val desiredTarget = resolveDesiredTarget()
            if (desiredTarget != currentPlayingTarget) {
                Log.d(TAG, "Switching video from $currentPlayingTarget to $desiredTarget (isLocked=$locked)")
                if (isEngineVisible) {
                    switchVideoTarget(desiredTarget)
                } else {
                    currentPlayingTarget = null
                }
            }
        }

        private fun initAndStartPlayer(holder: SurfaceHolder?, target: VideoTarget) {
            if (holder == null || holder.surface == null || !holder.surface.isValid) {
                Log.d(TAG, "Surface is not valid or ready yet.")
                return
            }
            releasePlayer()
            currentPlayingTarget = target
            try {
                mediaPlayer = MediaPlayer().apply {
                    setSurface(holder.surface)
                    isLooping = true

                    val volume = if (config.isMuted) 0f else 1f
                    setVolume(volume, volume)

                    bindDataSource(this, target)

                    setOnPreparedListener { mp ->
                        isPrepared = true
                        applyScalingMode()
                        if (isEngineVisible) {
                            mp.start()
                        }
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        false
                    }

                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MediaPlayer", e)
            }
        }

        private fun bindDataSource(player: MediaPlayer, target: VideoTarget) {
            if (config.hasVideoForTarget(target)) {
                val path = config.getVideoPathForTarget(target)!!
                player.setDataSource(File(path).absolutePath)
            } else if (config.hasHomeVideo()) {
                val path = config.homeVideoPath!!
                player.setDataSource(File(path).absolutePath)
            } else {
                val afd: AssetFileDescriptor = resources.openRawResourceFd(R.raw.sample_wallpaper)
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
        }

        private fun switchVideoTarget(target: VideoTarget) {
            val holder = surfaceHolder ?: return
            if (holder.surface == null || !holder.surface.isValid) return

            currentPlayingTarget = target
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.reset()
                    player.setSurface(holder.surface)
                    player.isLooping = true
                    val volume = if (config.isMuted) 0f else 1f
                    player.setVolume(volume, volume)

                    bindDataSource(player, target)

                    player.setOnPreparedListener { mp ->
                        isPrepared = true
                        applyScalingMode()
                        if (isEngineVisible) {
                            mp.start()
                        }
                    }
                    player.prepareAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "Error switching video target: ${e.message}")
                    initAndStartPlayer(holder, target)
                }
            } ?: run {
                initAndStartPlayer(holder, target)
            }
        }

        private fun reloadCurrentConfig() {
            val target = resolveDesiredTarget()
            switchVideoTarget(target)
        }

        private fun applyScalingMode() {
            mediaPlayer?.let { player ->
                try {
                    val mode = if (config.scaleMode == ScaleMode.ASPECT_FILL) {
                        MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    } else {
                        MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    }
                    player.setVideoScalingMode(mode)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set scaling mode: ${e.message}")
                }
            }
        }

        private fun applyAudioVolume() {
            mediaPlayer?.let { player ->
                val volume = if (config.isMuted) 0f else 1f
                player.setVolume(volume, volume)
            }
        }

        private fun handleDoubleTap() {
            when (config.doubleTapAction) {
                DoubleTapAction.MUTE_TOGGLE -> {
                    config.isMuted = !config.isMuted
                    applyAudioVolume()
                    triggerHapticFeedback()
                }
                DoubleTapAction.PLAY_PAUSE -> {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.start()
                        }
                        triggerHapticFeedback()
                    }
                }
                DoubleTapAction.NONE -> {
                    // Do nothing
                }
            }
        }

        private fun triggerHapticFeedback() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        private fun releasePlayer() {
            isPrepared = false
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.reset()
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing MediaPlayer", e)
                }
            }
            mediaPlayer = null
        }
    }
}
