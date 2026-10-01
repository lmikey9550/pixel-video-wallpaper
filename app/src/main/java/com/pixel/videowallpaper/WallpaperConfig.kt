package com.pixel.videowallpaper

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

enum class ScaleMode(val title: String, val description: String) {
    ASPECT_FIT("等比例居中 (画面不变形)", "严格保持原视频真实长宽比等比缩放居中，黑边衬底，画面绝不拉伸变形"),
    ORIGINAL_SIZE("原视频尺寸放置 (原始比例)", "以视频原本物理像素尺寸居中放置，黑边衬底，画面绝不拉伸变形");

    companion object {
        fun fromString(name: String?): ScaleMode {
            return when (name) {
                "ASPECT_FIT", "FIT_CENTER", "ORIGINAL_FIT" -> ASPECT_FIT
                "ORIGINAL_SIZE" -> ORIGINAL_SIZE
                else -> ASPECT_FIT
            }
        }
    }
}

enum class DoubleTapAction(val title: String) {
    MUTE_TOGGLE("切换静音 / 有声"),
    PLAY_PAUSE("暂停 / 继续播放"),
    NONE("无操作")
}

enum class WallpaperMode(val title: String, val description: String) {
    DUAL("独立双视频模式", "分别设置锁屏与桌面视频，解锁时智能无缝切换"),
    UNIFIED("单视频统一模式", "锁屏与主屏使用同一个动态视频")
}

enum class VideoTarget {
    HOME,
    LOCK
}

class WallpaperConfig(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixel_wallpaper_prefs", Context.MODE_PRIVATE)

    companion object {
        const val ACTION_CONFIG_CHANGED = "com.pixel.videowallpaper.ACTION_WALLPAPER_CONFIG_CHANGED"
        private const val KEY_WALLPAPER_MODE = "wallpaper_mode"
        private const val KEY_HOME_VIDEO_PATH = "home_video_path"
        private const val KEY_LOCK_VIDEO_PATH = "lock_video_path"
        private const val KEY_LEGACY_VIDEO_PATH = "video_path"
        private const val KEY_SCALE_MODE = "scale_mode"
        private const val KEY_IS_MUTED = "is_muted"
        private const val KEY_DOUBLE_TAP = "double_tap_action"

        const val HOME_VIDEO_FILENAME = "home_wallpaper.mp4"
        const val LOCK_VIDEO_FILENAME = "lock_wallpaper.mp4"
    }

    var wallpaperMode: WallpaperMode
        get() {
            val name = prefs.getString(KEY_WALLPAPER_MODE, WallpaperMode.DUAL.name)
            return try {
                WallpaperMode.valueOf(name ?: WallpaperMode.DUAL.name)
            } catch (e: Exception) {
                WallpaperMode.DUAL
            }
        }
        set(value) {
            prefs.edit().putString(KEY_WALLPAPER_MODE, value.name).apply()
            notifyConfigChanged()
        }

    var homeVideoPath: String?
        get() = prefs.getString(KEY_HOME_VIDEO_PATH, prefs.getString(KEY_LEGACY_VIDEO_PATH, null))
        set(value) {
            prefs.edit().putString(KEY_HOME_VIDEO_PATH, value).apply()
            notifyConfigChanged()
        }

    var lockVideoPath: String?
        get() = prefs.getString(KEY_LOCK_VIDEO_PATH, null)
        set(value) {
            prefs.edit().putString(KEY_LOCK_VIDEO_PATH, value).apply()
            notifyConfigChanged()
        }

    var scaleMode: ScaleMode
        get() {
            val name = prefs.getString(KEY_SCALE_MODE, ScaleMode.ASPECT_FIT.name)
            return ScaleMode.fromString(name)
        }
        set(value) {
            prefs.edit().putString(KEY_SCALE_MODE, value.name).apply()
            notifyConfigChanged()
        }

    var isMuted: Boolean
        get() = prefs.getBoolean(KEY_IS_MUTED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_MUTED, value).apply()
            notifyConfigChanged()
        }

    var doubleTapAction: DoubleTapAction
        get() {
            val name = prefs.getString(KEY_DOUBLE_TAP, DoubleTapAction.MUTE_TOGGLE.name)
            return try {
                DoubleTapAction.valueOf(name ?: DoubleTapAction.MUTE_TOGGLE.name)
            } catch (e: Exception) {
                DoubleTapAction.MUTE_TOGGLE
            }
        }
        set(value) {
            prefs.edit().putString(KEY_DOUBLE_TAP, value.name).apply()
            notifyConfigChanged()
        }

    /**
     * Save video for specific target (Home or Lock screen)
     */
    fun saveVideoForTarget(uri: Uri, target: VideoTarget): Boolean {
        return try {
            val filename = if (target == VideoTarget.HOME) HOME_VIDEO_FILENAME else LOCK_VIDEO_FILENAME
            val targetFile = File(context.filesDir, filename)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (target == VideoTarget.HOME) {
                homeVideoPath = targetFile.absolutePath
            } else {
                lockVideoPath = targetFile.absolutePath
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearVideo(target: VideoTarget) {
        val filename = if (target == VideoTarget.HOME) HOME_VIDEO_FILENAME else LOCK_VIDEO_FILENAME
        File(context.filesDir, filename).delete()
        if (target == VideoTarget.HOME) {
            homeVideoPath = null
        } else {
            lockVideoPath = null
        }
        notifyConfigChanged()
    }

    fun hasHomeVideo(): Boolean {
        val path = homeVideoPath ?: return false
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    fun hasLockVideo(): Boolean {
        val path = lockVideoPath ?: return false
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    fun getVideoPathForTarget(target: VideoTarget): String? {
        return if (target == VideoTarget.HOME) homeVideoPath else lockVideoPath
    }

    fun hasVideoForTarget(target: VideoTarget): Boolean {
        return if (target == VideoTarget.HOME) hasHomeVideo() else hasLockVideo()
    }

    fun notifyConfigChanged() {
        val intent = Intent(ACTION_CONFIG_CHANGED).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
