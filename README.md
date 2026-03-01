# ListApp - 轻量级列表聚合工具

一个 App 记录所有类型的列表，轻量如纸笔，分享如 Git。

## 功能特性

- 📋 **多类型列表** - Live 演出、餐厅、书单、影单、旅行清单...
- ⚡ **轻量快速** - 3 秒内完成一条记录
- 🔗 **分享与 Fork** - 列表可以克隆，像 GitHub 一样
- 📴 **离线优先** - 无网络也能完整使用

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose
- **数据库**: Room
- **依赖注入**: Hilt
- **导航**: Navigation Compose

## 项目结构

```
app/
├── src/main/
│   ├── java/com/fizzzli/listapp/
│   │   ├── ui/              # UI 层
│   │   ├── data/            # 数据层
│   │   ├── domain/          # 领域层
│   │   └── di/              # 依赖注入
│   └── res/                 # 资源文件
└── build.gradle.kts
```

## 开发环境

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34

## 构建

```bash
./gradlew assembleDebug
```

## 许可证

MIT License
