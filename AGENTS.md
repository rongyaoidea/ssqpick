# AGENTS.md

## 构建与测试

```bash
./gradlew :core:model:test :feature:machine:testDebugUnitTest   # 单元测试（27 项）
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
