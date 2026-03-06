# MrsHudson Android 开发 - 并行 Task 代理执行规划（Wave版）

## 概述

本文档将 Android 开发的 12 个任务按 **Wave（阶段）** 组织，通过 **5 个并行的 Task 代理**同时执行任务，**最大化并行度**以提升开发效率。

## 🎯 并行执行核心理念

1. **Wave 内最大化并行**: 同一 Wave 内的多个任务，分配给不同代理**同时执行**
2. **代理内任务并行**: 单个代理内的多个子任务，使用 `run_in_background` **并行执行**
3. **减少串行依赖**: 扁平化任务依赖，允许更多任务同时启动
4. **包隔离避免冲突**: 每个代理只修改指定包/目录

## 📊 各 Wave 总览（并行度优化）

| Wave | 阶段名称 | 并行代理 | 并行度 | 预计时间 | 关键交付物 |
|------|----------|----------|--------|----------|------------|
| Wave 1-2 | 基础架构 | Task 1 | 1 | 2天 | 可编译项目+认证+主框架 |
| Wave 3 | 核心功能并行 | Task 1/2/3/4/5 | **4** ★ | 2天 | AI对话、日历、待办、天气、路线 |
| Wave 4 | 高级功能并行 | Task 1/2/3/4 | **4** ★ | 1.5天 | 语音输入、推送、离线DB、优化 |
| Wave 5-6 | 收尾发布 | Task 3/5 | **2** | 1.5天 | 离线完善、打包发布 |

**预计总工期**: 5-7天（5个Task并行执行）
**相比串行**: 节省约50%时间

---

## 🚀 并行 Task 代理执行策略

**核心原则**: 通过 Claude Code 的 **Task 工具** 启动多个并行的子代理，各自负责不同的功能模块，通过**包级别隔离**避免冲突。

**最大化并行度原则**:
1. **Wave 内并行**: 同一 Wave 内的多个任务，尽可能分配给不同代理并行执行
2. **代理内并行**: 单个代理内的多个任务，使用 Task 工具的 `run_in_background` 参数并行执行
3. **依赖扁平化**: 减少任务间的依赖关系，允许更多任务同时启动
4. **包隔离**: 每个代理只修改自己负责的包/目录，避免文件冲突

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    并行 Task 代理执行模型 (5个代理)                          │
│                    mrshudson-android/                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Task 1        Task 2        Task 3        Task 4        Task 5          │
│   (基础架构)     (AI对话)       (日历+待办)    (天气+路线)    (推送+打包)     │
│                                                                             │
│   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │
│   │ Wave 1  │  │         │  │         │  │         │  │         │          │
│   │ Wave 2  │  │         │  │         │  │         │  │         │          │
│   └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘          │
│        │            │            │            │            │               │
│        ▼            ▼            ▼            ▼            ▼               │
│   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │
│   │ Wave 3  │  │ Wave 3  │  │ Wave 3  │  │ Wave 3  │  │         │          │
│   │         │  │         │  │         │  │         │  │         │          │
│   │ Auth    │  │ Chat    │  │ Calendar│  │ Weather │  │         │          │
│   │ + Main  │  │         │  │ Todo    │  │ Route   │  │         │          │
│   └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘          │
│        │            │            │            │            │               │
│        ▼            ▼            ▼            ▼            ▼               │
│   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │
│   │ Wave 4  │  │ Wave 4  │  │ Wave 4  │  │ Wave 4  │  │ Wave 5  │          │
│   │         │  │ Voice   │  │ Offline │  │ FCM     │  │ Build   │          │
│   └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘          │
│        │            │            │            │            │               │
│        ▼            ▼            ▼            ▼            ▼               │
│   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │
│   │ Wave 5  │  │         │  │ Wave 5  │  │         │  │ Wave 6  │          │
│   │ 优化    │  │         │  │ Offline │  │         │  │ Release │          │
│   └────┬────┘  └─────────┘  └────┬────┘  └─────────┘  └────┬────┘          │
│        │                          │                         │               │
│        ▼                          ▼                         ▼               │
│                         最终编译验证                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 并行执行命令模板

**启动 5 个并行 Task 代理**（推荐）:

```bash
# 同时启动 5 个 Task 代理
Task 1: "执行 Wave 1 + Wave 2 - 初始化项目、认证、主框架"
Task 2: "执行 Wave 3 - AI对话功能"
Task 3: "执行 Wave 3 - 日历+待办功能 (并行)"
Task 4: "执行 Wave 3 - 天气+路线功能 (并行)"
Task 5: "等待 Wave 3 完成后执行打包配置"

# Wave 3 完成后，同时启动 Wave 4
Task 2: "执行 Wave 4 - 语音输入"
Task 3: "执行 Wave 4 - 离线模式DB"
Task 4: "执行 Wave 4 - FCM推送"
Task 5: "并行执行 Wave 5 + Wave 6"
```

### Task 代理分配建议（按并行度优化）

| 代理数 | 分配方案 | 预计工期 | 并行度 |
|--------|---------|----------|--------|
| 1个 Task | 按Wave顺序串行执行 | 15-20天 | 1 |
| 2个 Task | Task1负责基础+打包，Task2负责核心功能 | 10-12天 | 2 |
| 3个 Task | Task1基础+认证，Task2对话+语音，Task3日历+待办+离线 | 8-10天 | 3 |
| **4个 Task** | **推荐**：基础/对话/日历待办/天气路线 各自独立 | **6-8天** | **4** |
| **5个 Task** | **最优**：基础/对话/日历待办/天气路线/推送打包 | **5-7天** | **5** |

**推荐配置**: 5个 Task 代理，可达到最高并行效率

### 并行 Task 代理执行流程

### 并行执行流程图（最大化并行度）

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           并行执行时间线 (5 Task 代理)                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Day 1     Day 2     Day 3     Day 4     Day 5     Day 6     Day 7           │
│                                                                                 │
│  阶段1: 基础架构 (并行度=1)                                                     │
│  Task1 ════════ Wave1-2 (初始化 + 认证 + 主框架)                              │
│                                                                                 │
│  阶段2: 核心功能并行开发 (并行度=4) ★ 最关键                                    │
│  Task1 ════════ Wave3 - Auth/Main 完善                                       │
│  Task2 ════════ Wave3 - AI对话 ★                                             │
│  Task3 ════════ Wave3 - 日历 ★     ╔═══ 并行: 8.5 日历 + 8.6 待办           │
│  Task4 ════════ Wave3 - 天气 ★     ╔═══ 并行: 8.7 天气 + 8.8 路线           │
│  Task5 ════════ Wave3 - 打包准备 ★                                            │
│                                                                                 │
│  阶段3: 高级功能并行 (并行度=4) ★                                              │
│  Task1 ════════ Wave4 - 优化                                                  │
│  Task2 ════════ Wave4 - 语音输入 ★                                           │
│  Task3 ════════ Wave4 - 离线DB ★     ╔═══ 阶段1: Room + DAO + SyncManager   │
│  Task4 ════════ Wave4 - FCM推送 ★                                            │
│                                                                                 │
│  阶段4: 收尾与发布 (并行度=2)                                                   │
│  Task3 ════════ Wave5 - 离线完善     ╔═══ 阶段2: Repository离线支持          │
│  Task5 ════════ Wave5-6 - 打包发布 ★                                         │
│                                                                                 │
│  最终验证: ./gradlew build && ./gradlew assembleRelease                        │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 快速启动命令（5个代理并行执行）

```bash
# 步骤1: 启动 Task 1 初始化项目 + 认证 + 主框架
Task 1: "执行 Wave 1 + Wave 2 - 初始化Android项目、实现认证、主框架"

# 步骤2: 同时启动 4 个代理执行 Wave 3 (核心功能并行)
Task 1: "Wave 3 - Auth/Main 完善"
Task 2: "Wave 3 - AI对话功能 (ChatApi, ChatScreen, ChatViewModel)"
Task 3: "Wave 3 - 日历+待办功能 - 同时执行 Task 8.5日历 + 8.6待办"
Task 4: "Wave 3 - 天气+路线功能 - 同时执行 Task 8.7天气 + 8.8路线"
Task 5: "Wave 5 - 打包配置准备 (签名、ProGuard、build配置)"

# 步骤3: 同时启动 5 个代理执行 Wave 4-5 (高级功能)
Task 1: "Wave 4 - 优化用户体验"
Task 2: "Wave 4 - 语音输入功能"
Task 3: "Wave 4 - 离线模式DB准备 (Room+DAO+SyncManager)"
Task 4: "Wave 4 - FCM推送集成"
Task 5: "Wave 5-6 - 打包发布配置"

# 步骤4: 最终验证
./gradlew build
./gradlew assembleRelease
```
### 文件冲突预防策略

由于所有 Task 代理共享同一个工作目录，**必须遵守以下规则**:

| 规则 | 说明 |
|------|------|
| **包级别隔离** | 每个 Task 只修改分配给自己的包/目录 |
| **文件锁定** | 开始编辑前声明"我正在修改Xxx.kt" |
| **不修改他人文件** | Task 1 不要修改 Calendar 相关的任何文件 |
| **编译验证** | 每完成一个 Wave 就执行 `./gradlew build` 验证 |

### 工作目录

所有 Task 代理都在同一个目录下工作：

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android
```

**无需 Git 操作**，直接在同一个代码库上协作。

---

## 🔄 并行 Task 代理时间线（优化版）

```
Day 1    Day 2    Day 3    Day 4    Day 5    Day 6    Day 7
─────────────────────────────────────────────────────────────────

Task 1
├─ Wave1-2 [8.1+8.2+8.3]──────────────────────────────────────┤
                         │
                         ▼ 并行启动 (4个Task代理同时执行)
Task 1 ───────── Wave3 - Auth/Main完善 ★
Task 2 ───────── Wave3 - AI对话 [8.4] ★
Task 3 ───────── Wave3 - 日历+待办 [8.5+8.6] ★ 并行
Task 4 ───────── Wave3 - 天气+路线 [8.7+8.8] ★ 并行
Task 5 ───────── Wave3 - 打包配置准备 ★

                         │
                         ▼ Wave3完成

Task 1 ───────── Wave4 - 优化 ★
Task 2 ───────── Wave4 - 语音 [8.11] ★
Task 3 ───────── Wave4 - 离线DB [8.10阶段1] ★
Task 4 ───────── Wave4 - FCM [8.9] ★

                         │
                         ▼ Wave4完成

Task 3 ───────── Wave5 - 离线完善 [8.10阶段2] ★
Task 5 ───────── Wave5-6 - 打包发布 [8.12] ★

                         │
                         ▼ 最终验证

./gradlew build && ./gradlew assembleRelease

关键路径: Wave1-2 → Wave3(4并行) → Wave4(4并行) → Wave5-6 (约7天)
并行度: Wave3/Wave4 = 4, Wave5 = 2
```

**并行度提升说明**:
- Wave 1-2: 串行 (1个Task) - 基础必须先完成
- Wave 3: **4个Task并行** - AI对话/日历待办/天气路线/打包配置同时执行
- Wave 4: **4个Task并行** - 语音/离线DB/FCM/优化同时执行
- Wave 5-6: 2个Task并行 - 离线完善 + 打包发布

---

## 📋 Wave 详细规划

### Wave 1: 项目初始化 ⏱️ 1天

**执行代理**: Task 1
**阻塞**: Wave 2, Wave 3, Wave 4, Wave 5, Wave 6
**状态**: 阻塞节点

#### 目标
建立 Android 项目基础架构，确保项目可编译运行。

#### 任务清单

| 任务ID | 任务名称 | 输出文件 | 验收标准 |
|--------|----------|----------|----------|
| 8.1 | 初始化 Android 项目结构 | `mrshudson-android/` 完整项目骨架 | `./gradlew build` 成功 |

#### 技术要点
- minSdk: 26, targetSdk: 34
- 技术栈: Kotlin + Jetpack Compose + MVVM + Hilt
- 核心依赖: Retrofit 2.9, Room 2.6, Navigation Compose 2.7

#### Task 1 执行命令

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 启动 Wave 1
echo "=== 开始执行 Wave 1 - 项目初始化 ==="

# Task 8.1 - 初始化 Android 项目
# [执行 Wave 1 Prompt]

# 编译检查
./gradlew build -q
if [ $? -eq 0 ]; then
    echo "✅ Wave 1 编译通过"
else
    echo "❌ Wave 1 编译失败"
    exit 1
fi

echo "=== Wave 1 完成，通知 Task 2/C/D 可以准备Wave 3 ==="
```

#### Wave 1 完成后检查点 ✅

- [x] `./gradlew build` 编译成功
- [x] 项目结构符合规范（Kotlin DSL）
- [x] 依赖版本正确（Compose BOM 2024.02.00, Hilt 2.50 等）
- [x] MainActivity.kt 存在且为 Compose 入口
- [x] Hilt 依赖注入配置完成
- [x] Retrofit + OkHttp 基础配置完成
- [x] Room 数据库基础配置完成

**阻塞解除信号**: Wave 1完成，Task 2/C/D可以开始Wave 3

---

### Wave 2: 认证与主框架 ⏱️ 2天

**执行代理**: Task 1
**依赖**: Wave 1 完成
**阻塞**: Wave 3 所有任务
**状态**: 阻塞节点

#### 目标
实现用户认证和主页面框架，为功能模块开发提供基础。

#### 任务清单

| 顺序 | 任务ID | 任务名称 | 输出文件 | 依赖 |
|------|--------|----------|----------|------|
| 1 | 8.2 | 实现用户认证模块 | `AuthApi.kt`, `LoginScreen.kt`, `AuthRepository.kt` | 8.1 |
| 2 | 8.3 | 实现主页面框架与底部导航 | `MainScreen.kt`, `BottomNavItem.kt`, 5个占位页面 | 8.2 |

#### 接口契约（供后续 Wave 使用）

```kotlin
// BottomNavItem 枚举（所有 Wave 遵守）
enum class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    CHAT("chat", "对话", Icons.Default.Chat),
    CALENDAR("calendar", "日历", Icons.Default.CalendarToday),
    TODO("todo", "待办", Icons.Default.CheckCircle),
    WEATHER("weather", "天气", Icons.Default.WbSunny),
    ROUTE("route", "路线", Icons.Default.Map)
}
```

#### Task 1 执行命令

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 继续 Wave 2（在Wave 1同目录）
echo "=== 开始执行 Wave 2 - 认证与主框架 ==="

# [执行 Wave 2 Prompts]
# Task 8.2 - 用户认证
# Task 8.3 - 主框架

# 编译检查
./gradlew build -q
if [ $? -eq 0 ]; then
    echo "✅ Wave 2 编译通过"
else
    echo "❌ Wave 2 编译失败"
    exit 1
fi

echo "=== Wave 2 完成，通知 Task 2/C/D 可以开始 Wave 3 ==="
```

#### Wave 2 完成后检查点 ✅

- [x] admin/admin 可登录成功
- [x] Token 正确保存到 DataStore（使用 DataStore Preferences）
- [x] 自动登录逻辑工作正常
- [x] 登录错误显示友好提示（Snackbar）
- [x] 底部5个导航标签可点击切换
- [x] 切换时页面状态保持（rememberSaveable）
- [x] 各功能页面占位符存在（Chat/Calendar/Todo/Weather/Route）
- [x] Material3 主题配置完成（支持亮/暗色模式）
- [x] MrsHudson 品牌色已定义

**阻塞解除信号**: Wave 2完成，Task 2/C/D可以开始Wave 3

---

### Wave 3: 核心功能并行开发 ⏱️ 3天

**执行代理**: Task 2 + Task 3 + Task 4 **并行**
**依赖**: Wave 2 完成
**阻塞**: Wave 4 对应任务
**状态**: 高并发阶段

#### 目标
并行开发5个核心功能模块，最大化利用开发资源。

#### 任务分配

| 代理 | 负责任务 | 关联需求 | 预计时间 |
|------|----------|----------|----------|
| **Task 2** | 8.4 AI对话 | US-002 | 2天 |
| **Task 3** | 8.5 日历 + 8.6 待办 | US-004, US-005 | 3天 |
| **Task 4** | 8.7 天气 + 8.8 路线规划 | US-003, US-006 | 2天 |

#### 各代理执行计划

##### Task 2 - AI对话功能

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 声明文件锁定（口头告知其他终端）
# "Task 2开始编辑: ChatApi.kt, ChatScreen.kt, chat/包下所有文件"

# 启动 Task 8.4
# [执行 Task 8.4 Prompt]

# 编译检查
./gradlew build -q
if [ $? -eq 0 ]; then
    echo "✅ Task 2 - Wave 3 编译通过"
fi

echo "=== Task 2 - Wave 3 完成 ==="
```

**输出文件**:
- `data/remote/ChatApi.kt`
- `data/repository/ChatRepository.kt`
- `ui/screens/chat/ChatScreen.kt`
- `ui/components/chat/MessageBubble.kt`
- `ui/screens/chat/ChatViewModel.kt`

##### Task 3 - 日历 + 待办

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 声明文件锁定
# "Task 3开始编辑: CalendarApi.kt, TodoApi.kt, calendar/包, todo/包"

# 并行启动 Task 8.5 和 8.6
# [执行 Task 8.5 Prompt]
# [执行 Task 8.6 Prompt]

# 编译检查
./gradlew build -q
if [ $? -eq 0 ]; then
    echo "✅ Task 3 - Wave 3 编译通过"
fi

echo "=== Task 3 - Wave 3 完成 ==="
```

**输出文件**:
- `data/remote/CalendarApi.kt`, `TodoApi.kt`
- `data/repository/CalendarRepository.kt`, `TodoRepository.kt`
- `ui/screens/calendar/CalendarScreen.kt`, `MonthView.kt`
- `ui/screens/todo/TodoScreen.kt`, `TodoItemCard.kt`
- Room Entity: `EventEntity.kt`, `TodoEntity.kt`

##### Task 4 - 天气 + 路线规划

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 声明文件锁定
# "Task 4开始编辑: WeatherApi.kt, RouteApi.kt, weather/包, route/包"

# 并行启动 Task 8.7 和 8.8
# [执行 Task 8.7 Prompt]
# [执行 Task 8.8 Prompt]

# 编译检查
./gradlew build -q
if [ $? -eq 0 ]; then
    echo "✅ Task 4 - Wave 3 编译通过"
fi

echo "=== Task 4 - Wave 3 完成 ==="
```

**输出文件**:
- `data/remote/WeatherApi.kt`, `RouteApi.kt`
- `data/repository/WeatherRepository.kt`
- `ui/screens/weather/WeatherScreen.kt`
- `ui/screens/route/RouteScreen.kt`

#### Wave 3 完成后检查点 ✅

| 代理 | 检查项 | 状态 |
|------|--------|------|
| B | 能发送消息并显示在列表 | [-] |
| B | AI回复正确显示（左对齐，带头像） | [-] |
| B | 下拉刷新加载历史消息 | [-] |
| B | 空消息不发送（输入验证） | [-] |
| C | 月视图正确显示日期 | [-] |
| C | 能创建新事件 | [-] |
| C | 事件显示在对应日期 | [-] |
| C | 能删除事件 | [-] |
| C | 待办列表显示 | [-] |
| C | 能标记完成/未完成 | [-] |
| C | 筛选功能正常 | [-] |
| D | 显示当前天气 | [-] |
| D | 显示7天预报 | [-] |
| D | 定位获取当前城市 | [-] |
| D | 能手动切换城市 | [-] |
| D | 输入起点终点能查询路线 | [-] |
| D | 显示距离、时间、费用 | [-] |
| D | 显示详细步骤 | [-] |

**状态标记**:
- [-] 进行中
- [x] 已完成
- [ ] 待开始
- [!] 有阻塞问题

#### Wave 3 验证

由于B/C/D修改的是不同文件，各自完成后直接验证即可，无需合并：

```bash
# 任意代理执行最终验证
./gradlew build

# 如果编译通过，说明没有冲突
echo "✅ Wave 3 全部完成且编译通过"
```

---

### Wave 4: 高级功能增强 ⏱️ 2天

**执行代理**: Task 2 + Task 3 + Task 4 **并行**
**依赖**: Wave 3 对应任务完成
**阻塞**: Wave 5, Wave 6
**状态**: 高并发阶段

#### 目标
增强功能体验：语音输入、推送通知、为离线模式做准备。

#### 任务分配

| 代理 | 负责任务 | 依赖 | 预计时间 |
|------|----------|------|----------|
| **Task 2** | 8.11 语音输入 | 8.4 (AI对话) | 1天 |
| **Task 3** | 8.10 离线模式DB准备 | 8.5, 8.6 (日历/待办) | 1天 |
| **Task 4** | 8.9 FCM推送集成 | 8.1 (基础) | 1天 |

#### 各代理执行计划

##### Task 2 - 语音输入

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 声明文件锁定
# "Task 2继续编辑: VoiceRecognizer.kt, VoiceInputButton.kt, chat/包"

# 启动 Task 8.11
# [执行 Task 8.11 Prompt]

# 编译检查
./gradlew build -q
```

##### Task 3 - 离线模式（数据同步部分）

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 声明文件锁定
# "Task 3继续编辑: SyncManager.kt, DAO接口, entity/包"

# 启动 Task 8.10（先做数据同步部分）
# [执行 Task 8.10 Prompt - 主要关注 Room DB 和 SyncManager]

# 编译检查
./gradlew build -q
```

##### Task 4 - FCM推送

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 声明文件锁定
# "Task 4开始编辑: FcmService.kt, PushRepository.kt"

# 启动 Task 8.9
# [执行 Task 8.9 Prompt]

# 编译检查
./gradlew build -q
```

#### Wave 4 完成后检查点 ✅

| 代理 | 检查项 | 状态 |
|------|--------|------|
| B | 按住语音按钮可以录音 | [-] |
| B | 语音正确转为文字 | [-] |
| B | 文字自动发送给 AI | [-] |
| B | 录音动画效果正常 | [-] |
| C | Room DAO 接口完整 | [-] |
| C | SyncManager 监听网络状态 | [-] |
| C | WorkManager 配置完成 | [-] |
| D | Firebase 项目配置完成 | [-] |
| D | 应用能获取 FCM Token | [-] |
| D | Token 上报后端成功 | [-] |
| D | 能接收并显示推送通知 | [-] |
| D | 点击通知打开应用 | [-] |

**同步点**: Wave 4 完成后，Task 3 继续 Wave 5 离线模式完善，Task 5 准备打包配置

---

### Wave 5: 离线模式完善与打包准备 ⏱️ 2天

**执行代理**: Task 3 + Task 5
**依赖**: Wave 4 完成
**阻塞**: Wave 6
**状态**: 收尾准备

#### 目标
完成离线模式的 Repository 层改造，配置应用打包。

#### 任务分配

| 代理 | 负责任务 | 依赖 | 预计时间 |
|------|----------|------|----------|
| **Task 3** | 8.10 离线模式完善 | Wave 4 的 8.10 | 1天 |
| **Task 5** | 8.12 打包配置（部分） | Wave 4 合并完成 | 1天 |

#### 执行计划

##### Task 3 - 离线模式完善

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 声明文件锁定
# "Task 3继续编辑: CalendarRepository, TodoRepository（添加离线支持）"

# 完善 Task 8.10
# - 完成离线支持的 Repository
# - WorkManager 后台同步
# - UI 离线提示

# 编译检查
./gradlew build -q
```

##### Task 5 - 打包配置启动

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 声明文件锁定
# "Task 5开始编辑: build.gradle.kts(签名配置), proguard-rules.pro"

# 启动 Task 8.12（先做配置部分）
# - 签名配置
# - ProGuard 规则
# - 版本管理

# 编译检查
./gradlew build -q
```

#### Wave 5 完成后检查点 ✅

| 代理 | 检查项 | 状态 |
|------|--------|------|
| C | 离线时能查看已缓存数据 | [-] |
| C | 网络恢复自动同步 | [-] |
| C | 后台定期同步运行（WorkManager） | [-] |
| C | 离线时显示提示条 | [-] |
| E | 签名密钥配置完成 | [-] |
| E | ProGuard 规则编写完成 | [-] |
| E | Release 构建配置完成 | [-] |
| E | versionCode 自动递增配置 | [-] |

---

### Wave 6: 集成测试与发布 ⏱️ 1-2天

**执行代理**: Task 5
**依赖**: Wave 5 完成，所有功能合并
**状态**: 最终发布

#### 目标
完成最终集成测试，生成发布包。

#### 任务清单

| 任务ID | 任务名称 | 输出 | 验收标准 |
|--------|----------|------|----------|
| 8.12 | Android 应用打包与发布配置（完成） | Release APK/AAB | `./gradlew assembleRelease` 成功 |
| - | 集成测试 | 测试报告 | 核心流程通过 |

#### Task 5 执行计划

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

echo "=== 开始执行 Wave 6 - 集成测试与发布 ==="

# 完成 Task 8.12
# [执行 Task 8.12 Prompt - 完成所有配置]

# 构建发布包
./gradlew assembleRelease

# 验证 APK
# - 安装到设备
# - 登录测试
# - 各功能模块测试
# - 离线模式测试

echo "=== Wave 6 完成 ==="
```

#### Wave 6 完成后检查点 ✅

| 检查项 | 状态 |
|--------|------|
| `./gradlew assembleRelease` 构建成功 | [-] |
| APK 已签名且能正常安装 | [-] |
| 混淆后功能正常 | [-] |
| 应用体积优化（APK < 50MB） | [-] |
| 登录流程正常 | [-] |
| AI对话正常 | [-] |
| 日历/待办 CRUD 正常 | [-] |
| 天气查询正常 | [-] |
| 路线规划正常 | [-] |
| 语音输入正常 | [-] |
| 推送通知正常 | [-] |
| 离线模式正常 | [-] |

#### 最终验收指标

| 指标 | 目标值 | 实际值 | 状态 |
|------|--------|--------|------|
| 编译通过 | 100% | [-] | 待验证 |
| 功能完整性 | 12/12任务 | [-] | 待验证 |
| Release APK大小 | < 50MB | [-] | 待验证 |
| 启动时间 | < 3秒 | [-] | 待验证 |
| 离线数据缓存 | 日历/待办/消息 | [-] | 待验证 |

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              表示层 (UI Layer)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ LoginScreen  │  │  ChatScreen  │  │CalendarScreen│  │  TodoScreen  │   │
│  │  (Wave 2)    │  │ (Wave 3/4)   │  │ (Wave 3/5)   │  │ (Wave 3/5)   │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                     │
│  │WeatherScreen │  │ RouteScreen  │  │ MainScreen   │                     │
│  │  (Wave 3)    │  │  (Wave 3)    │  │  (Wave 2)    │                     │
│  └──────────────┘  └──────────────┘  └──────────────┘                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                              业务层 (Domain Layer)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │AuthRepository│  │ChatRepository│  │CalendarRepo  │  │ TodoRepository│   │
│  │  (Wave 2)    │  │ (Wave 3)     │  │ (Wave 3/5)   │  │ (Wave 3/5)   │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                     │
│  │WeatherRepo   │  │ RouteRepo    │  │SyncManager   │                     │
│  │  (Wave 3)    │  │  (Wave 3)    │  │ (Wave 4/5)   │                     │
│  └──────────────┘  └──────────────┘  └──────────────┘                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                              数据层 (Data Layer)                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────┐  ┌─────────────────────────────────┐   │
│  │      Remote (Retrofit)          │  │       Local (Room)              │   │
│  │  ┌─────────┐    ┌─────────┐    │  │  ┌─────────┐    ┌─────────┐    │   │
│  │  │ AuthApi │    │ ChatApi │    │  │  │ EventDao│    │ TodoDao │    │   │
│  │  └─────────┘    └─────────┘    │  │  └─────────┘    └─────────┘    │   │
│  │  ┌─────────┐    ┌─────────┐    │  │  ┌─────────┐    ┌─────────┐    │   │
│  │  │WeatherApi    │ RouteApi│    │  │  │MessageDao    │SyncWorker     │   │
│  │  └─────────┘    └─────────┘    │  │  └─────────┘    └─────────┘    │   │
│  └─────────────────────────────────┘  └─────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────────┤
│                              服务层 (Service Layer)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                      │
│  │ FcmService   │  │VoiceRecognize│  │ WorkManager │                      │
│  │  (Wave 4)    │  │  (Wave 4)    │  │  (Wave 5)   │                      │
│  └──────────────┘  └──────────────┘  └──────────────┘                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📁 关键文件清单

### Wave 1-2: 基础架构

```
mrshudson-android/
├── build.gradle.kts (Project)              # Wave 1
├── app/build.gradle.kts (Module)           # Wave 1
├── gradle/libs.versions.toml               # Wave 1
├── app/src/main/java/com/mrshudson/android/
│   ├── MainActivity.kt                     # Wave 1
│   ├── MrsHudsonApplication.kt             # Wave 1
│   ├── di/
│   │   ├── NetworkModule.kt                # Wave 1
│   │   ├── DatabaseModule.kt               # Wave 1
│   │   └── RepositoryModule.kt             # Wave 1-2
│   ├── data/
│   │   ├── remote/
│   │   │   ├── AuthApi.kt                  # Wave 2
│   │   │   └── ... (其他API Wave 3添加)
│   │   ├── repository/
│   │   │   ├── AuthRepository.kt           # Wave 2
│   │   │   └── ... (其他Repository Wave 3添加)
│   │   └── local/
│   │       ├── datastore/
│   │       │   └── TokenDataStore.kt       # Wave 2
│   │       └── database/
│   │           └── MrsHudsonDatabase.kt    # Wave 1
│   └── ui/
│       ├── theme/                          # Wave 1
│       ├── screens/
│       │   ├── login/LoginScreen.kt        # Wave 2
│       │   └── main/MainScreen.kt          # Wave 2
│       └── components/
```

### Wave 3-5: 功能模块

```
mrshudson-android/app/src/main/java/com/mrshudson/android/
├── data/remote/
│   ├── ChatApi.kt                          # Wave 3 - Task 2
│   ├── CalendarApi.kt                      # Wave 3 - Task 3
│   ├── TodoApi.kt                          # Wave 3 - Task 3
│   ├── WeatherApi.kt                       # Wave 3 - Task 4
│   └── RouteApi.kt                         # Wave 3 - Task 4
├── data/repository/
│   ├── ChatRepository.kt                   # Wave 3 - Task 2
│   ├── CalendarRepository.kt               # Wave 3-5 - Task 3
│   └── ...
├── data/local/entity/
│   ├── EventEntity.kt                      # Wave 3 - Task 3
│   ├── TodoEntity.kt                       # Wave 3 - Task 3
│   └── MessageEntity.kt                    # Wave 5 - Task 3
├── data/local/dao/
│   ├── EventDao.kt                         # Wave 5 - Task 3
│   └── TodoDao.kt                          # Wave 5 - Task 3
├── data/sync/
│   └── SyncManager.kt                      # Wave 4-5 - Task 3
├── worker/
│   └── SyncWorker.kt                       # Wave 5 - Task 3
├── service/
│   └── FcmService.kt                       # Wave 4 - Task 4
├── utils/
│   └── VoiceRecognizer.kt                  # Wave 4 - Task 2
└── ui/screens/
    ├── chat/ChatScreen.kt                  # Wave 3 - Task 2
    ├── calendar/CalendarScreen.kt          # Wave 3 - Task 3
    ├── todo/TodoScreen.kt                  # Wave 3 - Task 3
    ├── weather/WeatherScreen.kt            # Wave 3 - Task 4
    └── route/RouteScreen.kt                # Wave 3 - Task 4
```

---

## 🎯 完整 Prompt 汇总

### Wave 1 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 初始化 MrsHudson Android 项目 (Wave 1)

Context:
- 项目根目录: /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson
- 需要创建 mrshudson-android 子项目
- 技术栈: Kotlin + Jetpack Compose + MVVM

Requirements:
1. 创建 Android 项目结构：
   - minSdk: 26 (Android 8.0)
   - targetSdk: 34
   - compileSdk: 34

2. 配置核心依赖（libs.versions.toml）：
   - compose-bom: 2024.02.00
   - hilt: 2.50
   - retrofit: 2.9.0
   - room: 2.6.1
   - datastore: 1.0.0
   - navigation-compose: 2.7.7
   - material3: 1.2.0

3. 创建基础架构：
   - MainActivity（Compose入口）
   - MrsHudsonApplication（Application类）
   - di/模块：NetworkModule, DatabaseModule, RepositoryModule
   - data/包：repository, local, remote
   - ui/包：theme, components, screens, viewmodel

4. 配置网络层：
   - Retrofit 配置（baseUrl: http://10.0.2.2:8080/api/）
   - OkHttp 添加 JWT Token 拦截器
   - 统一的 Result 封装类

5. 配置本地存储：
   - Room 数据库基础配置
   - DataStore 用于 Token 存储

Restrictions:
- 使用 Kotlin DSL 而非 Groovy
- 包名统一为 com.mrshudson.android
- 遵循官方架构指南

Success Criteria:
- ./gradlew build 成功
- 应用能在模拟器启动（显示空白主页面）
- 项目结构符合上述要求
```

### Wave 2 - Task 8.2 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端用户认证模块 (Wave 2)

Context:
- 后端已提供 JWT 认证 API
- 需要存储 Token 实现自动登录
- 使用后端相同的 API: POST /api/auth/login

Requirements:
1. 创建数据模型：
   - LoginRequest(username, password)
   - LoginResponse(accessToken, refreshToken)
   - User(id, username)

2. 创建 AuthApi 接口：
   - @POST("auth/login") suspend fun login(...): Response<LoginResponse>
   - @POST("auth/logout") suspend fun logout()
   - @GET("auth/me") suspend fun getCurrentUser(): Response<User>

3. 创建 TokenManager：
   - 使用 DataStore 存储 accessToken 和 refreshToken
   - 提供 saveTokens(), getAccessToken(), clearTokens()

4. 创建 AuthRepository：
   - login(username, password): Result<User>
   - logout()
   - isLoggedIn(): Boolean
   - getCurrentUser(): Flow<User?>

5. 创建登录页面：
   - 用户名输入框（默认admin）
   - 密码输入框（默认admin，隐藏显示）
   - 登录按钮（带Loading状态）
   - 登录失败提示
   - 登录成功后跳转到主页面

6. 自动登录逻辑：
   - 启动时检查是否有有效Token
   - 有Token直接跳主页面
   - 无Token或Token过期显示登录页

Restrictions:
- 使用 Material3 组件
- 输入框使用 OutlinedTextField
- 使用 Hilt 注入依赖

Success Criteria:
- 输入 admin/admin 能登录成功
- Token 正确保存到 DataStore
- 重启应用自动登录
- 登录错误显示友好提示
```

### Wave 2 - Task 8.3 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 主页面框架与底部导航 (Wave 2)

Requirements:
1. 创建 BottomNavItem 枚举：
   - CHAT("对话", Icons.Default.Chat)
   - CALENDAR("日历", Icons.Default.CalendarToday)
   - TODO("待办", Icons.Default.CheckCircle)
   - WEATHER("天气", Icons.Default.WbSunny)
   - ROUTE("路线", Icons.Default.Map)

2. 创建 MainScreen：
   - Scaffold 布局
   - 底部 NavigationBar
   - 中间内容区域（NavHost）

3. 创建占位页面：
   - ChatScreen（空白，Wave 3实现）
   - CalendarScreen（空白，Wave 3实现）
   - TodoScreen（空白，Wave 3实现）
   - WeatherScreen（空白，Wave 3实现）
   - RouteScreen（空白，Wave 3实现）

4. 实现导航状态保持：
   - 切换底部导航时保持页面状态
   - 使用 rememberSaveable 或 ViewModel

5. 设计主题：
   - 定义 MrsHudson 品牌色（暖色调）
   - 配置 Material3 主题
   - 支持亮色/暗色模式

6. 顶部标题栏：
   - 显示 "MrsHudson"
   - 右侧设置/退出按钮

Success Criteria:
- 底部5个导航标签可点击切换
- 切换时内容区域变化
- 主题色符合品牌调性
- 页面状态切换时保持
```

### Wave 3 - Task 2 - Task 8.4 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端 AI 对话功能 (Wave 3 - Task 2)

Context:
- 后端 API: POST /api/chat/send, GET /api/chat/history
- 消息包含 role: user/assistant/system
- 需要支持流式响应（可选）

Requirements:
1. 创建数据模型：
   - Message(id, role, content, createdAt)
   - SendMessageRequest(message, conversationId)

2. 创建 ChatApi：
   - POST /api/chat/send
   - GET /api/chat/history?limit=50

3. 创建 ChatRepository：
   - sendMessage(content): Result<Message>
   - getHistory(): Flow<List<Message>>

4. 创建 MessageBubble 组件：
   - 用户消息：右对齐，蓝色背景
   - AI 消息：左对齐，带 MrsHudson 头像
   - 显示发送时间
   - 支持 Markdown 文本渲染

5. 创建 ChatScreen：
   - 顶部：标题栏（显示"哈德森夫人"）
   - 中部：消息列表（LazyColumn，倒序）
   - 底部：输入框 + 发送按钮
   - 下拉刷新加载历史

6. 实现 ChatViewModel：
   - 管理消息列表状态
   - 发送消息逻辑
   - 加载历史消息
   - 处理加载状态

Success Criteria:
- 能发送消息并显示在列表
- AI回复正确显示（左对齐）
- 下拉刷新加载历史消息
- 空消息不发送
```

### Wave 3 - Task 3 - Task 8.5 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端日历功能 (Wave 3 - Task 3)

Context:
- 后端 API: /api/calendar/events
- 需要支持日/周/月视图（先实现月视图）

Requirements:
1. 创建数据模型：
   - CalendarEvent(id, title, description, startTime, endTime, location, category)
   - CreateEventRequest, UpdateEventRequest

2. 创建 CalendarApi：
   - GET /api/calendar/events?start_date=&end_date=
   - POST /api/calendar/events
   - PUT /api/calendar/events/{id}
   - DELETE /api/calendar/events/{id}

3. 创建 CalendarRepository：
   - getEvents(startDate, endDate): Flow<List<CalendarEvent>>
   - createEvent(event): Result<CalendarEvent>
   - updateEvent(id, event): Result<CalendarEvent>
   - deleteEvent(id): Result<Unit>

4. 创建 MonthView 组件：
   - 显示月历网格
   - 每天格子显示事件指示点
   - 支持月份切换
   - 点击日期选中

5. 创建 CalendarScreen：
   - 顶部：月份标题 + 左右切换按钮
   - 中部：MonthView
   - 底部：选中日期的事件列表
   - FAB 按钮添加事件

6. 创建 EventDialog：
   - 创建/编辑事件
   - 标题、时间、分类输入
   - 保存/取消按钮

Success Criteria:
- 月视图正确显示日期
- 能创建新事件
- 事件显示在对应日期
- 能删除事件
```

### Wave 3 - Task 3 - Task 8.6 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端待办事项功能 (Wave 3 - Task 3)

Requirements:
1. 创建数据模型：
   - TodoItem(id, title, description, priority, status, dueDate, completedAt)
   - Priority: LOW, MEDIUM, HIGH
   - Status: PENDING, COMPLETED

2. 创建 TodoApi：
   - GET /api/todos?status=&priority=
   - POST /api/todos
   - PUT /api/todos/{id}/complete
   - DELETE /api/todos/{id}

3. 创建 TodoRepository：
   - getTodos(filter): Flow<List<TodoItem>>
   - createTodo(todo): Result<TodoItem>
   - completeTodo(id): Result<Unit>
   - deleteTodo(id): Result<Unit>

4. 创建 TodoItemCard 组件：
   - 复选框标记完成
   - 标题（完成的有删除线）
   - 优先级标签（颜色区分）
   - 截止日期显示
   - 左滑删除

5. 创建 TodoScreen：
   - 顶部：筛选标签（全部/进行中/已完成）
   - 中部：待办列表（LazyColumn）
   - 底部：输入框快速添加
   - FAB 添加详细待办

6. 创建 AddTodoDialog：
   - 标题、描述、优先级、截止日期
   - 保存/取消

Success Criteria:
- 显示待办列表
- 能标记完成/未完成
- 能添加新待办
- 筛选功能正常
```

### Wave 3 - Task 4 - Task 8.7 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端天气查询功能 (Wave 3 - Task 4)

Context:
- 天气数据通过后端 API 获取（后端调用高德天气 API）
- 需要 Android 定位权限获取当前城市

Requirements:
1. 创建数据模型：
   - WeatherInfo(city, temperature, weather, humidity, windDirection, windPower)
   - WeatherForecast(date, dayWeather, nightWeather, dayTemp, nightTemp)

2. 创建 WeatherApi：
   - GET /api/weather/current?city=
   - GET /api/weather/forecast?city=&days=

3. 创建 WeatherRepository：
   - getCurrentWeather(city): Result<WeatherInfo>
   - getForecast(city, days): Result<List<WeatherForecast>>

4. 创建 WeatherScreen：
   - 顶部：当前城市（可搜索切换）
   - 中部：当前天气大卡片
     - 温度大字显示
     - 天气图标
     - 湿度、风向等信息
   - 底部：未来7天预报列表

5. 实现定位功能：
   - 申请定位权限
   - 使用 FusedLocationProviderClient
   - 根据坐标反编码获取城市名
   - 定位失败时默认显示北京

6. 创建 WeatherViewModel：
   - 当前城市状态
   - 天气数据状态
   - 刷新逻辑

Success Criteria:
- 显示当前天气
- 显示7天预报
- 定位获取当前城市
- 能手动切换城市
```

### Wave 3 - Task 4 - Task 8.8 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端路线规划功能 (Wave 3 - Task 4)

Requirements:
1. 创建数据模型：
   - RouteRequest(origin, destination, mode)
   - RouteInfo(distance, duration, toll, steps)
   - TravelMode: WALK, DRIVE, BUS

2. 创建 RouteApi：
   - POST /api/route/plan

3. 创建 RouteScreen：
   - 起点输入框（支持当前位置）
   - 终点输入框
   - 出行方式选择（步行/驾车/公交）
   - 查询按钮
   - 路线结果显示区域

4. 实现功能：
   - 使用定位获取当前位置作为起点
   - 调用后端 API 获取路线
   - 显示距离、时间、费用
   - 显示详细步骤列表

5. 创建 RouteViewModel：
   - 输入状态管理
   - 路线查询逻辑
   - 加载和错误状态

Success Criteria:
- 输入起点终点能查询路线
- 显示距离、时间、费用
- 显示详细步骤
- 切换出行方式重新查询
```

### Wave 4 - Task 2 - Task 8.11 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端语音输入功能 (Wave 4 - Task 2)

Requirements:
1. 权限配置：
   - AndroidManifest.xml 添加 RECORD_AUDIO 权限
   - 动态申请麦克风权限

2. 创建 VoiceRecognizer：
   - 使用 Android SpeechRecognizer API
   - 开始录音、停止录音
   - 回调识别结果

3. 创建 VoiceInputButton 组件：
   - 麦克风图标按钮
   - 按住录音，松开发送
   - 录音时显示动画效果
   - 显示录音时长

4. 集成到 ChatScreen：
   - 输入框旁边添加语音按钮
   - 语音输入转成文字后发送
   - 显示识别中的加载状态

5. 错误处理：
   - 权限被拒绝提示
   - 识别失败重试
   - 网络错误处理

Restrictions:
- 使用系统 SpeechRecognizer（无需第三方SDK）
- 仅支持中文语音识别
- 录音时长限制60秒

Success Criteria:
- 按住按钮可以录音
- 语音正确转为文字
- 文字自动发送给 AI
```

### Wave 4 - Task 3 - Task 8.10 Prompt (阶段1)

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现离线模式与数据同步 - 阶段1 (Wave 4 - Task 3)

Context:
- 用户希望离线时也能查看数据
- 网络恢复时自动同步

Requirements:
1. Room 数据库设计：
   - MessageEntity（缓存最近100条对话）
   - EventEntity（缓存日历事件）
   - TodoEntity（缓存待办事项）

2. 创建 Dao 接口：
   - MessageDao: insert, getAll, deleteAll
   - EventDao: insert, getByDateRange, delete, getAll
   - TodoDao: insert, getAll, update, delete

3. 创建 SyncManager：
   - 监听网络状态变化
   - 网络恢复时触发同步
   - 显示同步状态

4. 创建 SyncWorker（WorkManager）：
   - 定期后台同步（每6小时）
   - 电池优化考虑
   - 只在WiFi下同步大数据

Success Criteria:
- Room 数据库配置完成
- DAO 接口可用
- SyncManager 监听网络状态
```

### Wave 4 - Task 4 - Task 8.9 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 集成 FCM 推送通知 (Wave 4 - Task 4)

Context:
- 后端需要 deviceToken 才能发送推送
- 推送场景：日程提醒、待办提醒

Requirements:
1. Firebase 配置：
   - 创建 Firebase 项目
   - 下载 google-services.json
   - 应用 build.gradle 添加 FCM 插件和依赖

2. 创建 FcmService：
   - 继承 FirebaseMessagingService
   - onMessageReceived：处理接收到的消息
   - onNewToken：Token 刷新时上报

3. 创建通知显示：
   - 创建 NotificationChannel（Android 8.0+）
   - 显示通知标题和内容
   - 点击通知打开 MainActivity

4. 创建 PushRepository：
   - 上报 Device Token 到后端
   - POST /api/push/register {deviceToken, platform: "android"}

5. Token 管理：
   - 登录后上报 Token
   - Token 刷新时重新上报
   - 登出时注销 Token

6. 权限配置：
   - AndroidManifest.xml 添加服务声明
   - 申请 POST_NOTIFICATIONS 权限（Android 13+）

Success Criteria:
- 应用能获取 FCM Token
- Token 成功上报后端
- 能接收并显示推送通知
- 点击通知打开应用
```

### Wave 5 - Task 3 - Task 8.10 Prompt (阶段2)

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现离线模式与数据同步 - 阶段2 (Wave 5 - Task 3)

Context:
- Wave 4 已完成 Room DB 和 SyncManager
- 现在需要完善 Repository 层的离线支持

Requirements:
1. 实现离线支持 Repository：
   - CalendarRepository: 优先从本地读取，网络可用时刷新
   - TodoRepository: 优先从本地读取，网络可用时刷新
   - 新数据保存到本地

2. UI 离线提示：
   - 离线时显示提示条
   - 同步时显示进度
   - 同步完成提示

3. 完善 SyncWorker：
   - 后台定期同步逻辑
   - 冲突处理策略（服务器优先）

Success Criteria:
- 离线时能查看已缓存数据
- 网络恢复自动同步
- 后台定期同步运行
```

### Wave 5-6 - Task 5 - Task 8.12 Prompt

```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 配置 Android 应用发布打包 (Wave 5-6 - Task 5)

Requirements:
1. 签名配置：
   - 创建 release.keystore
   - build.gradle.kts 配置 signingConfigs
   - 本地属性文件管理密钥密码

2. ProGuard 配置：
   - 基础混淆规则
   - Retrofit/OkHttp 保留规则
   - Room 实体保留规则
   - Compose 保留规则

3. 构建配置：
   - 开启 minifyEnabled（混淆）
   - 开启 shrinkResources（资源压缩）
   - 配置多架构支持（arm64-v8a, armeabi-v7a）

4. 版本管理：
   - 配置 versionCode 自动递增
   - versionName 语义化版本

5. 输出配置：
   - APK 输出配置
   - AAB (Android App Bundle) 配置

6. 渠道配置（可选）：
   - 友盟/极光等多渠道打包支持

Success Criteria:
- ./gradlew assembleRelease 成功
- APK 已签名且能正常安装
- 混淆后功能正常
- 应用体积优化
```

---

## 🚀 Task 代理快速启动指南

### Task 1（基础架构）

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# Wave 1
# [执行 Wave 1 Prompt]
./gradlew build

# Wave 2
# [执行 Wave 2 Prompts]
./gradlew build

echo "=== Task 1 完成，通知 Task 2/C/D ==="
```

### Task 2（AI对话+语音）

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 等待 A 完成

# Wave 3
# 声明: "Task 2编辑chat/包"
# [执行 Wave 3 - Task 8.4 Prompt]
./gradlew build

# Wave 4
# 声明: "Task 2继续编辑chat/包"
# [执行 Wave 4 - Task 8.11 Prompt]
./gradlew build
```

### Task 3（日历+待办+离线）

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 等待 A 完成

# Wave 3
# 声明: "Task 3编辑calendar/和todo/包"
# [执行 Wave 3 - Task 8.5, 8.6 Prompts]
./gradlew build

# Wave 4
# 声明: "Task 3编辑sync/和dao/包"
# [执行 Wave 4 - Task 8.10 Prompt 阶段1]
./gradlew build

# Wave 5
# 声明: "Task 3编辑repository/包（离线支持）"
# [执行 Wave 5 - Task 8.10 Prompt 阶段2]
./gradlew build
```

### Task 4（天气+路线+推送）

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 等待 A 完成

# Wave 3
# 声明: "Task 4编辑weather/和route/包"
# [执行 Wave 3 - Task 8.7, 8.8 Prompts]
./gradlew build

# Wave 4
# 声明: "Task 4编辑service/包（FCM）"
# [执行 Wave 4 - Task 8.9 Prompt]
./gradlew build
```

### Task 5（打包发布）

```bash
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-android

# 等待 Wave 5 完成

# Wave 5-6
# 声明: "Task 5编辑build.gradle.kts和proguard-rules.pro"
# [执行 Wave 5-6 - Task 8.12 Prompt]
./gradlew assembleRelease

# 验证 APK
```

---

## ⚠️ 冲突预防指南

### 文件级冲突预防

| 文件 | 使用Wave | 建议处理方式 |
|------|----------|--------------|
| `build.gradle.kts` | 所有Wave | Wave 1 初始化，其他Wave只添加依赖 |
| `AndroidManifest.xml` | Wave 1,4 | Wave 1 创建基础，Wave 4 添加权限/服务 |
| `MainActivity.kt` | Wave 1 | Wave 1 完成，其他Wave不修改 |
| `MainScreen.kt` | Wave 2 | Wave 2 创建框架，其他Wave只实现内容 |
| `Theme.kt` / `Color.kt` | Wave 1-2 | Wave 2 定义，其他Wave只使用 |
| `NavHost` 路由表 | Wave 2 | Wave 2 定义路由，其他Wave添加页面 |

### 包结构约定

```
com.mrshudson.android
├── data
│   ├── local          # Wave 3-5 Task 3 使用
│   │   ├── dao        # Wave 4-5
│   │   ├── entity     # Wave 3-4
│   │   └── datastore  # Wave 2
│   ├── remote         # Wave 2-3 各Terminal使用
│   │   ├── AuthApi.kt         # Wave 2
│   │   ├── ChatApi.kt         # Wave 3 B
│   │   ├── CalendarApi.kt     # Wave 3 C
│   │   ├── TodoApi.kt         # Wave 3 C
│   │   ├── WeatherApi.kt      # Wave 3 D
│   │   └── RouteApi.kt        # Wave 3 D
│   └── repository     # Wave 2-5 各Terminal使用
├── di                 # Wave 1-2
├── service            # Wave 4 D 使用
├── sync               # Wave 4-5 C 使用
├── worker             # Wave 5 C 使用
├── utils              # Wave 4 B 使用
└── ui
    ├── components     # 各Wave使用
    ├── screens
    │   ├── login      # Wave 2
    │   ├── main       # Wave 2
    │   ├── chat       # Wave 3-4 B
    │   ├── calendar   # Wave 3-5 C
    │   ├── todo       # Wave 3-5 C
    │   ├── weather    # Wave 3 D
    │   └── route      # Wave 3 D
    ├── theme          # Wave 1-2
    └── viewmodel      # 各Wave使用
```

---

## 📝 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-03-05 | 初始版本，按终端分组规划 |
| v2.0 | 2026-03-05 | 参考 ai-cost-optimization 重构为 Wave 规划 |
| v2.1 | 2026-03-05 | 参考 PARALLEL_EXECUTION.md 增加团队规模建议、检查点、进度跟踪 |
| v3.0 | 2026-03-05 | 移除所有Git操作和协作流程，简化为并行Task代理执行方案 |
| v4.0 | 2026-03-06 | 将"本地多终端"改为"多个并行的Task代理模拟"执行 |
| v5.0 | 2026-03-06 | **优化并行度**：最大化Wave内并行执行，添加快速启动命令，预计工期缩短至5-7天 |

---

**文档版本**: v5.0
**最后更新**: 2026-03-06
**适用规格**: mrshudson-core
**参考文档**:
- ai-cost-optimization/IMPLEMENTATION_SUMMARY.md
- ai-cost-optimization/PARALLEL_EXECUTION.md
