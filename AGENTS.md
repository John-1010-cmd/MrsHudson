# MrsHudson AI管家 - 项目指南

## 项目概述

**MrsHudson（哈德森夫人）** 是一款AI管家助手应用，灵感源自《福尔摩斯探案集》中的房东哈德森太太。项目采用前后端分离架构，通过自然语言对话帮助用户管理日常事务，包括天气查询、日程管理、待办事项、智能提醒等功能。

### 核心价值

> "让每个现代人都能拥有自己的私人管家"

### 技术架构概览

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Vue 3 前端    │────▶│  Spring Boot    │────▶│   MySQL 8.0     │
│  (Port 3000)    │     │   后端服务      │     │   主数据存储    │
│  Element Plus   │◀────│  (Port 8080)    │◀────│                 │
│    Pinia        │     │                 │     │   Redis 7.0     │
└─────────────────┘     └─────────────────┘     │   缓存/会话     │
                                                └─────────────────┘
                                                         │
                                                ┌─────────────────┐
                                                │  Kimi AI API    │
                                                │  月之暗面       │
                                                └─────────────────┘
```

---

## 技术栈详情

### 后端 (mrshudson-backend/)

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.2.3 | 主框架 |
| **MyBatis Plus** | 3.5.5 | **ORM 数据访问 (已从 JPA 迁移)** |
| Spring Security | - | 认证授权 |
| Spring WebSocket | - | 实时通信 |
| MySQL Connector | 8.x | 数据库驱动 |
| ~~Flyway~~ | ~~-~~ | ~~(已移除)~~ |
| Redis | - | 缓存与会话 |
| Lombok | - | 代码简化 |
| FastJSON2 | 2.0.46 | JSON 处理 |

### 前端 (mrshudson-frontend/)

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4.21 | 前端框架 |
| TypeScript | 5.3.3 | 类型系统 |
| Vite | 5.1.5 | 构建工具 |
| Element Plus | 2.5.6 | UI 组件库 |
| Pinia | 2.1.7 | 状态管理 |
| Vue Router | 4.3.0 | 路由管理 |
| Axios | 1.6.7 | HTTP 客户端 |
| Element Plus Icons | 2.3.2 | 图标库 |

---

## 项目结构

### 后端目录结构

```
mrshudson-backend/
├── pom.xml                           # Maven 构建配置
├── Dockerfile                        # 后端容器构建文件
└── src/
    ├── main/
    │   ├── java/com/mrshudson/
    │   │   ├── MrshudsonApplication.java    # 应用入口 (@MapperScan 已添加)
    │   │   ├── config/                      # 配置类
    │   │   │   ├── SecurityConfig.java      # Spring Security 配置
    │   │   │   ├── WebConfig.java           # Web 配置 (CORS 允许 3000 端口)
    │   │   │   ├── ~~JpaConfig.java~~       # ~~(已移除)~~
    │   │   │   ├── mybatis/                 # MyBatis Plus 配置
    │   │   │   │   └── MyMetaObjectHandler.java  # 自动填充时间戳
    │   │   │   ├── VoiceProperties.java     # 语音服务配置
    │   │   │   └── WeatherProperties.java   # 天气 API 配置
    │   │   ├── controller/                  # REST API 控制器
    │   │   │   ├── AuthController.java      # 认证接口
    │   │   │   ├── ChatController.java      # AI 对话接口
    │   │   │   ├── CalendarController.java  # 日历管理接口
    │   │   │   ├── TodoController.java      # 待办事项接口
    │   │   │   ├── WeatherController.java   # 天气查询接口
    │   │   │   └── ReminderController.java  # 提醒管理接口
    │   │   ├── service/                     # 业务逻辑层
    │   │   │   ├── AuthService.java
    │   │   │   ├── ChatService.java
    │   │   │   ├── CalendarService.java
    │   │   │   ├── TodoService.java
    │   │   │   ├── WeatherService.java
    │   │   │   ├── VoiceService.java
    │   │   │   ├── ReminderService.java
    │   │   │   └── impl/                    # 服务实现类 (已改为 MyBatis Plus)
    │   │   ├── domain/                      # 领域模型
    │   │   │   ├── entity/                  # 实体类 (已改为 MyBatis Plus 注解)
    │   │   │   │   ├── User.java
    │   │   │   │   ├── ChatMessage.java
    │   │   │   │   ├── CalendarEvent.java
    │   │   │   │   ├── TodoItem.java
    │   │   │   │   └── Reminder.java
    │   │   │   └── dto/                     # 数据传输对象
    │   │   ├── ~~repository/~~              # ~~(已移除，改为 mapper)~~
    │   │   ├── mapper/                      # MyBatis Plus Mapper 接口
    │   │   │   ├── UserMapper.java
    │   │   │   ├── ChatMessageMapper.java
    │   │   │   ├── CalendarEventMapper.java
    │   │   │   ├── TodoItemMapper.java
    │   │   │   └── ReminderMapper.java
    │   │   ├── mcp/                         # MCP (Model Context Protocol) 工具层
    │   │   │   ├── BaseTool.java
    │   │   │   ├── ToolRegistry.java
    │   │   │   ├── kimi/                    # Kimi AI 客户端
    │   │   │   │   ├── KimiClient.java
    │   │   │   │   ├── KimiProperties.java
    │   │   │   │   └── dto/
    │   │   │   ├── weather/WeatherTool.java
    │   │   │   ├── calendar/CalendarTool.java
    │   │   │   └── todo/TodoTool.java
    │   │   └── job/                         # 定时任务
    │   │       └── ReminderJob.java
    │   └── resources/
    │       ├── application.yml              # 应用配置 (已更新为 MyBatis Plus)
    │       └── db/migration/                # 数据库初始化脚本
    │           └── V1__init_schema.sql
    └── test/                                # 测试代码 (当前为空)
```

### 前端目录结构

```
mrshudson-frontend/
├── package.json               # npm 依赖配置
├── vite.config.ts             # Vite 构建配置
├── tsconfig.json              # TypeScript 配置
├── Dockerfile                 # 前端容器构建文件
├── nginx.conf                 # Nginx 配置 (生产环境)
└── src/
    ├── main.ts                # 应用入口
    ├── App.vue                # 根组件
    ├── router/
    │   └── index.ts           # 路由配置
    ├── stores/
    │   ├── index.ts           # Pinia 配置
    │   └── user.ts            # 用户状态管理
    ├── api/                   # API 封装
    │   ├── axios.ts           # Axios 实例配置 (⚠️ 已改为直接访问 8080)
    │   ├── auth.ts            # 认证接口
    │   ├── chat.ts            # 对话接口
    │   ├── calendar.ts        # 日历接口
    │   └── todo.ts            # 待办接口
    ├── views/                 # 页面组件
    │   ├── LoginView.vue      # 登录页
    │   ├── ChatView.vue       # 主布局 (含侧边栏)
    │   ├── ChatRoom.vue       # 对话界面
    │   ├── CalendarView.vue   # 日历视图
    │   └── TodoView.vue       # 待办列表
    └── components/            # 可复用组件
        ├── EventDialog.vue
        ├── TodoItem.vue
        └── VoiceInputButton.vue
```

---

## 构建与运行

### 环境要求

- Docker & Docker Compose
- JDK 17+ (本地开发)
- Node.js 18+ (本地开发)
- Maven 3.8+ (本地开发)

### 开发环境启动

**基础设施 (MySQL + Redis):**
```bash
# 使用 Docker Compose 启动数据库和缓存
docker-compose up -d mysql redis

# 服务启动后:
#    - MySQL: localhost:3306
#    - Redis: localhost:6379
```

**后端:**
```bash
cd mrshudson-backend

# 编译
mvn clean compile

# 运行
mvn spring-boot:run

# 后端 API: http://localhost:8080
```

**前端:**
```bash
cd mrshudson-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 前端: http://localhost:3000
```

---

## 关键配置说明

### 环境变量 (.env)

**文件位置**: 项目根目录 `.env` (已从 `.env.example` 复制)

```bash
# ============================================
# MySQL 数据库配置
# ============================================
MYSQL_ROOT_PASSWORD=mrshudson_root
MYSQL_DATABASE=mrshudson
MYSQL_USER=mrshudson
MYSQL_PASSWORD=mrshudson_pass

# ============================================
# Redis 配置
# ============================================
REDIS_PASSWORD=mrshudson_redis

# ============================================
# Kimi AI API 配置 【必填】
# 获取地址: https://platform.moonshot.cn/
# ============================================
KIMI_API_KEY=your-kimi-api-key-here

# ============================================
# 天气 API 配置 【可选】
# 获取地址: https://lbs.amap.com/ (高德地图)
# ============================================
WEATHER_API_KEY=your-weather-api-key-here

# ============================================
# 讯飞语音服务配置 【可选】
# 获取地址: https://www.xfyun.cn/
# 仅当 mock-mode=false 时需要配置
# ============================================
XFYUN_APP_ID=
XFYUN_API_SECRET=
XFYUN_API_KEY=

# ============================================
# JVM 配置（可选）
# ============================================
JAVA_OPTS=-Xms512m -Xmx1024m
```

### 配置文件清单

| 配置文件 | 用途 | 敏感信息 |
|---------|------|---------|
| `.env` | Docker 环境变量 | 数据库密码、API Keys |
| `application.yml` | 后端应用配置 | 从环境变量读取 |
| `VoiceProperties.java` | 语音服务配置类 | 代码中无硬编码 |
| `vite.config.ts` | 前端构建配置 | 无 |

### 后端配置 (application.yml)

- **数据库**: MySQL 8.0，连接池自动配置
- **ORM**: MyBatis Plus (已从 JPA 迁移)
- **Redis**: 用于会话缓存
- **Kimi API**: 默认模型 `moonshot-v1-8k`
- **语音服务**: 讯飞语音 (当前为模拟模式)
- **天气服务**: 高德地图天气 API

### 前端配置 (vite.config.ts)

- **开发端口**: 3000
- **代理**: `/api` 转发到 `http://localhost:8080` (⚠️ 当前 axios.ts 已改为直接访问后端)

---

## API 设计

### 认证模块

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/logout` | 用户登出 |
| GET | `/api/auth/me` | 获取当前用户信息 |

**Demo 账号**: `admin` / `admin`

### 对话模块

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/chat/send` | 发送消息 |
| GET | `/api/chat/history` | 获取对话历史 |
| POST | `/api/chat/voice` | 语音输入 (语音识别后发送) |

### 日历模块

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/calendar/events` | 获取事件列表 |
| POST | `/api/calendar/events` | 创建事件 |
| PUT | `/api/calendar/events/{id}` | 更新事件 |
| DELETE | `/api/calendar/events/{id}` | 删除事件 |

### 待办模块

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/todos` | 获取待办列表 |
| POST | `/api/todos` | 创建待办 |
| PUT | `/api/todos/{id}` | 更新待办 |
| PUT | `/api/todos/{id}/complete` | 标记完成 |
| DELETE | `/api/todos/{id}` | 删除待办 |

### 天气模块

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/weather/current` | 当前天气 |
| GET | `/api/weather/forecast` | 天气预报 |

---

## 数据库设计

### 核心表结构

1. **user** - 用户表 (Demo 阶段简单设计)
2. **chat_message** - 对话记录表 (支持函数调用记录)
3. **calendar_event** - 日历事件表 (支持重复事件)
4. **todo_item** - 待办事项表 (支持优先级和状态)
5. **reminder** - 提醒记录表

### 数据库初始化

数据库表结构由 `V1__init_schema.sql` 初始化，包含默认 admin 用户。

---

## MCP 工具架构

项目采用 **Model Context Protocol (MCP)** 模式集成 AI 工具:

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  ChatService │────▶│  KimiClient  │────▶│  Kimi API    │
└──────────────┘     └──────────────┘     └──────────────┘
       │
       ▼
┌──────────────┐
│ ToolRegistry │
└──────────────┘
       │
   ┌───┴───┐
   ▼       ▼
┌──────┐ ┌──────┐ ┌────────┐ ┌────────┐
│Weather│ │Calendar│ │ Todo   │ │ (扩展) │
│ Tool │ │  Tool  │ │ Tool   │ │        │
└──────┘ └──────┘ └────────┘ └────────┘
```

### 内置 MCP 工具

- **WeatherTool**: 天气查询 (高德地图 API)
- **CalendarTool**: 日历事件管理
- **TodoTool**: 待办事项管理

---

## 开发约定

### 后端代码风格

- 使用 **Lombok** 简化 POJO 代码
- 使用构造函数注入 (`@RequiredArgsConstructor`)
- 统一返回格式: `Result<T>` (code, data, message)
- 使用 Slf4j 进行日志记录
- 异常处理统一在 `GlobalExceptionHandler`
- **实体类使用 MyBatis Plus 注解** (`@TableName`, `@TableId`, `@TableField`)
- **Mapper 继承 BaseMapper** 获得基础 CRUD 能力

### 前端代码风格

- 使用 **Composition API** (`<script setup>`)
- 使用 **TypeScript** 进行类型检查
- 状态管理使用 **Pinia**
- UI 组件使用 **Element Plus**
- API 调用统一封装在 `api/` 目录

### 命名规范

| 类型 | 命名风格 | 示例 |
|------|----------|------|
| Java 类 | 大驼峰 | `ChatController` |
| Java 方法 | 小驼峰 | `sendMessage` |
| Vue 组件 | 大驼峰 | `ChatView.vue` |
| TypeScript 接口 | 大驼峰 | `SendMessageRequest` |
| 数据库表 | 下划线 | `chat_message` |
| 数据库字段 | 下划线 | `created_at` |

---

## 近期变更记录

### 2026-03-01 - 架构重大变更

#### 1. ORM 框架迁移 (JPA → MyBatis Plus)

**变更原因:**
- 消除 Hibernate 与 MySQL enum 类型的验证冲突
- 更灵活的数据访问控制

**变更内容:**
- 移除 `spring-boot-starter-data-jpa` 依赖
- 添加 `mybatis-plus-spring-boot3-starter:3.5.5`
- 实体类从 JPA 注解改为 MyBatis Plus 注解:
  - `@Entity` → `@TableName`
  - `@Id` → `@TableId`
  - `@Column` → `@TableField`
- 删除 `repository/` 包，新建 `mapper/` 包
- 删除 `JpaConfig.java`
- 新建 `MyMetaObjectHandler.java` 处理自动填充
- `MrshudsonApplication.java` 添加 `@MapperScan`
- `application.yml` 移除 JPA/Flyway 配置，添加 MyBatis Plus 配置

#### 2. 移除 Flyway

**变更原因:**
- 简化数据库管理
- 数据库结构由手动 SQL 脚本初始化

**变更内容:**
- 移除 `flyway-core` 和 `flyway-mysql` 依赖
- 保留 `V1__init_schema.sql` 作为初始化参考

#### 3. Docker 部署调整

**变更内容:**
- 仅启动 MySQL 和 Redis (移除后端、前端自动启动)
- 后端改为本地开发模式启动

#### 4. 前端配置调整

**变更内容:**
- `axios.ts` 改为直接访问 `http://localhost:8080/api`
- 解决代理模式下的跨域问题

#### 5. 语音服务配置清理 (2026-03-01)

**变更原因:**
- 统一使用讯飞语音作为语音识别供应商
- 移除未使用的阿里云 NLS 配置

**变更内容:**
- `.env`: 移除 `ALIYUN_ACCESS_KEY_ID`, `ALIYUN_ACCESS_KEY_SECRET`, `ALIYUN_NLS_APP_KEY`
- `application.yml`: 移除阿里云 NLS 配置项
- `VoiceProperties.java`: 移除阿里云相关字段 (accessKeyId, accessKeySecret, appKey, nlsUrl, provider)
- `VoiceServiceImpl.java`: 移除 `recognizeByAliyun()` 方法及相关代码
- `.env`: 添加讯飞语音配置项 (`XFYUN_APP_ID`, `XFYUN_API_SECRET`, `XFYUN_API_KEY`)

### 已知问题

1. **Element Plus 依赖问题**
   - 现象: `Failed to resolve entry for package "element-plus"`
   - 状态: 待修复 (需完整重新安装 node_modules)

2. **前端登录问题**
   - 现象: 登录无响应
   - 原因: Element Plus 加载失败导致页面功能异常
   - 解决方案: 修复 Element Plus 依赖后重新测试

---

## 待办事项

### 高优先级

- [ ] 修复 Element Plus 依赖问题，重新安装前端依赖
- [ ] 验证前端登录功能
- [ ] 验证完整业务流程 (聊天、日历、待办)
- [ ] 验证语音服务功能 (当前为模拟模式)

### 中优先级

- [ ] 恢复前端代理配置 (vite.config.ts)
- [ ] 优化 MyBatis Plus 日志输出
- [ ] 添加单元测试
- [ ] 注册讯飞账号并配置真实语音 API Key

### 低优先级

- [ ] 升级 Element Plus 到最新版本
- [ ] 添加 API 文档 (Swagger)
- [ ] 配置生产环境部署
- [ ] 关闭语音模拟模式，测试真实语音识别

---

## 安全说明

### 当前实现

- **CSRF 防护**: 开发环境已禁用 (前后端分离)
- **Session 管理**: Spring Security 管理，7天过期
- **API 认证**: 除 `/api/auth/**` 外，所有接口需要认证
- **SQL 注入**: MyBatis Plus 参数绑定防护

### Demo 阶段限制

- 密码明文存储 (仅 Demo)
- 简单 Session 认证 (无 JWT)
- 单用户会话限制

### 生产环境建议

- 启用 HTTPS
- 配置 API 限流
- 敏感配置外部化 (K8s Secrets / Vault)
- 启用 SQL 注入、XSS 防护

---

## 扩展开发

### 添加新的 MCP 工具

1. 在 `mcp/` 包下创建新的 Tool 类，继承 `BaseTool`
2. 在 `ToolRegistry` 中注册工具
3. 在 `KimiClient` 的工具定义中添加 schema
4. 实现工具执行逻辑

### 添加新的 API 端点

1. Controller 添加 `@RestController` 和 `@RequestMapping`
2. 创建对应的 Service 接口和实现
3. 如需数据持久化，创建 Entity 和 Mapper
4. 更新数据库初始化脚本 (如需要)

---

## 故障排查

### 常见问题

1. **后端启动失败**: 检查 MySQL 和 Redis 是否已启动
2. **前端无法连接后端**: 检查 `axios.ts` 中的 baseURL 配置
3. **Kimi API 调用失败**: 检查 `KIMI_API_KEY` 环境变量
4. **数据库连接失败**: 检查 `application.yml` 中的数据库配置
5. **MyBatis Plus 报错**: 检查 Mapper 是否添加 `@Mapper` 注解

### 日志位置

- **后端**: 控制台输出 (默认配置)
- **前端**: 浏览器控制台

---

## 相关文档

- **需求文档**: `.spec-workflow/specs/mrshudson-core/requirements.md`
- **设计文档**: `.spec-workflow/specs/mrshudson-core/design.md`
- **产品愿景**: `.spec-workflow/steering/product.md`
- **技术架构**: `.spec-workflow/steering/tech.md`

---

*最后更新: 2026-03-01*
*架构变更: JPA → MyBatis Plus (已完成)*
*配置变更: 移除阿里云 NLS，统一使用讯飞语音 (已完成)*
