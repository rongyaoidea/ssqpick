# AGENTS.md

## 构建与测试

```bash
./gradlew :core:model:test :feature:machine:testDebugUnitTest :feature:ticket:testDebugUnitTest   # 单元测试
./gradlew compileDebugKotlin                                    # 全模块 Kotlin 编译
./gradlew spotlessCheck                                         # ktlint 格式检查
./gradlew assembleDebug                                         # 完整 APK（见下方限制）
```

## 环境限制（重要）

**Android SDK 的 `aapt2` 是 x86-64 ELF 二进制。** 在 aarch64 环境（Android PRoot / Alpine）
下无法执行，`processDebugResources` 必然失败：

```
AAPT2 aapt2-9.4.0-15978811-linux Daemon #0: Daemon startup failed
cannot execute: required file not found   # PT_INTERP = /lib64/ld-linux-x86-64.so.2
```

所以本地只能验证到 **Kotlin 编译 + 单元测试**；APK 组装必须由 CI 完成。
不要试图在本地反复跑 `assembleDebug` 来排查资源问题——那台机器上不可能成功。

Java 相关进程若报 `Failed to mark memory page as executable`，需要
`export LD_PRELOAD=/usr/local/lib/libexecmem.so`（Android SELinux 拒绝 RW→RWX）。

## 约定

- 包名 **不能用 `data`**：`data` 是 Kotlin 保留字，SQLDelight 会把包名转义成
  `` com.ssqpick.`data` ``，生成的代码无法编译。数据层模块是 `:core:store` / `com.ssqpick.store`。
- SQLDelight 的 `INTEGER` 默认映射 Kotlin **`Long`**，不是 `Int`。
- `INTEGER AS Integer` 是无效写法，会生成 `import Integer`。
- `mapToList` / `mapToOneOrNull` 的接收者是 **`Flow<Query<T>>`**，需先 `.asFlow()`。
- 版本目录里 `compose-material3 = 1.10.0-alpha05` 这类号是 **Compose Multiplatform
  (JetBrains)** 的版本，不是 androidx 的；本项目用 androidx，最新稳定版是 `1.4.0`。
- AGP 9.0 起已内置 Kotlin 支持，再加 `org.jetbrains.kotlin.android` 会直接报错。

## 单测要点

改 `TicketRules` 的任何逻辑，必须先跑 `组合计数与暴力枚举一致`——
它把解析式和朴素展开实现逐项比对，是这套对奖逻辑唯一的安全网。

## 界面崩溃三连（都实测踩过）

**1. Scaffold 的 topBar 里绝不能用 `fillMaxSize()`。**
Material3 用 `Constraints.fixed(width, layoutHeight)` 测量 topBar —— 高度是**整个页面高度**。
顶部一填满，NavHost 就被挤成 0 高度，整屏内容消失，只剩 Scaffold 自己的头部和底栏可见。
`AppHeader` 同时加了 `heightIn(max = 120.dp)` 作硬保险。这个 bug 在真机上表现是
"除了标题栏和底部导航，什么都没有"，很容易被误判成渲染层问题。

**2. 不要用 `GLSurfaceView`。** `DrumStage` 用 Compose `Canvas` 画，**不要**换回去。
`SurfaceView` 的 surface 由系统单独合成，不跟随父视图滚动；且 `onResume()` 只在
生命周期**状态切换**时回调，合成时宿主已 RESUMED，渲染线程因此从未启动；
还额外依赖设备 GLES 3.0、EGL 配置与着色器编译，任一失败都是静默黑屏。

**3. 物理层 `DrumWorld` 与渲染方式无关**（纯 Kotlin，无 Android 依赖，有独立单测），
改渲染不影响它，可放心复用。

排查界面"整块空白"时，先怀疑布局把 body 挤成 0 高度，而不是渲染层。

## 设计纪律

界面遵循 `awesome-opus-5.5-video` 技能里 Motion/UI 案例的设计约束，
详见 `UI_UX.md` 的"设计纪律"一节。要点：暖灰画布、黑白灰控件、
饱和色只给球（红蓝本身是语义色）；弹簧动效且过冲极小（阻尼 0.86），
**禁止 bounce / glow / 粒子 / UI 控件渐变**。布线与新增动效前先读那一节。
