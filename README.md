# Pixel 10 动态视频壁纸 (Pixel Live Video Wallpaper)

一款专为 Google Pixel 10 系列（Android 15 / 16）深度调优的高性能、超低能耗**原生动态视频壁纸应用**。
支持将手机相册内的任意视频（MP4 / WebM / MKV）自由转为**桌面主屏幕**与**锁定屏幕**动态壁纸。

---

## 🚀 v1.1.0 核心突破：锁屏与桌面独立双视频引擎

> [!NOTE]
> 原生 Android 系统限制了第三方 App 无法同时为锁屏和主屏设置不同的独立动态壁纸服务。
> **本项目通过单服务结合 `KeyguardManager` 智能锁屏状态感知机制，攻克了这一系统级痛点！**

- 🔒 **锁屏专属动态视频**：手机点亮处于锁屏界面（等待人脸、指纹解锁）时自动播放。
- 🏠 **桌面主屏动态视频**：用户按压指纹或人脸解锁成功的瞬间，引擎在**毫秒级内自动无缝平滑切换**为桌面主屏专属视频。
- 🔄 **熄屏自动复位**：电源键锁屏熄屏后，引擎自动复位至锁屏准备状态，下次点亮屏幕依然呈现锁屏视频。
- 🎬 **一键切换模式**：除了「独立双视频模式」，依然保留「单视频统一模式」供用户自由选择。

---

## ✨ 其它核心特性

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
  - 内置 Pixel 10 模拟机型实时预览窗口，支持锁屏/桌面双视角即时预览与热重载。
- **声音与手势定制**：
  - 支持静音开关（默认静音，避免解锁与滑动时的声音打扰）。
  - 支持桌面双击空白处手势（双击切换静音 / 双击暂停与播放 / 触感震动反馈）。
- **隐私合规与持久化保存**：
  - 采用 Android 现代标准 Photo Picker，无需申请危险存储权限。
  - 选定视频自动流式保存至 App 本地私有沙箱目录，手机重启或系统清理后壁纸永不丢失失效。

---

## 📱 界面与使用流程

1. **设置壁纸模式**：选择 **「独立双视频模式 (推荐)」**。
2. **分别选择视频**：
   - 在 **「锁屏专属动态视频」** 卡片中导入开屏/锁屏特效视频。
   - 在 **「桌面主屏动态视频」** 卡片中导入桌面常驻视频。
3. **实时预览与调节**：在顶部的 Pixel 预览窗口自由切换「锁屏效果」与「桌面效果」进行即时预览，按需调节比例与声音。
4. **一键应用（关键步骤）**：
   - 点击底部「设为 Pixel 动态壁纸」。
   - 在 Pixel 官方弹出的壁纸预览界面右上角点击「设置壁纸」，务必选择 **「主屏幕和锁定屏幕」**。
   - 随后系统会将本服务同时挂载，应用内置的智能状态机将在锁屏播放锁屏视频、解锁后播放桌面视频！

---

## 🛠️ 技术栈与架构

- **语言与架构**：Kotlin 2.0+ / Android Architecture Components
- **UI 框架**：Jetpack Compose / Material 3 (Material You Dynamic Theme)
- **状态感知**：`KeyguardManager.KeyguardLockedStateListener` / `ACTION_USER_PRESENT` / `ACTION_SCREEN_OFF`
- **解码引擎**：Android Native `MediaPlayer` + `SurfaceHolder` (MediaCodec 硬件直出)
- **编译与目标**：CompileSdk 35/36, MinSdk 26, TargetSdk 35
- **构建工具**：Gradle 8.11.1 / Android Gradle Plugin 8.7.3

---

## 📥 下载安装

前往本仓库的 [Releases](https://github.com/lmikey9550/pixel-video-wallpaper/releases) 页面直接下载最新版 `PixelLiveWallpaper-v1.1.0.apk` 安装包即可。

或者使用 ADB 一键安装：
```bash
adb install PixelLiveWallpaper-v1.1.0.apk
```

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。
