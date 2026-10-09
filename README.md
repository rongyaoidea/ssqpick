# 双色球选号机 (ssqpick)

Android 应用：3D 摇号机选号、单式/复式投注、号码保存、历史战绩查询、官方开奖后自动对奖并计算金额。

> **关于"预测"的一句实话**：双色球每期开奖是独立随机事件，中一等奖概率恒为
> `1 / C(33,6) / 16 = 1/17,721,088`。本应用不提供任何"预测模型"——
> 历史数据无法改善中奖概率，这一点在开奖数据模块的说明里对用户是透明的。
> 应用的价值在于**记录、核对与算账**，而不是提高运气。

---

## 架构

```
ssqpick/
├── core/
│   ├── model/         纯 Kotlin JVM 库，无 Android 依赖 —— 全部规则与可测逻辑
│   │   ├── model/     Draw / PrizeTier / TicketDraft / Ticket / Bet
│   │   └── rules/     TicketRules（校验·注数·复式展开·对奖）
│   │                   NumberGenerator（CSPRNG 选号）
│   │                   HistoryScanner（历史战绩）
│   │                   OddsInfo（概率说明）
│   ├── store/         Android 库 —— 数据层
│   │   ├── network/   ktor 客户端，官方开奖公告接口
│   │   ├── db/        SQLDelight（draw / ticket 两张表）+ 建库
│   │   ├── mapper/    实体 ⇄ 领域模型
│   │   └── Repository.kt（DrawRepository / TicketRepository）
│   └── designsystem/  Compose 主题与号码球组件
└── feature/
    ├── machine/       3D 摇号机
    │   ├── physics/   DrumWorld —— 纯 Kotlin 刚体模拟（可单测）
    │   ├── gl/        OpenGL ES 3.0 渲染（实例化绘制 + 纹理图集）
    │   ├── MachineLayout / MachineController / MachineView / MachineScreen
    ├── ticket/       手选号码 + 已保存号码管理
    └── check/        对奖与奖金计算
```

### 关键设计取舍

**1. 领域层零 Android 依赖。** `:core:model` 是纯 JVM 模块，注数计算、奖级判定、
对奖逻辑全部可在 JVM 上直接单测——这也是 27 项单元测试能覆盖"复式/胆拖对奖"的原因。

**2. 对奖用组合计数，不展开注。** 一张 10 红 4 蓝的复式有 `C(10,6)×4 = 3,360` 注，
20 红 16 蓝更是数十万注。逐注判定会让全历史扫描变成数亿次循环。
`TicketRules.prizeBreakdown` 用解析式直接算出各奖级的中奖注数：

```
胆码为空：命中 k 个红球的注数 = C(|D∩P|, k) · C(|P|-|D∩P|, 6-k)
带胆码 B：                    = C(|T∩D|, k-|B∩D|) · C(|拖|-|T∩D|, (6-|B|)-(k-|B∩D|))
```

正确性由 `TicketRulesTest.组合计数与暴力枚举一致` 保证——400 组随机票
与"老老实实展开再逐注判定"的参照实现逐项比对。

**3. 选号与表现分离。** 号码由 `SecureRandom` 决定，3D 机器只负责把结果演出来。
物理引擎的碰撞动力学**不是**均匀随机源，直接拿"哪个球先掉出来"当结果会让号码分布
带系统性偏差，反而破坏公平性。所以机器先按 CSPRNG 抽好，再物理引导这些球到出口槽。

**4. 蓝球是 `List<Int>` 不是 `Int`。** 蓝球胆拖的一注同时含"胆码 + 拖码"两个蓝号，
任一个对上开奖蓝球都算中。用单个 `Int` 表达会直接漏掉胆码那注——
这个 bug 是被单元测试抓出来的，不是靠读代码看出来的。

---

## 开奖数据

中国福利彩票发行管理中心官方公告接口（`www.cwl.gov.cn`）：

- 必须带 `Referer` 请求头，否则服务端拒绝；
- 服务端仅提供 **2013-01-01 起** 的开奖数据（当前约 2000+ 期），
  更早的期号区间查询返回空数组——这是服务端限制，不是客户端缺陷。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.4.10（`com.android.tools.build` 9.4.0 已内置 Kotlin 支持，无需 `kotlin.android` 插件） |
| UI | Jetpack Compose 1.12.0 + Material3 1.4.0 |
| DI | Koin 4.2.2 |
| 网络 | ktor 3.5.2（OkHttp 引擎） |
| 数据库 | SQLDelight 2.3.2 |
| 3D | OpenGL ES 3.0，实例化绘制 + 8×8 号码贴图图集 |
| 异步 | kotlinx.coroutines / Flow |
| minSdk | 26 · compileSdk 37 |

---

## 文档

- [AGENTS.md](AGENTS.md) —— 构建命令与环境注意事项
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) —— 模块职责与数据流
- [UI_UX.md](UI_UX.md) —— 界面结构与设计说明

## 本地构建

见 [AGENTS.md](AGENTS.md)。注意：**Android SDK 自带的 aapt2 是 x86-64 二进制**，
在 aarch64 环境（如 Android PRoot 容器）里无法执行，资源链接这一步只能交给 CI。
