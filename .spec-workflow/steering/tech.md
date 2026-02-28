# MrsHudson 技术架构文档

## 技术栈选择

### 后端架构
| 层级 | 技术选型 | 版本 | 说明 |
|------|---------|------|------|
| 框架 | Spring Boot | 3.2.x | 主框架 |
| AI集成 | Spring AI | 1.0.x | AI抽象层 |
| LLM | Moonshot Kimi API | 最新 | 核心对话能力 |
| 数据库 | MySQL | 8.x | 主存储 |
| 缓存 | Redis | 7.x | 会话、缓存 |
| ORM | MyBatis Plus | 3.5.x | 数据访问 |
| 安全 | Spring Security + JWT | - | 认证授权 |
| 消息 | WebSocket (STOMP) | - | 实时通信 |
| 语音 | 阿里云/讯飞语音 | - | 语音识别/合成 |

### 前端架构
| 层级 | 技术选型 | 版本 | 说明 |
|------|---------|------|------|
| 框架 | Vue 3 | 3.4.x | Composition API |
| 语言 | TypeScript | 5.x | 类型安全 |
| UI库 | Element Plus | 2.x | 组件库 |
| 状态 | Pinia | 2.x | 状态管理 |
| 路由 | Vue Router | 4.x | 路由管理 |
| HTTP | Axios | 1.x | API通信 |
| 构建 | Vite | 5.x | 构建工具 |
| 语音 | Web Speech API | - | 浏览器语音支持 |

### 移动端架构（iOS）
| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 框架 | SwiftUI | 原生UI框架 |
| 语言 | Swift 5.x | 类型安全 |
| 网络 | Alamofire | HTTP通信 |
| 数据 | Core Data | 本地存储 |
| 语音 | Apple Speech / AVFoundation | 语音识别/合成 |
| 推送 | APNs | 苹果推送服务 |

### 移动端架构（Android）
| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 框架 | Jetpack Compose | 声明式UI |
| 语言 | Kotlin | 首选语言 |
| 网络 | Retrofit + OkHttp | HTTP通信 |
| 数据 | Room | 本地存储 |
| 语音 | 阿里云/讯飞语音SDK | 语音识别/合成 |
| 推送 | Firebase Cloud Messaging | 谷歌推送服务 |
| 层级 | 技术选型 | 版本 | 说明 |
|------|---------|------|------|
| 框架 | Vue 3 | 3.4.x | Composition API |
| 语言 | TypeScript | 5.x | 类型安全 |
| UI库 | Element Plus | 2.x | 组件库 |
| 状态 | Pinia | 2.x | 状态管理 |
| 路由 | Vue Router | 4.x | 路由管理 |
| HTTP | Axios | 1.x | API通信 |
| 构建 | Vite | 5.x | 构建工具 |

## MCP服务集成

### 工具服务
| 服务 | 用途 | 协议 |
|------|------|------|
| Weather MCP | 天气查询 | stdio/sse |
| Calendar MCP | 日历管理 | stdio/sse |
| Maps MCP | 路线规划 | stdio/sse |
| Todo MCP | 待办管理 | stdio/sse |
| Notification MCP | 提醒推送 | stdio/sse |

## 项目结构

```
mrshudson/
├── mrshudson-backend/          # Spring Boot后端
│   ├── src/main/java/
│   │   └── com/mrshudson/
│   │       ├── MrshudsonApplication.java
│   │       ├── config/          # 配置类
│   │       ├── controller/      # API控制器
│   │       ├── service/         # 业务逻辑
│   │       ├── domain/          # 领域模型
│   │       ├── repository/      # 数据访问
│   │       ├── mcp/             # MCP客户端
│   │       └── security/        # 安全相关
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/        # 数据库迁移
│
├── mrshudson-frontend/          # Vue3前端
│   ├── src/
│   │   ├── components/          # 组件
│   │   ├── views/               # 页面
│   │   ├── stores/              # Pinia状态
│   │   ├── api/                 # API封装
│   │   ├── composables/         # 组合式函数
│   │   └── types/               # TypeScript类型
│   └── vite.config.ts
│
├── mrshudson-mcp/               # MCP服务集合
│   ├── weather-mcp/
│   ├── calendar-mcp/
│   ├── maps-mcp/
│   └── todo-mcp/
│
├── mrshudson-ios/               # iOS原生应用
│   ├── MrsHudson/
│   │   ├── App/
│   │   ├── Views/
│   │   ├── ViewModels/
│   │   ├── Services/
│   │   ├── Models/
│   │   └── Utils/
│   └── MrsHudson.xcodeproj
│
└── mrshudson-android/           # Android原生应用
    ├── app/src/main/java/com/mrshudson/
    │   ├── ui/                  # UI层
    │   ├── viewmodel/           # ViewModel
    │   ├── repository/          # 数据仓库
    │   ├── service/             # 服务
    │   └── di/                  # 依赖注入
    └── app/src/main/res/        # 资源文件
```

## 数据库设计原则

- 采用领域驱动设计(DDD)思想
- 使用时间戳审计字段(created_at, updated_at)
- 软删除机制(deleted_at)
- 适当索引优化查询

## 安全规范

- JWT Token认证
- HTTPS强制
- 敏感数据加密存储
- API限流防护
- SQL注入防护(MyBatis参数绑定)
