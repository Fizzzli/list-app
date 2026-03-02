# ListApp Web - 轻量级列表聚合工具

一个纯前端的列表管理应用，数据保存在浏览器本地（IndexedDB）。

## 特性

- 📋 多类型列表 - Live 演出、餐厅、书单、影单、旅行清单...
- ⚡ 轻量快速 - 3 秒内完成一条记录
- 📴 离线优先 - 无需网络，数据保存在浏览器
- 🔒 隐私安全 - 数据不上传服务器，只存在你的浏览器

## 技术栈

- **框架**: Next.js 16 (App Router)
- **语言**: TypeScript
- **样式**: Tailwind CSS 4
- **UI**: shadcn/ui
- **数据库**: Dexie.js (IndexedDB)
- **状态管理**: Zustand

## 快速开始

### 本地开发

```bash
# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev

# 访问 http://localhost:3000
```

### 构建

```bash
pnpm build
pnpm start
```

## 部署

### Vercel（推荐）

1. 安装 Vercel CLI：
```bash
npm i -g vercel
```

2. 部署：
```bash
vercel
```

3. 生产环境部署：
```bash
vercel --prod
```

### 其他静态托管

构建后输出在 `.next/` 目录，可以部署到任何支持 Next.js 的平台：
- Netlify
- Cloudflare Pages
- GitHub Pages (需要额外配置)

## 数据存储

所有数据保存在浏览器 IndexedDB 中：
- **数据库名**: ListAppDB
- **表**: lists
- **持久化**: 数据会永久保存，除非清除浏览器数据

### 数据导出（TODO）
- 导出为 JSON
- 导入备份

## 开发计划

### Phase 1 (已完成)
- [x] 项目搭建
- [x] 列表 CRUD
- [x] 本地存储

### Phase 2 (进行中)
- [ ] 列表详情页
- [ ] 条目管理（添加/编辑/删除/标记完成）
- [ ] 模板系统

### Phase 3
- [ ] 分享功能（导出/导入 JSON）
- [ ] PWA 支持（离线安装）
- [ ] 云同步（可选）

## 本地开发

```bash
pnpm dev
```

访问 http://localhost:3000

## 许可证

MIT
