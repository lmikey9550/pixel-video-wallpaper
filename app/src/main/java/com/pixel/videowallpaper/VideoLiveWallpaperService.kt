package com.pixel.videowallpaper

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
        private var isEngineVisible = false
        private var isPrepared = false
        private var gestureDetector: GestureDetector? = null

        private val configReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Configuration changed broadcast received.")
                reloadConfigAndApply()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            config = WallpaperConfig(applicationContext)

            // Setup double tap gesture detector
            gestureDetector = GestureDetector(applicationContext, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    handleDoubleTap()
                    return true
                }
            })

            // Register configuration change receiver
            val filter = IntentFilter(WallpaperConfig.ACTION_CONFIG_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(configReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(configReceiver, filter)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            initAndStartPlayer(holder)
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
            mediaPlayer?.let { player ->
                try {
                    if (visible) {
                        if (isPrepared && !player.isPlaying) {
                            player.start()
                        }
                    } else {
                        if (player.isPlaying) {
                            player.pause()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling visibility change: ${e.message}")
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            gestureDetector?.onTouchEvent(event)
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                unregisterReceiver(configReceiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
            releasePlayer()
        }

        private fun initAndStartPlayer(holder: SurfaceHolder?) {
            if (holder == null || holder.surface == null || !holder.surface.isValid) {
                Log.d(TAG, "Surface is not valid or ready yet.")
                return
            }
            releasePlayer()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setSurface(holder.surface)
                    isLooping = true

                    // Apply audio volume
                    val volume = if (config.isMuted) 0f else 1f
                    setVolume(volume, volume)

                    // Data source: custom or built-in sample
                    if (config.hasCustomVideo()) {
                        val videoFile = File(config.videoPath!!)
                        setDataSource(videoFile.absolutePath)
                    } else {
                        val afd: AssetFileDescriptor = resources.openRawResourceFd(R.raw.sample_wallpaper)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }

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

        private fun reloadConfigAndApply() {
            // Check if player needs to be reinitialized for new data source
            initAndStartPlayer(surfaceHolder)
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
                // Ignore haptic feedback failure
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
