package com.pixel.videowallpaper

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

enum class ScaleMode(val title: String, val description: String) {
    CENTER_CROP("满屏填充 (Center Crop)", "自动适配 Pixel 10 全屏，无黑边居中填充 (推荐)"),
    FIT_CENTER("等比适应 (Fit Center)", "完整保留原视频画面比例，多余部分黑边填充")
}

enum class DoubleTapAction(val title: String) {
    MUTE_TOGGLE("切换静音 / 有声"),
    PLAY_PAUSE("暂停 / 继续播放"),
    NONE("无操作")
}

class WallpaperConfig(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixel_wallpaper_prefs", Context.MODE_PRIVATE)

    companion object {
        const val ACTION_CONFIG_CHANGED = "com.pixel.videowallpaper.ACTION_WALLPAPER_CONFIG_CHANGED"
        private const val KEY_VIDEO_PATH = "video_path"
        private const val KEY_SCALE_MODE = "scale_mode"
        private const val KEY_IS_MUTED = "is_muted"
        private const val KEY_DOUBLE_TAP = "double_tap_action"
        const val VIDEO_FILENAME = "active_wallpaper.mp4"
    }

    var videoPath: String?
        get() = prefs.getString(KEY_VIDEO_PATH, null)
        set(value) {
            prefs.edit().putString(KEY_VIDEO_PATH, value).apply()
            notifyConfigChanged()
        }

    var scaleMode: ScaleMode
        get() {
            val name = prefs.getString(KEY_SCALE_MODE, ScaleMode.CENTER_CROP.name)
            return try {
                ScaleMode.valueOf(name ?: ScaleMode.CENTER_CROP.name)
            } catch (e: Exception) {
                ScaleMode.CENTER_CROP
            }
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
     * Copy selected media Uri into internal files directory for persistent reliable access.
     */
    fun saveVideoFromUri(uri: Uri): Boolean {
        return try {
            val targetFile = File(context.filesDir, VIDEO_FILENAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            videoPath = targetFile.absolutePath
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun hasCustomVideo(): Boolean {
        val path = videoPath ?: return false
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    fun notifyConfigChanged() {
        val intent = Intent(ACTION_CONFIG_CHANGED).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
