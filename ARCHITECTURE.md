# PromptFlow 系统架构设计 (ARCHITECTURE.md)

> 版本：v1.0 | 架构风格：Clean Architecture + MVI/MVVM + Compose First

---

## 1. 总体架构拓扑 (High-Level Topology)

PromptFlow 遵循现代 Android 开发标准规范，采用单工程、高内聚的分层设计，确保独立开发者一人维护的极简性与可测试性。

```
+-----------------------------------------------------------------------+
|                           UI Layer (Jetpack Compose)                  |
|  [ScriptHubScreen]  [VideoPrompterScreen]  [AudioPrompterScreen]      |
|                      [FloatingOverlayView (Compose)]                  |
+-----------------------------------------------------------------------+
                                   | (StateFlow / Actions)
+-----------------------------------------------------------------------+
|                         ViewModel / State Machine                     |
|  [ScriptViewModel]  [VideoRecordViewModel]  [AudioRecordViewModel]    |
|                      [PrompterScrollController]                       |
+-----------------------------------------------------------------------+
                                   | (UseCases)
+-----------------------------------------------------------------------+
|                         Domain / Business Rules                       |
|  [CalculateScrollSpeed]  [FormatRecordingMetadata]  [FormatEyeOffset] |
+-----------------------------------------------------------------------+
                                   |
+-----------------------------------------------------------------------+
|                         Data & System Services                        |
|  +--------------------+  +--------------------+  +-----------------+  |
|  |   Camera Manager   |  |   Audio Recorder   |  |   Room Database |  |
|  |   (CameraX 1.4)    |  |  (MediaRecorder /  |  |   (ScriptDao,   |  |
|  |                    |  |      Media3)       |  |    Entity)      |  |
|  +--------------------+  +--------------------+  +-----------------+  |
|  +--------------------+  +--------------------+  +-----------------+  |
|  |  Floating Service  |  | Bluetooth Key Hook |  |  DataStore Pref |  |
|  | (WindowManager +   |  | (KeyEventReceiver) |  | (Speed, Fonts,  |  |
|  |  ForegroundService)|  |                    |  |  Opacity)       |  |
|  +--------------------+  +--------------------+  +-----------------+  |
+-----------------------------------------------------------------------+
```

---

## 2. 核心技术选型与实现细节

### 2.1 视频录制提词引擎 (CameraX Video Subsystem)
- **技术选型**：`androidx.camera:camera-camera2`, `androidx.camera:camera-video`, `androidx.camera:camera-lifecycle`, `androidx.camera:camera-view`
- **设计决策**：
  - 使用 `Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()` 构建视频录像用例。
  - 采用双层叠加：底层为 `AndroidView` 承载的 `PreviewView`，顶层为纯 Compose 渲染的半透明提词器图层。
  - **眼神聚焦优化算法 (Eye Focus Tuning)**：
    提词器文本滚动框强制限制最大宽度（例如不超过屏幕宽度的 65%），并且始终向摄像头方向靠拢（前置摄像头打孔在顶部居中则向顶居中靠拢；打孔在左上角则支持靠左上微调），使视线焦点自然保持在镜头极小视差夹角内。

### 2.2 纯音频录制引擎 (Audio Subsystem)
- **技术选型**：`android.media.MediaRecorder`（简单高保真 M4A/AAC）或底层 `AudioRecord`（用于实时波形采集）
- **设计决策**：
  - 封装 `AudioRecordingManager`，对外暴露统一的状态机：
    `Idle` -> `Recording(durationMs, currentAmplitude)` -> `Paused` -> `Completed(fileUri)`
  - 采集振幅（`getMaxAmplitude()`），在 Compose 界面中实时绘制平滑波形条，给创作者清晰的拾音反馈。
  - 录制保存路径采用 `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`，录制结束自动刷新媒体库。

### 2.3 全局悬浮窗引擎 (Floating Window Subsystem)
- **技术选型**：`android.app.Service` (带有 `FOREGROUND_SERVICE_SPECIAL_USE` 声明) + `WindowManager` + `androidx.compose.ui.platform.ComposeView`
- **设计难点与解决方案**：
  - **生命周期接管**：通过 `LifecycleOwner` 拓展，将 `ComposeView` 挂载到悬浮窗时，手动注入 `ViewTreeLifecycleOwner`、`ViewTreeViewModelStoreOwner` 和 `ViewTreeSavedStateRegistryOwner`，使得悬浮窗内能完整复用 Compose Material 3 组件和状态管理。
  - **手势交互**：
    - 悬浮窗分“移动把手区”和“内容滚动区”。
    - 移动把手响应触摸并动态更新 `WindowManager.LayoutParams.x` 与 `y`。
    - 边缘自动吸附（松手时通过动画平滑贴靠至屏幕左边缘或右边缘）。

### 2.4 提词滚动控制器 (Prompter Engine)
- **核心逻辑**：
  - 基于固定时钟驱动（如基于协程 `ticker` 或 Compose `LaunchedEffect(withFrameNanos)`），消除不同机型和刷新率的滚动跳帧。
  - 状态包括：`isPlaying: Boolean`, `scrollSpeed: Float`（像素/秒 或 行/秒）, `currentOffset: Float`, `totalHeight: Float`。
  - 支持**外接蓝牙事件监听**（如音量+、音量-、空格键、回车键）：在 Activity/Service 级别拦截 `onKeyDown`，映射到“播放/暂停”、“速度微调”、“回滚 5 秒”。

---

## 3. 代码目录结构规范 (Project Structure)

```
app/src/main/java/com/promptflow/app/
├── core/
│   ├── designsystem/          # Compose 主题、配色规范、字体、通用的 Glassmorphic 悬浮卡片样式
│   ├── model/                 # 核心数据模型 (Script, RecordingMode, PrompterConfig)
│   └── util/                  # 权限管理辅助器、媒体文件保存器、时间格式化
├── data/
│   ├── local/                 # Room Database (ScriptEntity, ScriptDao)
│   ├── preferences/           # DataStore (用户默认语速、字号、透明度偏好)
│   └── repository/            # ScriptRepository, MediaRepository
├── domain/
│   └── usecase/               # 核心业务逻辑用例
├── feature/
│   ├── scripthub/             # 台本列表与编辑器 UI & ViewModel
│   ├── videoprompter/         # 视频录制提词 UI & ViewModel (CameraX 结合)
│   ├── audioprompter/         # 音频录制提词 UI & ViewModel (波形可视化与录音)
│   ├── overlay/               # 全局悬浮窗 Service 与 Compose 悬浮卡片
│   └── settings/              # 遥控配置与设置页
└── PromptFlowApp.kt           # Application 入口
```

---

## 4. 依赖选型建议 (Gradle Dependencies)

```toml
[versions]
kotlin = "2.0.20"
composeBom = "2024.09.02"
camerax = "1.4.0"
room = "2.6.1"
datastore = "1.1.1"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-video = { group = "androidx.camera", name = "camera-video", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

---

## 5. 防御性设计与边界处理 (Defensive Boundaries)

1. **相机被占用 / 硬件中断**：
   - 监听 CameraX `CameraState.ERROR`，友好降级并提示用户“其他应用正在使用相机，已暂停录制”。
2. **麦克风无声音 / 权限缺失**：
   - 在启动录音前校验音频输入设备是否就绪，若音轨静音持续 2 秒以上，提供视觉气泡提示“未检测到声音输入，请检查麦克风权限”。
3. **系统杀后台与悬浮窗保活**：
   - 悬浮窗启动时绑定带常驻通知栏（Notification with Stop Action）的 Foreground Service，防止悬浮录像/念词时被系统低内存杀死。
4. **存储空间预警**：
   - 录像开始前检测设备剩余磁盘空间，若低于 500MB 禁止开启 1080P/4K 录制，避免录一半崩溃损坏文件。
