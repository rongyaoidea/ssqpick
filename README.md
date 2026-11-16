# 双色球选号机 (ssqpick)

Android 应用：摇号机选号、单式/复式投注、号码保存、历史战绩查询、官方开奖后自动对奖并计算金额。

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
│   │                   TicketMasks（票面位掩码，模拟/热循环复用）
│   │                   NumberGenerator（CSPRNG 选号）
│   │                   HistoryScanner（历史战绩）
│   │                   sim/Simulation（理论随机/历史重放、预算、分位、回撤）
│   │                   strategy/StrategyPicker（历史统计打分选号）
│   │                   OddsInfo（概率说明）
│   ├── store/         Android 库 —— 数据层
│   │   ├── network/   ktor 客户端，官方开奖公告接口
│   │   ├── db/        SQLDelight（draw / ticket 两张表）+ 建库
│   │   ├── mapper/    实体 ⇄ 领域模型
│   │   └── Repository.kt（DrawRepository / TicketRepository）
│   └── designsystem/  Compose 主题与号码球组件
└── feature/
    ├── machine/       摇号机（Compose Canvas 2D + 纯 Kotlin 刚体物理）
    │   ├── physics/   DrumWorld —— 无 Android 依赖的鼓内刚体模拟（可单测）
    │   ├── DrumStage  Canvas 绘制：双鼓 + 深度排序的球 + 结果条
    │   └── MachineController / MachineLayout / MachineScreen
    ├── ticket/       手选号码 + 已保存号码管理
    ├── data/         开奖数据、统计与模拟实验室
    └── check/        对奖与奖金计算
```

### 关键设计取舍

**1. 领域层零 Android 依赖。** `:core:model` 是纯 JVM 模块，注数计算、奖级判定、
对奖逻辑全部可在 JVM 上直接单测——这也是复式/胆拖对奖、模拟引擎能逐项覆盖的原因。

**2. 对奖用组合计数，不展开注。** 一张 10 红 4 蓝的复式有 `C(10,6)×4 = 3,360` 注，
20 红 16 蓝更是数十万注。逐注判定会让全历史扫描变成数亿次循环。
`TicketRules.prizeBreakdown` 用解析式直接算出各奖级的中奖注数：

```
胆码为空：命中 k 个红球的注数 = C(|D∩P|, k) · C(|P|-|D∩P|, 6-k)
带胆码 B：                    = C(|T∩D|, k-|B∩D|) · C(|拖|-|T∩D|, (6-|B|)-(k-|B∩D|))
```

正确性由 `TicketRulesTest.组合计数与暴力枚举一致` 保证——400 组随机票
与"老老实实展开再逐注判定"的参照实现逐项比对。

**3. 选号与表现分离。** 号码由 `SecureRandom` 决定，摇号机只负责把结果演出来。
物理引擎的碰撞动力学**不是**均匀随机源，直接拿"哪个球先掉出来"当结果会让号码分布
带系统性偏差，反而破坏公平性。所以机器先按 CSPRNG 抽好，再物理引导这些球到出口槽。

**4. 蓝球是 `List<Int>` 不是 `Int`。** 蓝球胆拖的一注同时含"胆码 + 拖码"两个蓝号，
任一个对上开奖蓝球都算中。用单个 `Int` 表达会直接漏掉胆码那注——
这个 bug 是被单元测试抓出来的，不是靠读代码看出来的。

**5. 出号真的落下来，排除只排除历史组合。** 出号分三段：管口抽吸 → 出号管自由落体 →
承接槽滚动，球落稳后才在结果条亮出号码。摇号前可勾选排除历史一/二/三等奖组合，
它只是把样本空间条件到"没中过这些奖级"的子集，**不提高单注中奖概率**；
没有同步开奖数据或大复式下无解时会明确提示。

**6. 模拟实验室用同一套规则做风险沙盘。** 理论随机 / 历史重放、每期机选 / 我的复式、
预算耗尽、收益分位、最大回撤、奖级命中 vs 理论都走 `TicketMasks` + `TicketRules`，
不另写一套对奖。实验是“如果一直这样买”的分布，不是下一期预测；
“最好路径”通常来自一次头奖，不能当成策略可复制。

**7. 策略选号只做历史打分。** 贝叶斯后验、指数衰减、遗漏回归、马尔可夫转移、
综合排序五种方法，用户选红/蓝数量和回看窗口后生成推荐并填入草稿。
`docs/STRATEGY_STUDY.md` 的 2019–2026 walk-forward 研究没有找到稳定最优窗口：
30/50/100/200/500/1000 期与全历史的差异基本在噪声内，因此默认 100 期只是产品选择，
**不宣称能提高中奖概率**。

**8. 账本、预算、备份都属于"记录"，不是"预测"。** 复盘页按真实「记录购买」算投入、
奖金与本月预算；数据页检查缺期/号码/奖金字段是否完整；备份只导出自己的号码与购买记录，
恢复是合并且会跳过重复。模拟实验室的 A/B 对照限定单式 6+1，同一预算和种子下比较
守号与每期换号——两组期望相同且为负，结果只描述波动。

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
| 3D/渲染 | Compose `Canvas`（2D，正交投影 + z 轴深度排序） |
| 异步 | kotlinx.coroutines / Flow |
| minSdk | 26 · compileSdk 37 |

---

## 文档

- [AGENTS.md](AGENTS.md) —— 构建命令与环境注意事项
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) —— 模块职责与数据流
- [docs/SIM_LAB.md](docs/SIM_LAB.md) —— 模拟实验室口径、指标与规模上限
- [docs/STRATEGY_STUDY.md](docs/STRATEGY_STUDY.md) —— 策略选号的窗口研究（walk-forward）
- [docs/OMISSION_STREAK_STUDY.md](docs/OMISSION_STREAK_STUDY.md) —— 遗漏、连开、重号与连号对下一期的影响
- [UI_UX.md](UI_UX.md) —— 界面结构与设计说明

## 本地构建

见 [AGENTS.md](AGENTS.md)。注意：**Android SDK 自带的 aapt2 是 x86-64 二进制**，
在 aarch64 环境（如 Android PRoot 容器）里无法执行，资源链接这一步只能交给 CI。
