# Pixel 10 动态视频壁纸 (Pixel Live Video Wallpaper)

一款专为 Google Pixel 10 系列（Android 15 / 16）深度调优的高性能、超低能耗**原生动态视频壁纸应用**。
支持将手机相册内的任意视频（MP4 / WebM / MKV）自由转为**桌面主屏幕**或**主屏幕与锁定屏幕**动态壁纸。

---

## ✨ 核心特性

- **Pixel 10 屏幕满屏适配**：
  - **满屏居中填充 (Center Crop)**：采用硬件级 `VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING`，自动撑满 Pixel 10 的 20:9 比例 OLED 屏幕，无黑边、无拉伸变形。
  - **等比居中适应 (Fit Center)**：原汁原味展示视频宽高比。
- **近乎零待机功耗 (Smart Power Saving)**：
  - 严密监听 Android 系统底层 `onVisibilityChanged(visible)` 可见性状态。
  - 当手机熄屏、处于其他全屏应用或桌面被遮挡时，**解码管线立即停止 (0% CPU/GPU 开销)**；仅在桌面或锁屏露出时渲染。
- **原生系统级动态壁纸集成**：
  - 基于 Android 官方标准 `android.service.wallpaper.WallpaperService`。
  - 自动收录至 Pixel 系统的「壁纸与样式」->「动态壁纸」库中。
- **现代化 Material You 界面**：
  - 采用 Jetpack Compose + Material 3 开发，完美适配 Pixel 原生动态取色 (Dynamic Color)。
  - 内置 Pixel 10 模拟机型实时预览窗口，支持秒级热重载。
- **声音与手势定制**：
  - 支持静音开关（默认静音，避免解锁与滑动时的声音打扰）。
  - 支持桌面双击空白处手势（双击切换静音 / 双击暂停与播放 / 触感震动反馈）。
- **隐私合规与持久化保存**：
  - 采用 Android 现代标准 Photo Picker，无需申请危险存储权限。
  - 选定视频自动流式保存至 App 本地私有沙箱目录，手机重启或系统清理后壁纸永不丢失失效。

---

## 📱 界面与使用流程

1. **从相册选取视频**：点击「从手机相册选择任意视频」，选取喜欢的实拍、动漫或风景视频。
2. **实时预览与调节**：在顶部的 Pixel 预览窗口即时查看动态效果，自由切换「满屏裁剪」或「等比适应」，按需开关音频与双击手势。
3. **一键设为壁纸**：点击底部「设为 Pixel 动态壁纸 (桌面 / 锁屏)」，在 Pixel 原生壁纸面板点击右上角「设置壁纸」，选择：
   - **主屏幕**
   - **主屏幕和锁定屏幕**

---

## 🛠️ 技术栈与架构

- **语言与架构**：Kotlin 2.0+ / Android Architecture Components
- **UI 框架**：Jetpack Compose / Material 3 (Material You Dynamic Theme)
- **解码引擎**：Android Native `MediaPlayer` + `SurfaceHolder` (MediaCodec 硬件直出)
- **编译与目标**：CompileSdk 35/36, MinSdk 26, TargetSdk 35
- **构建工具**：Gradle 8.11.1 / Android Gradle Plugin 8.7.3

---

## 📥 下载安装

前往本仓库的 [Releases](https://github.com/lmikey9550/pixel-video-wallpaper/releases) 页面直接下载 `PixelLiveWallpaper-debug.apk` 安装包即可。

或者使用 ADB 一键安装：
```bash
adb install PixelLiveWallpaper-debug.apk
```

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。
