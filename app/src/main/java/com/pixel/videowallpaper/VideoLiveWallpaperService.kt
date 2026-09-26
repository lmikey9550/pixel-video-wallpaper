package com.pixel.videowallpaper

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Build
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

        private var keyguardListener: KeyguardManager.KeyguardLockedStateListener? = null

        private val systemEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "Device unlocked (ACTION_USER_PRESENT)")
                        onKeyguardStateChanged(false)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen off (ACTION_SCREEN_OFF) -> reset to lock screen state")
                        onKeyguardStateChanged(true)
                    }
                    WallpaperConfig.ACTION_CONFIG_CHANGED -> {
                        Log.d(TAG, "Configuration changed broadcast received")
                        reloadCurrentConfig()
                    }
                }
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

            // Register keyguard locked state listener on Android 13+ (Pixel 10)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                keyguardListener = KeyguardManager.KeyguardLockedStateListener { locked ->
                    Log.d(TAG, "KeyguardLockedStateListener: locked=$locked")
                    onKeyguardStateChanged(locked)
                }
                keyguardListener?.let { listener ->
                    keyguardManager.addKeyguardLockedStateListener(mainExecutor, listener)
                }
            }

            // Register broadcast receiver for lock/unlock and config changes
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(WallpaperConfig.ACTION_CONFIG_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(systemEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(systemEventReceiver, filter)
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
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isEngineVisible = visible

            if (visible) {
                // Check if keyguard state changed while not visible
                val currentLocked = keyguardManager.isKeyguardLocked
                if (currentLocked != isKeyguardLocked) {
                    isKeyguardLocked = currentLocked
                }
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
            } else {
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

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            gestureDetector?.onTouchEvent(event)
        }

        override fun onDestroy() {
            super.onDestroy()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                keyguardListener?.let { listener ->
                    try {
                        keyguardManager.removeKeyguardLockedStateListener(listener)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            try {
                unregisterReceiver(systemEventReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            releasePlayer()
        }

        private fun resolveDesiredTarget(): VideoTarget {
            if (config.wallpaperMode == WallpaperMode.UNIFIED) {
                return VideoTarget.HOME
            }
            return if (isKeyguardLocked) {
                if (config.hasLockVideo()) VideoTarget.LOCK else VideoTarget.HOME
            } else {
                VideoTarget.HOME
            }
        }

        private fun onKeyguardStateChanged(locked: Boolean) {
            isKeyguardLocked = locked
            val desiredTarget = resolveDesiredTarget()
            if (desiredTarget != currentPlayingTarget) {
                Log.d(TAG, "Switching video from $currentPlayingTarget to $desiredTarget (isLocked=$locked)")
                if (isEngineVisible) {
                    switchVideoTarget(desiredTarget)
                } else {
                    // Update target so it starts with correct video when made visible
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

                    // Bind video data source
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
                    val mode = if (config.scaleMode == ScaleMode.CENTER_CROP) {
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
