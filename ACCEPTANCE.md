# ListApp - 验收指南

## 当前状态

**Phase 1:** 本地 MVP 开发完成
**代码:** https://github.com/Fizzzli/list-app
**CI:** https://github.com/Fizzzli/list-app/actions

---

## 验收方式

### 方式 A：GitHub Actions 自动构建（推荐）

1. 触发构建：
   - 去 https://github.com/Fizzzli/list-app/actions/workflows/build.yml
   - 点击 "Run workflow"
   - 输入版本号如 `v1.0.0`
   - 点击绿色按钮

2. 等待完成（约 5-10 分钟）

3. 下载 APK：
   - 点击完成的 run
   - 在 "Artifacts" 部分下载 `app-debug.apk`
   - 或者去 Releases 页面下载

### 方式 B：本地编译

```bash
cd /path/to/list-app
./gradlew assembleDebug
```

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

---

## 测试清单

安装 APK 后测试：

- [ ] App 能启动，不崩溃
- [ ] 主界面显示"暂无列表"
- [ ] 点击 FAB 能创建列表
- [ ] 能选择模板（Live、餐厅、书单等）
- [ ] 创建列表后能在首页看到
- [ ] 点击列表能进入详情页
- [ ] 能添加条目
- [ ] 能标记条目为完成
- [ ] 能删除条目

---

## 流程验证

- [x] 需求 → GitHub Issues
- [x] 开发 → 代码提交
- [x] 推送 → GitHub
- [ ] CI → 自动构建 **(需要 Android 许可证)**
- [ ] 发布 → 自动创建 Release
- [ ] 验收 → 下载 APK 测试

---

## 下一步

Phase 1 验收通过后：

- Phase 2: 云同步 + 分享功能
- Phase 3: 变现功能（Pro 版）
