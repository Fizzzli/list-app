# ListApp Phase 2 架构设计文档 - 云同步与分享功能

**版本：** 1.0  
**日期：** 2026-03-01  
**作者：** Tech Lead Agent  
**状态：** 待评审

---

## 1. 技术选型

### 1.1 后端方案决策

#### 方案对比

| 维度 | Firebase | Supabase | 自建后端 |
|------|----------|----------|----------|
| 开发速度 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ |
| 实时同步 | 原生支持 | 需配置 | 需自研 |
| 离线支持 | 优秀 SDK | 一般 | 需自研 |
| 认证系统 | 完整 | 完整 | 需自研 |
| 国内访问 | ⚠️ 不稳定 | ⚠️ 不稳定 | ✅ 可控 |
| 成本 | 免费额度高 | 免费额度高 | 服务器成本 |
| 锁定风险 | 高 | 中 | 低 |

#### 最终决策：**Firebase 为主，Supabase 为备选**

**理由：**
- Firebase Firestore 的离线持久化和实时同步是业界最佳
- Authentication 支持匿名登录和账号升级，完美匹配 US-02
- 免费额度足够 Phase 2 用户规模（月活 10 万以下）
- 通过 Repository 模式抽象数据层，未来可切换 Supabase

**风险缓解：**
```kotlin
// 数据层抽象示例
interface ListRepository {
    suspend fun getList(listId: String): ListEntity?
    suspend fun syncChanges(changes: List<Change>): SyncResult
    suspend fun subscribeToList(listId: String): Flow<ListEntity>
}

// Firebase 实现
class FirebaseListRepository(...) : ListRepository { ... }

// Supabase 实现（备选）
class SupabaseListRepository(...) : ListRepository { ... }
```

---

### 1.2 数据同步策略

#### 策略选择：**乐观同步 + 最后写入优先（LWW）**

**乐观同步理由：**
- 用户体验优先，操作无阻塞
- 冲突概率低（列表编辑非高频操作）
- 符合离线优先的产品定位

**冲突解决策略：**

| 冲突类型 | 解决策略 | 用户感知 |
|----------|----------|----------|
| 同一字段多设备修改 | LWW（Last Write Wins） | 无感知，保留版本历史 |
| 删除 vs 修改 | 修改优先，标记待确认 | 下次同步时提示 |
| 同一 Item 多字段修改 | 字段级合并 | 无感知 |
| 列表元数据冲突 | LWW | 无感知 |

**版本控制机制：**
```kotlin
data class VersionedItem(
    val id: String,
    val data: ItemData,
    val version: Long,           // 单调递增版本号
    val lastModified: Long,      // 毫秒时间戳
    val deviceId: String,        // 修改设备标识
    val vectorClock: Map<String, Long>  // 设备 -> 版本号映射
)
```

---

### 1.3 认证方案

#### 阶段式认证流程

```
┌─────────────────────────────────────────────────────────┐
│  Phase 1: 匿名登录（首次启动）                           │
│  - Firebase Anonymous Auth                              │
│  - 自动生成 UID，无需用户操作                            │
│  - 完整功能可用                                          │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  Phase 2: 账号绑定（可选升级）                           │
│  - 匿名账号 → 邮箱/Google 账号                           │
│  - Firebase account linking                             │
│  - 数据不丢失，UID 保持不变                              │
└─────────────────────────────────────────────────────────┘
```

**认证方式支持：**
- ✅ 匿名登录（默认）
- ✅ 邮箱密码（绑定/注册）
- ✅ Google OAuth（绑定/登录）
- ⏳ 手机号（Phase 3）

**Token 管理：**
```kotlin
data class AuthToken(
    val firebaseIdToken: String,    // JWT，1 小时过期
    val refreshToken: String,       // 长期有效
    val expiresIn: Long,            // 过期时间戳
    val uid: String
)

// Token 自动刷新机制
class AuthManager {
    private val tokenCache = MutableStateFlow<AuthToken?>(null)
    
    init {
        // 每 50 分钟刷新一次（token 有效期 60 分钟）
        viewModelScope.launch {
            while (true) {
                delay(50.minutes)
                refreshToken()
            }
        }
    }
}
```

---

## 2. 数据模型设计

### 2.1 本地 Room Entity 扩展

#### 核心 Entity 变更

```kotlin
// ===== 原有 ListEntity 扩展 =====
@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val title: String,
    val description: String?,
    val icon: String?,
    val color: String?,
    val createdAt: Long,
    val updatedAt: Long,
    
    // === 新增同步字段 ===
    val ownerId: String,                    // 所有者 UID
    val version: Long = 0,                  // 本地版本号
    val lastSyncedAt: Long? = null,         // 最后同步时间
    val isDeleted: Boolean = false,         // 软删除标记
    val pendingChanges: Boolean = false,    // 有待同步变更
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    
    // === 新增分享字段 ===
    val visibility: Visibility = Visibility.PRIVATE,
    val shareToken: String? = null,
    val shareExpiresAt: Long? = null
) {
    // 计算属性：是否需要同步
    val needsSync: Boolean 
        get() = pendingChanges && syncStatus != SyncStatus.SYNCING
}

enum class SyncStatus { 
    SYNCED,      // 已同步
    PENDING,     // 有待同步变更
    SYNCING,     // 同步中
    CONFLICT,    // 检测到冲突
    FAILED       // 同步失败
}

enum class Visibility { 
    PRIVATE,     // 仅自己可见
    SHARED,      // 指定人可见
    PUBLIC       // 公开可见
}

// ===== 原有 ItemEntity 扩展 =====
@Entity(
    tableName = "items",
    indices = [Index(value = ["listId"]), Index(value = ["version"])]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val status: ItemStatus,
    val fields: Map<String, String>,  // JSON 序列化
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
    
    // === 新增同步字段 ===
    val version: Long = 0,
    val deviceId: String? = null,     // 最后修改设备
    val vectorClock: String? = null,  // JSON: {deviceId: version}
    val isDeleted: Boolean = false,
    val pendingChanges: Boolean = false
)

// ===== 新增 Entity：用户账号 =====
@Entity(tableName = "user_accounts")
data class UserAccount(
    @PrimaryKey val uid: String,
    val isAnonymous: Boolean,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: Long,
    val lastLoginAt: Long,
    val lastSyncAt: Long?
)

// ===== 新增 Entity：同步元数据 =====
@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey val entityType: String,   // "list" | "item" | "collaborator"
    @PrimaryKey val entityId: String,
    val localVersion: Long,
    val remoteVersion: Long,
    val lastModified: Long,
    val isDeleted: Boolean,
    val conflictResolved: Boolean = false,
    val conflictData: String? = null  // JSON: 冲突详情
)

// ===== 新增 Entity：分享配置 =====
@Entity(tableName = "share_configs")
data class ShareConfig(
    @PrimaryKey val listId: String,
    val visibility: Visibility,
    val shareToken: String?,
    val shareUrl: String?,
    val expiresAt: Long?,
    val accessCount: Int = 0,
    val lastAccessedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

// ===== 新增 Entity：协作者 =====
@Entity(
    tableName = "collaborators",
    indices = [Index(value = ["listId"]), Index(value = ["userId"])]
)
data class Collaborator(
    @PrimaryKey val listId: String,
    @PrimaryKey val userId: String,
    val userEmail: String?,
    val userName: String?,
    val permission: Permission,
    val status: CollaboratorStatus,
    val invitedAt: Long,
    val acceptedAt: Long?,
    val invitedBy: String
)

enum class Permission { VIEW, COMMENT, EDIT }

enum class CollaboratorStatus { 
    PENDING,    // 已邀请，未接受
    ACCEPTED,   // 已接受
    REVOKED     // 已撤销
}

// ===== 新增 Entity：Fork 关系 =====
@Entity(tableName = "forks")
data class ForkRelation(
    @PrimaryKey val forkId: String,       // Fork 后的列表 ID
    val originalListId: String,
    val originalOwnerId: String,
    val forkedAt: Long,
    val isLinked: Boolean,                // 是否关联原列表
    val lastSyncedFromOriginal: Long?     // 最后同步时间
)

// ===== 新增 Entity：操作日志（用于版本历史） =====
@Entity(
    tableName = "operation_logs",
    indices = [Index(value = ["listId"]), Index(value = ["timestamp"])]
)
data class OperationLog(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val listId: String,
    val itemId: String?,
    val operation: OperationType,
    val oldData: String?,      // JSON: 变更前数据
    val newData: String?,      // JSON: 变更后数据
    val deviceId: String,
    val timestamp: Long
)

enum class OperationType { 
    CREATE, UPDATE, DELETE, RESTORE, FORK, SHARE 
}
```

---

### 2.2 Firestore 数据结构

```
┌─────────────────────────────────────────────────────────────┐
│  users/{uid}                                                 │
├─────────────────────────────────────────────────────────────┤
│  {                                                           │
│    "profile": {                                              │
│      "isAnonymous": true,                                    │
│      "email": null,                                          │
│      "displayName": "用户 1234",                              │
│      "photoUrl": null,                                       │
│      "createdAt": 1709294400000,                             │
│      "lastLoginAt": 1709380800000                            │
│    },                                                        │
│    "settings": {                                             │
│      "theme": "system",                                      │
│      "notifications": true                                   │
│    }                                                         │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  lists/{listId}                                              │
├─────────────────────────────────────────────────────────────┤
│  {                                                           │
│    "owner": {                                                │
│      "uid": "user_abc123",                                   │
│      "name": "用户 1234"                                      │
│    },                                                        │
│    "templateId": "template_travel",                          │
│    "title": "日本旅行清单",                                   │
│    "description": "2026 年 4 月出行",                         │
│    "icon": "🗾",                                             │
│    "color": "#4A90D9",                                       │
│    "visibility": "SHARED",                                   │
│    "shareToken": "shr_xyz789",                               │
│    "shareExpiresAt": null,                                   │
│    "version": 15,                                            │
│    "createdAt": 1709294400000,                               │
│    "updatedAt": 1709380800000,                               │
│    "isDeleted": false                                        │
│  }                                                           │
│                                                              │
│  lists/{listId}/items/{itemId}                               │
│  {                                                           │
│    "name": "护照",                                           │
│    "status": "completed",                                    │
│    "fields": {                                               │
│      "notes": "确保护照有效期>6 个月",                        │
│      "priority": "high"                                      │
│    },                                                        │
│    "order": 1,                                               │
│    "version": 3,                                             │
│    "deviceId": "device_phone_001",                           │
│    "vectorClock": {                                          │
│      "device_phone_001": 3,                                  │
│      "device_tablet_002": 2                                  │
│    },                                                        │
│    "createdAt": 1709294400000,                               │
│    "updatedAt": 1709380800000,                               │
│    "isDeleted": false                                        │
│  }                                                           │
│                                                              │
│  lists/{listId}/collaborators/{userId}                       │
│  {                                                           │
│    "userEmail": "friend@example.com",                        │
│    "userName": "朋友",                                       │
│    "permission": "EDIT",                                     │
│    "status": "ACCEPTED",                                     │
│    "invitedAt": 1709380800000,                               │
│    "acceptedAt": 1709384400000,                              │
│    "invitedBy": "user_abc123"                                │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  forks/{forkId}                                              │
├─────────────────────────────────────────────────────────────┤
│  {                                                           │
│    "originalListId": "list_original_123",                    │
│    "originalOwnerId": "user_original_456",                   │
│    "forkedById": "user_fork_789",                            │
│    "forkedAt": 1709380800000,                                │
│    "isLinked": true,                                         │
│    "lastSyncedFromOriginal": 1709384400000                   │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  share_access/{shareToken}                                   │
├─────────────────────────────────────────────────────────────┤
│  {                                                           │
│    "listId": "list_123",                                     │
│    "ownerId": "user_abc123",                                 │
│    "visibility": "PUBLIC",                                   │
│    "expiresAt": null,                                        │
│    "accessLog": [                                            │
│      { "accessedAt": 1709380800000, "viewerId": "anonymous" }│
│    ]                                                         │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
```

---

### 2.3 索引设计

#### Room 本地索引

```kotlin
@Database(
    entities = [
        ListEntity::class,
        ItemEntity::class,
        UserAccount::class,
        SyncMetadata::class,
        ShareConfig::class,
        Collaborator::class,
        ForkRelation::class,
        OperationLog::class
    ],
    version = 2,
    exportSchema = true
)
abstract class ListAppDatabase : RoomDatabase() {
    
    // 预定义索引
    // ListEntity: 
    //   - INDEX(lists_ownerId)
    //   - INDEX(lists_visibility)
    //   - INDEX(lists_syncStatus)
    //
    // ItemEntity:
    //   - INDEX(items_listId)
    //   - INDEX(items_version)
    //   - INDEX(items_listId_order)
    //
    // Collaborator:
    //   - INDEX(collaborators_listId)
    //   - INDEX(collaborators_userId)
    //
    // OperationLog:
    //   - INDEX(operation_logs_listId)
    //   - INDEX(operation_logs_timestamp)
}
```

#### Firestore 索引

```javascript
// 复合索引（需在 Firebase 控制台配置）
{
  "indexes": [
    {
      "collectionGroup": "lists",
      "fields": [
        { "fieldPath": "owner.uid", "order": "ASCENDING" },
        { "fieldPath": "updatedAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "lists",
      "fields": [
        { "fieldPath": "visibility", "order": "ASCENDING" },
        { "fieldPath": "shareToken", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "items",
      "fields": [
        { "fieldPath": "listId", "order": "ASCENDING" },
        { "fieldPath": "order", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "collaborators",
      "fields": [
        { "fieldPath": "userId", "order": "ASCENDING" },
        { "fieldPath": "status", "order": "ASCENDING" }
      ]
    }
  ]
}
```

---

## 3. API 设计

### 3.1 REST API 端点

> **注意：** 实际实现使用 Firebase SDK 直接操作 Firestore，以下 API 设计用于理解数据流，或为未来自建后端预留接口。

#### 认证相关

```http
POST /api/v1/auth/anonymous
Content-Type: application/json

Response: 200 OK
{
  "uid": "user_anon_abc123",
  "firebaseIdToken": "eyJhbGc...",
  "expiresIn": 3600,
  "isAnonymous": true
}

POST /api/v1/auth/link-email
Content-Type: application/json
{
  "email": "user@example.com",
  "password": "secure_password",
  "firebaseIdToken": "eyJhbGc..."
}

Response: 200 OK
{
  "uid": "user_anon_abc123",  // UID 不变
  "isAnonymous": false,
  "email": "user@example.com"
}
```

#### 同步相关

```http
POST /api/v1/sync/push
Content-Type: application/json
Authorization: Bearer {firebaseIdToken}

Request:
{
  "changes": [
    {
      "entityType": "list",
      "entityId": "list_123",
      "operation": "UPDATE",
      "data": { "title": "新标题", "version": 16 },
      "baseVersion": 15,
      "deviceId": "device_phone_001",
      "timestamp": 1709380800000
    },
    {
      "entityType": "item",
      "entityId": "item_456",
      "operation": "CREATE",
      "data": { "name": "新物品", "status": "pending" },
      "deviceId": "device_phone_001",
      "timestamp": 1709380801000
    }
  ],
  "clientVectorClock": {
    "device_phone_001": 100
  }
}

Response: 200 OK
{
  "syncedChanges": [
    {
      "entityType": "list",
      "entityId": "list_123",
      "newVersion": 16,
      "status": "SYNCED"
    }
  ],
  "conflicts": [],
  "serverVectorClock": {
    "device_phone_001": 100,
    "device_tablet_002": 50
  },
  "syncTimestamp": 1709380802000
}

GET /api/v1/sync/pull?since={timestamp}&vectorClock={json}
Authorization: Bearer {firebaseIdToken}

Response: 200 OK
{
  "changes": [
    {
      "entityType": "list",
      "entityId": "list_789",
      "operation": "UPDATE",
      "data": { ... },
      "version": 20,
      "deviceId": "device_tablet_002",
      "timestamp": 1709380803000
    }
  ],
  "deletions": [
    {
      "entityType": "item",
      "entityId": "item_deleted_123",
      "version": 5
    }
  ],
  "serverVectorClock": {
    "device_phone_001": 100,
    "device_tablet_002": 51
  },
  "syncTimestamp": 1709380804000
}

POST /api/v1/sync/resolve-conflict
Content-Type: application/json
Authorization: Bearer {firebaseIdToken}

Request:
{
  "conflictId": "conflict_abc123",
  "resolution": "KEEP_LOCAL" | "KEEP_REMOTE" | "MERGE",
  "mergedData": { ... }  // 仅 MERGE 时需要
}

Response: 200 OK
{
  "conflictId": "conflict_abc123",
  "resolved": true,
  "resolvedVersion": 17
}
```

#### 分享相关

```http
POST /api/v1/lists/{listId}/share
Content-Type: application/json
Authorization: Bearer {firebaseIdToken}

Request:
{
  "visibility": "PUBLIC",
  "expiresAt": 1710000000000,  // 可选，过期时间
  "allowFork": true
}

Response: 200 OK
{
  "listId": "list_123",
  "shareUrl": "https://listapp.example.com/s/shr_xyz789",
  "shareToken": "shr_xyz789",
  "visibility": "PUBLIC",
  "expiresAt": 1710000000000,
  "qrCodeDataUrl": "data:image/png;base64,..."
}

DELETE /api/v1/lists/{listId}/share
Authorization: Bearer {firebaseIdToken}

Response: 204 No Content

POST /api/v1/lists/{listId}/collaborate
Content-Type: application/json
Authorization: Bearer {firebaseIdToken}

Request:
{
  "invitees": [
    {
      "email": "friend@example.com",
      "permission": "EDIT"
    }
  ],
  "message": "一起维护这个列表吧！"  // 可选
}

Response: 200 OK
{
  "invitations": [
    {
      "email": "friend@example.com",
      "status": "SENT",
      "invitationId": "inv_abc123"
    }
  ]
}

GET /api/v1/share/{shareToken}
Authorization: Bearer {firebaseIdToken}  // 可选，公开链接无需认证

Response: 200 OK
{
  "listId": "list_123",
  "title": "日本旅行清单",
  "visibility": "PUBLIC",
  "items": [ ... ],
  "canEdit": false,
  "canFork": true,
  "owner": {
    "uid": "user_abc123",
    "name": "用户 1234"
  }
}

POST /api/v1/lists/{listId}/fork
Content-Type: application/json
Authorization: Bearer {firebaseIdToken}

Request:
{
  "isLinked": true,  // 是否关联原列表
  "newTitle": "我的日本旅行清单"  // 可选，默认复制原标题
}

Response: 201 Created
{
  "forkId": "list_fork_456",
  "originalListId": "list_123",
  "isLinked": true,
  "createdAt": 1709380805000
}
```

---

### 3.2 错误处理

#### 统一错误响应格式

```json
{
  "error": {
    "code": "SYNC_CONFLICT",
    "message": "检测到同步冲突，请解决后重试",
    "details": {
      "conflictId": "conflict_abc123",
      "entityType": "list",
      "entityId": "list_123",
      "localVersion": 15,
      "remoteVersion": 16,
      "conflictingFields": ["title", "description"]
    },
    "recoverable": true,
    "suggestedAction": "RESOLVE_CONFLICT"
  }
}
```

#### 错误码定义

| 错误码 | HTTP 状态 | 说明 | 恢复建议 |
|--------|----------|------|----------|
| `AUTH_REQUIRED` | 401 | 未认证或 Token 过期 | 重新登录 |
| `AUTH_INVALID` | 401 | Token 无效 | 清除缓存，重新登录 |
| `FORBIDDEN` | 403 | 无权限访问资源 | 检查分享权限 |
| `NOT_FOUND` | 404 | 资源不存在 | 刷新列表 |
| `SYNC_CONFLICT` | 409 | 同步冲突 | 调用冲突解决接口 |
| `RATE_LIMITED` | 429 | 请求过于频繁 | 等待后重试 |
| `NETWORK_ERROR` | - | 网络错误 | 检查网络，离线模式 |
| `SERVER_ERROR` | 500 | 服务器内部错误 | 稍后重试 |
| `SHARE_EXPIRED` | 410 | 分享链接已过期 | 联系所有者 |
| `SHARE_REVOKED` | 403 | 分享已被撤销 | 联系所有者 |

---

## 4. 同步协议

### 4.1 增量同步逻辑

#### 同步流程图

```
┌─────────────────────────────────────────────────────────────┐
│                     客户端同步流程                           │
└─────────────────────────────────────────────────────────────┘

  ┌──────────────┐
  │  数据变更事件 │
  └──────┬───────┘
         │
         ▼
  ┌──────────────┐     ┌──────────────┐
  │  本地写入 DB  │────▶│  记录 Change  │
  └──────────────┘     └──────┬───────┘
                              │
         ┌────────────────────┘
         │
         ▼
  ┌──────────────┐     ┌──────────────┐
  │  网络可用？   │────▶│  立即同步    │
  └──────┬───────┘     └──────────────┘
         │ 否
         ▼
  ┌──────────────┐
  │  标记待同步  │
  │  (pending=true)
  └──────────────┘


  ┌─────────────────────────────────────────────────────────────┐
│                     服务端同步流程                           │
└─────────────────────────────────────────────────────────────┘

  ┌──────────────┐
  │  接收变更    │
  └──────┬───────┘
         │
         ▼
  ┌──────────────┐
  │  版本检查    │───▶ 版本过旧？───▶ 返回冲突
  └──────┬───────┘
         │ 版本 OK
         ▼
  ┌──────────────┐
  │  写入 Firestore│
  └──────┬───────┘
         │
         ▼
  ┌──────────────┐
  │  广播变更    │───▶ 其他在线设备收到推送
  └──────────────┘
```

#### 变更追踪机制

```kotlin
// 变更数据模型
data class Change(
    val id: String = UUID.randomUUID().toString(),
    val entityType: EntityType,
    val entityId: String,
    val operation: OperationType,
    val data: String,          // JSON 序列化
    val baseVersion: Long,     // 变更基于的版本
    val deviceId: String,
    val timestamp: Long,
    val synced: Boolean = false
)

// 变更队列管理
class ChangeQueue @Inject constructor(
    private val dao: ChangeDao
) {
    // 添加变更
    suspend fun enqueue(change: Change) {
        dao.insert(change)
        trySync()  // 尝试触发同步
    }
    
    // 获取待同步变更（批量）
    suspend fun getPendingChanges(limit: Int = 50): List<Change> {
        return dao.getUnsynced(limit)
    }
    
    // 标记已同步
    suspend fun markSynced(changeIds: List<String>) {
        dao.updateSynced(changeIds)
    }
    
    // 合并短时间内的多次操作
    suspend fun coalesceChanges(entityId: String): Change? {
        return dao.getLatestUnsynced(entityId)
    }
}
```

#### 批量合并策略

```kotlin
class SyncBatcher(
    private val debounceMs: Long = 1000,  // 1 秒防抖
    private val maxBatchSize: Int = 50
) {
    private val changeBuffer = Channel<Change>(Channel.UNLIMITED)
    private val batchFlow = flow {
        val batch = mutableListOf<Change>()
        
        changeBuffer.consumeAsFlow()
            .debounce(debounceMs)
            .collect { change ->
                batch.add(change)
                
                if (batch.size >= maxBatchSize) {
                    emit(batch.toList())
                    batch.clear()
                }
            }
        
        // 定期刷新剩余变更
        flow {
            while (true) {
                delay(5000)  // 5 秒强制刷新
                if (batch.isNotEmpty()) {
                    emit(batch.toList())
                    batch.clear()
                }
            }
        }.launchIn(viewModelScope)
    }
}
```

---

### 4.2 版本控制机制

#### 版本向量（Vector Clock）实现

```kotlin
data class VectorClock(
    val clocks: MutableMap<String, Long> = mutableMapOf()
) {
    // 递增本地时钟
    fun increment(deviceId: String) {
        clocks[deviceId] = (clocks[deviceId] ?: 0) + 1
    }
    
    // 合并远程时钟
    fun merge(other: VectorClock) {
        other.clocks.forEach { (deviceId, version) ->
            clocks[deviceId] = maxOf(clocks[deviceId] ?: 0, version)
        }
    }
    
    // 比较：返回 BEFORE, AFTER, CONCURRENT, EQUAL
    fun compareTo(other: VectorClock): ComparisonResult {
        var thisGreater = false
        var otherGreater = false
        
        val allDevices = (clocks.keys + other.clocks.keys).toSet()
        
        for (device in allDevices) {
            val thisVersion = clocks[device] ?: 0
            val otherVersion = other.clocks[device] ?: 0
            
            if (thisVersion > otherVersion) thisGreater = true
            if (otherVersion > thisVersion) otherGreater = true
        }
        
        return when {
            !thisGreater && !otherGreater -> ComparisonResult.EQUAL
            thisGreater && !otherGreater -> ComparisonResult.AFTER
            !thisGreater && otherGreater -> ComparisonResult.BEFORE
            else -> ComparisonResult.CONCURRENT  // 并发，需要冲突解决
        }
    }
    
    fun toJson(): String = Json.encodeToString(clocks)
    
    companion object {
        fun fromJson(json: String): VectorClock {
            val clocks = Json.decodeFromString<Map<String, Long>>(json)
            return VectorClock(clocks.toMutableMap())
        }
    }
}

enum class ComparisonResult { BEFORE, AFTER, CONCURRENT, EQUAL }
```

#### 时间戳策略

```kotlin
data class TimestampedVersion(
    val version: Long,
    val timestamp: Long,  // 毫秒时间戳
    val deviceId: String
) : Comparable<TimestampedVersion> {
    
    // 用于 LWW 冲突解决
    override fun compareTo(other: TimestampedVersion): Int {
        // 先比较时间戳
        val timeCompare = timestamp.compareTo(other.timestamp)
        if (timeCompare != 0) return timeCompare
        
        // 时间戳相同，比较设备 ID（确定性排序）
        return deviceId.compareTo(other.deviceId)
    }
}
```

---

### 4.3 冲突检测与解决

#### 冲突检测算法

```kotlin
class ConflictDetector {
    
    fun detectConflict(
        local: VersionedItem,
        remote: VersionedItem
    ): Conflict? {
        // 同一设备，无冲突
        if (local.deviceId == remote.deviceId) {
            return null
        }
        
        // 比较版本向量
        val comparison = local.vectorClock.compareTo(remote.vectorClock)
        
        return when (comparison) {
            ComparisonResult.EQUAL -> null
            ComparisonResult.AFTER -> null  // 本地更新
            ComparisonResult.BEFORE -> null  // 远程更新
            ComparisonResult.CONCURRENT -> {
                // 并发修改，检测具体冲突字段
                detectFieldConflict(local, remote)
            }
        }
    }
    
    private fun detectFieldConflict(
        local: VersionedItem,
        remote: VersionedItem
    ): Conflict {
        val localData = local.data
        val remoteData = remote.data
        
        val conflictingFields = mutableListOf<String>()
        
        // 字段级冲突检测
        localData.fields.forEach { (key, value) ->
            if (remoteData.fields[key] != value) {
                conflictingFields.add(key)
            }
        }
        
        return Conflict(
            id = UUID.randomUUID().toString(),
            entityType = local.entityType,
            entityId = local.id,
            localVersion = local.version,
            remoteVersion = remote.version,
            conflictingFields = conflictingFields,
            localData = localData,
            remoteData = remoteData,
            detectedAt = System.currentTimeMillis()
        )
    }
}
```

#### 冲突解决策略

```kotlin
sealed class ConflictResolution {
    object KeepLocal : ConflictResolution()
    object KeepRemote : ConflictResolution()
    data class Merge(val mergedData: ItemData) : ConflictResolution()
    data class Manual(val conflictId: String) : ConflictResolution()
}

class ConflictResolver {
    
    fun resolve(conflict: Conflict, strategy: ConflictResolution): ResolutionResult {
        return when (strategy) {
            is ConflictResolution.KeepLocal -> {
                ResolutionResult(
                    winner = "local",
                    version = conflict.localVersion + 1,
                    data = conflict.localData
                )
            }
            
            is ConflictResolution.KeepRemote -> {
                ResolutionResult(
                    winner = "remote",
                    version = conflict.remoteVersion + 1,
                    data = conflict.remoteData
                )
            }
            
            is ConflictResolution.Merge -> {
                ResolutionResult(
                    winner = "merged",
                    version = maxOf(conflict.localVersion, conflict.remoteVersion) + 1,
                    data = strategy.mergedData
                )
            }
            
            is ConflictResolution.Manual -> {
                // 保存冲突，等待用户手动解决
                ResolutionResult(
                    winner = "pending",
                    conflictId = conflict.id,
                    requiresUserAction = true
                )
            }
        }
    }
    
    // 自动解决策略：LWW（最后写入优先）
    fun autoResolve(conflict: Conflict): ConflictResolution {
        return if (conflict.localData.lastModified >= conflict.remoteData.lastModified) {
            ConflictResolution.KeepLocal
        } else {
            ConflictResolution.KeepRemote
        }
    }
    
    // 字段级合并策略
    fun mergeFields(local: ItemData, remote: ItemData): ItemData {
        val mergedFields = mutableMapOf<String, String>()
        
        // 非冲突字段直接合并
        val allFields = (local.fields.keys + remote.fields.keys).toSet()
        val conflictFields = detectConflictingFields(local, remote)
        
        for (field in allFields) {
            mergedFields[field] = when {
                field !in conflictFields -> {
                    // 无冲突，取较新版本
                    if (local.fields[field] != remote.fields[field]) {
                        // 简单策略：取远程（可优化为取更新者）
                        remote.fields[field] ?: local.fields[field]
                    } else {
                        local.fields[field]!!
                    }
                }
                local.fields[field] != null -> local.fields[field]!!
                else -> remote.fields[field]!!
            }
        }
        
        return local.copy(
            fields = mergedFields,
            lastModified = System.currentTimeMillis()
        )
    }
}
```

#### 版本历史（用于恢复）

```kotlin
class VersionHistoryManager @Inject constructor(
    private val dao: OperationLogDao
) {
    
    // 记录操作
    suspend fun logOperation(
        listId: String,
        itemId: String?,
        operation: OperationType,
        oldData: ItemData?,
        newData: ItemData?,
        deviceId: String
    ) {
        dao.insert(OperationLog(
            listId = listId,
            itemId = itemId,
            operation = operation,
            oldData = oldData?.let { Json.encodeToString(it) },
            newData = newData?.let { Json.encodeToString(it) },
            deviceId = deviceId,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    // 获取历史版本
    suspend fun getHistory(
        listId: String,
        itemId: String,
        limit: Int = 20
    ): List<OperationLog> {
        return dao.getHistory(listId, itemId, limit)
    }
    
    // 恢复到指定版本
    suspend fun restoreToVersion(
        listId: String,
        itemId: String,
        targetLogId: Long
    ): ItemData? {
        val targetLog = dao.getById(targetLogId) ?: return null
        
        val oldData = targetLog.oldData?.let { 
            Json.decodeFromString<ItemData>(it) 
        } ?: return null
        
        // 创建恢复操作
        logOperation(
            listId = listId,
            itemId = itemId,
            operation = OperationType.RESTORE,
            oldData = null,
            newData = oldData,
            deviceId = "restore_operation"
        )
        
        return oldData
    }
    
    // 清理旧日志（保留最近 N 天）
    suspend fun cleanupOldLogs(keepDays: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000)
        dao.deleteBefore(cutoffTime)
    }
}
```

---

## 5. 安全设计

### 5.1 认证流程

#### Firebase Authentication 集成

```kotlin
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val datastore: DataStore
) {
    
    // 匿名登录
    suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = auth.signInAnonymously().await()
            val idToken = result.user?.getIdToken(false) ?: throw AuthException("No token")
            
            AuthResult(
                uid = result.user!!.uid,
                idToken = idToken,
                isAnonymous = true,
                expiresAt = System.currentTimeMillis() + 3600 * 1000
            )
        } catch (e: Exception) {
            throw AuthException("Anonymous sign-in failed: ${e.message}")
        }
    }
    
    // 账号绑定（匿名 → 邮箱）
    suspend fun linkEmailCredential(
        email: String,
        password: String
    ): AuthResult {
        val currentUser = auth.currentUser ?: throw AuthException("No user")
        
        return try {
            val credential = EmailAuthProvider.getCredential(email, password)
            val result = currentUser.linkWithCredential(credential).await()
            
            AuthResult(
                uid = result.user!!.uid,  // UID 保持不变
                idToken = result.user.getIdToken(false)!!,
                isAnonymous = false,
                email = email
            )
        } catch (e: FirebaseAuthUserCollisionException) {
            // 邮箱已被使用，需要合并账号
            handleAccountMerge(currentUser, email, password)
        }
    }
    
    // 账号绑定（匿名 → Google）
    suspend fun linkGoogleCredential(
        idToken: String
    ): AuthResult {
        val currentUser = auth.currentUser ?: throw AuthException("No user")
        
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = currentUser.linkWithCredential(credential).await()
            
            AuthResult(
                uid = result.user!!.uid,
                idToken = result.user.getIdToken(false)!!,
                isAnonymous = false,
                email = result.user.email
            )
        } catch (e: Exception) {
            throw AuthException("Google linking failed: ${e.message}")
        }
    }
    
    // Token 刷新
    suspend fun refreshIdToken(): String {
        val currentUser = auth.currentUser ?: throw AuthException("No user")
        return currentUser.getIdToken(true)!!
    }
}
```

#### 认证状态管理

```kotlin
sealed class AuthState {
    object Initializing : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: UserAccount) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel @Inject constructor(
    private val authManager: FirebaseAuthManager,
    private val repository: UserRepository
) : ViewModel() {
    
    val authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    
    init {
        // 监听 Firebase 认证状态变化
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            viewModelScope.launch {
                authState.value = if (user != null) {
                    val account = repository.getUser(user.uid)
                    if (account != null) {
                        AuthState.Authenticated(account)
                    } else {
                        // 本地无用户数据，创建新记录
                        val newAccount = createAccountFromFirebase(user)
                        repository.insert(newAccount)
                        AuthState.Authenticated(newAccount)
                    }
                } else {
                    AuthState.Unauthenticated
                }
            }
        }
    }
    
    suspend fun signInAnonymously() {
        try {
            val result = authManager.signInAnonymously()
            // 创建本地用户记录
            val account = UserAccount(
                uid = result.uid,
                isAnonymous = true,
                email = null,
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )
            repository.insert(account)
        } catch (e: Exception) {
            authState.value = AuthState.Error(e.message!!)
        }
    }
}
```

---

### 5.2 授权模型

#### Firestore 安全规则

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // 辅助函数：检查用户是否认证
    function isAuthenticated() {
      return request.auth != null;
    }
    
    // 辅助函数：获取用户 UID
    function getUid() {
      return request.auth.uid;
    }
    
    // 辅助函数：检查是否为列表所有者
    function isListOwner(listId) {
      return get(/databases/$(database)/documents/lists/$(listId)).data.owner.uid == getUid();
    }
    
    // 辅助函数：检查是否为协作者
    function isCollaborator(listId, requiredPermission) {
      let collab = get(/databases/$(database)/documents/lists/$(listId)/collaborators/$(getUid())).data;
      return collab != null 
             && collab.status == 'ACCEPTED'
             && (requiredPermission == 'VIEW' 
                 || (requiredPermission == 'COMMENT' && (collab.permission == 'COMMENT' || collab.permission == 'EDIT'))
                 || (requiredPermission == 'EDIT' && collab.permission == 'EDIT'));
    }
    
    // 辅助函数：检查列表可见性
    function isListVisible(listId) {
      let list = get(/databases/$(database)/documents/lists/$(listId)).data;
      return list.visibility == 'PUBLIC' 
             || list.visibility == 'SHARED' && isCollaborator(listId, 'VIEW')
             || isListOwner(listId);
    }
    
    // users 集合：用户只能读写自己的数据
    match /users/{userId} {
      allow read, write: if isAuthenticated() && getUid() == userId;
    }
    
    // lists 集合
    match /lists/{listId} {
      // 创建：必须认证
      allow create: if isAuthenticated() 
                    && request.resource.data.owner.uid == getUid();
      
      // 读取：所有者、协作者、或公开列表
      allow read: if isListVisible(listId);
      
      // 更新：所有者或有编辑权限的协作者
      allow update: if isListOwner(listId) 
                    || isCollaborator(listId, 'EDIT');
      
      // 删除：仅所有者
      allow delete: if isListOwner(listId);
      
      // items 子集合
      match /items/{itemId} {
        allow create: if isListOwner(listId) || isCollaborator(listId, 'EDIT');
        allow read: if isListVisible(listId);
        allow update: if isListOwner(listId) || isCollaborator(listId, 'EDIT');
        allow delete: if isListOwner(listId) || isCollaborator(listId, 'EDIT');
      }
      
      // collaborators 子集合
      match /collaborators/{userId} {
        allow read: if isListOwner(listId) || userId == getUid();
        allow create, update: if isListOwner(listId);
        allow delete: if isListOwner(listId) || userId == getUid();
      }
    }
    
    // forks 集合
    match /forks/{forkId} {
      allow read: if isAuthenticated() 
                  && (resource.data.forkedById == getUid() 
                      || isListVisible(resource.data.originalListId));
      allow create: if isAuthenticated() 
                    && request.resource.data.forkedById == getUid();
      allow update, delete: if isAuthenticated() 
                            && resource.data.forkedById == getUid();
    }
    
    // share_access 集合：公开访问
    match /share_access/{shareToken} {
      allow read: if true;  // 公开
      allow write: if false;  // 仅服务器端写入
    }
    
    // 拒绝其他所有未明确允许的访问
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

#### 分享 Token 验证

```kotlin
class ShareTokenManager @Inject constructor(
    private val crypto: CryptoProvider
) {
    
    // 生成分享 Token
    fun generateToken(listId: String, ownerId: String): String {
        val payload = ShareTokenPayload(
            listId = listId,
            ownerId = ownerId,
            createdAt = System.currentTimeMillis(),
            nonce = SecureRandom().nextLong()
        )
        
        // HMAC-SHA256 签名
        val signature = crypto.sign(payload.toJson(), shareTokenSecret)
        
        // Base64 编码
        return Base64.encodeToString(
            "${payload.toJson()}:${signature}".toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
    }
    
    // 验证分享 Token
    fun verifyToken(token: String): ShareTokenPayload? {
        return try {
            val decoded = Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP)
            val parts = String(decoded).split(":")
            
            if (parts.size != 2) return null
            
            val payload = Json.decodeFromString<ShareTokenPayload>(parts[0])
            val signature = parts[1]
            
            // 验证签名
            if (!crypto.verify(payload.toJson(), signature, shareTokenSecret)) {
                return null
            }
            
            // 检查过期
            if (payload.expiresAt != null && 
                System.currentTimeMillis() > payload.expiresAt) {
                return null
            }
            
            payload
        } catch (e: Exception) {
            null
        }
    }
    
    // 验证访问权限
    suspend fun verifyAccess(
        token: String,
        listId: String,
        requesterUid: String?
    ): AccessResult {
        val payload = verifyToken(token) ?: return AccessResult.Denied("Invalid token")
        
        if (payload.listId != listId) {
            return AccessResult.Denied("Token mismatch")
        }
        
        // 检查列表是否存在且未删除
        val list = listRepository.getList(listId) ?: return AccessResult.Denied("List not found")
        if (list.isDeleted) return AccessResult.Denied("List deleted")
        
        // 检查过期
        if (list.shareExpiresAt != null && 
            System.currentTimeMillis() > list.shareExpiresAt) {
            return AccessResult.Denied("Share expired")
        }
        
        // 返回访问权限
        return when (list.visibility) {
            Visibility.PUBLIC -> AccessResult.Granted(
                canView = true,
                canEdit = false,
                canFork = true
            )
            Visibility.SHARED -> {
                // 检查是否为协作者
                val collaborator = collaboratorRepository.get(listId, requesterUid ?: "")
                if (collaborator != null && collaborator.status == CollaboratorStatus.ACCEPTED) {
                    AccessResult.Granted(
                        canView = true,
                        canEdit = collaborator.permission == Permission.EDIT,
                        canFork = true
                    )
                } else {
                    AccessResult.Denied("Not a collaborator")
                }
            }
            Visibility.PRIVATE -> AccessResult.Denied("List is private")
        }
    }
}

data class ShareTokenPayload(
    val listId: String,
    val ownerId: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val nonce: Long
) {
    fun toJson(): String = Json.encodeToString(this)
    
    companion object {
        fun fromJson(json: String): ShareTokenPayload = 
            Json.decodeFromString(json)
    }
}

sealed class AccessResult {
    data class Granted(
        val canView: Boolean,
        val canEdit: Boolean,
        val canFork: Boolean
    ) : AccessResult()
    
    data class Denied(val reason: String) : AccessResult()
}
```

---

### 5.3 数据加密

#### 传输加密

- ✅ 所有通信使用 HTTPS/TLS 1.3
- ✅ Firebase SDK 自动处理 Token 加密
- ✅ 分享链接 Token 使用 HMAC-SHA256 签名

#### 本地数据加密

```kotlin
class LocalEncryptionManager @Inject constructor(
    private val preferences: EncryptedSharedPreferences
) {
    
    // 使用 Android Keystore 生成主密钥
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    // 加密敏感数据
    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray())
        
        // IV + 密文
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }
    
    // 解密数据
    fun decrypt(encryptedData: String): String {
        val decoded = Base64.decode(encryptedData, Base64.NO_WRAP)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, decoded.copyOfRange(0, 12))
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)
        
        val decrypted = cipher.doFinal(decoded.copyOfRange(12, decoded.size))
        return String(decrypted)
    }
    
    private fun getMasterKey(): javax.crypto.SecretKey {
        // 从 Android Keystore 获取
        return KeyStore.getInstance("AndroidKeyStore")
            .load(null)
            .getKey("listapp_master_key", null) as javax.crypto.SecretKey
    }
}

// Room 类型转换器：自动加密/解密
class EncryptedTypeConverters(
    private val encryptionManager: LocalEncryptionManager
) {
    
    @TypeConverter
    fun encryptShareToken(token: String?): String? {
        return token?.let { encryptionManager.encrypt(it) }
    }
    
    @TypeConverter
    fun decryptShareToken(encrypted: String?): String? {
        return encrypted?.let { encryptionManager.decrypt(it) }
    }
}
```

#### 敏感字段加密

| 字段 | 加密方式 | 说明 |
|------|----------|------|
| shareToken | AES-256-GCM | 本地存储时加密 |
| user email | AES-256-GCM | 可选，增强隐私 |
| 协作者邮箱 | AES-256-GCM | 可选，增强隐私 |
| 操作日志 | 不加密 | 仅本地，用于调试 |

---

## 6. 实施计划

### 6.1 Sprint 规划总览

```
┌─────────────────────────────────────────────────────────────┐
│  Phase 2 实施计划（预计 10-12 周）                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Sprint 1: 基础架构（2 周）                                    │
│  ├── Firebase 项目搭建                                       │
│  ├── 认证系统实现                                            │
│  └── 数据模型迁移                                            │
│                                                              │
│  Sprint 2: 同步引擎核心（3 周）                                │
│  ├── 增量同步实现                                            │
│  ├── 冲突检测与解决                                          │
│  └── 离线模式支持                                            │
│                                                              │
│  Sprint 3: 分享功能（2 周）                                    │
│  ├── 分享链接生成                                            │
│  ├── 权限控制                                                │
│  └── Fork 功能                                               │
│                                                              │
│  Sprint 4: 协作功能（2 周）                                    │
│  ├── 协作者邀请                                              │
│  ├── 权限管理                                                │
│  └── 实时协作同步                                            │
│                                                              │
│  Sprint 5: 测试与优化（2-3 周）                                │
│  ├── 多设备测试                                              │
│  ├── 边界场景测试                                            │
│  ├── 性能优化                                                │
│  └── 安全审计                                                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

### 6.2 Sprint 1: 基础架构

**时间：** 2 周  
**目标：** 完成 Firebase 集成和认证系统

#### 任务列表

| ID | 任务 | 优先级 | 估算工时 | 依赖 |
|----|------|--------|----------|------|
| S1-T1 | Firebase 项目创建与配置 | Must | 4h | - |
| S1-T2 | Firestore 数据库初始化 | Must | 4h | S1-T1 |
| S1-T3 | Firebase Authentication 集成 | Must | 8h | S1-T1 |
| S1-T4 | 匿名登录实现 | Must | 8h | S1-T3 |
| S1-T5 | 邮箱/Google 账号绑定 | Must | 12h | S1-T4 |
| S1-T6 | Room 数据库迁移（v1→v2） | Must | 8h | - |
| S1-T7 | 新增 Entity 实现 | Must | 8h | S1-T6 |
| S1-T8 | 认证状态管理（ViewModel） | Must | 8h | S1-T4 |
| S1-T9 | Token 自动刷新机制 | Must | 4h | S1-T4 |
| S1-T10 | 单元测试：认证流程 | Should | 8h | S1-T5 |

#### 交付物

- ✅ Firebase 项目配置完成
- ✅ 用户可匿名登录
- ✅ 支持账号绑定（邮箱/Google）
- ✅ 本地数据库迁移完成
- ✅ 认证状态 UI 集成

---

### 6.3 Sprint 2: 同步引擎核心

**时间：** 3 周  
**目标：** 实现完整的增量同步和冲突处理

#### 任务列表

| ID | 任务 | 优先级 | 估算工时 | 依赖 |
|----|------|--------|----------|------|
| S2-T1 | Change 队列实现 | Must | 12h | S1-T7 |
| S2-T2 | 增量同步逻辑（Push） | Must | 16h | S2-T1 |
| S2-T3 | 增量同步逻辑（Pull） | Must | 16h | S2-T1 |
| S2-T4 | 版本向量（Vector Clock）实现 | Must | 8h | - |
| S2-T5 | 冲突检测算法 | Must | 12h | S2-T4 |
| S2-T6 | 冲突解决策略（LWW） | Must | 8h | S2-T5 |
| S2-T7 | 手动冲突解决 UI | Should | 12h | S2-T6 |
| S2-T8 | 版本历史记录 | Should | 8h | S2-T1 |
| S2-T9 | 离线模式支持 | Must | 16h | S2-T2 |
| S2-T10 | 同步状态 UI 指示器 | Must | 8h | S2-T2 |
| S2-T11 | 批量合并优化 | Could | 8h | S2-T1 |
| S2-T12 | 单元测试：同步场景 | Must | 16h | S2-T3 |
| S2-T13 | 集成测试：多设备同步 | Must | 12h | S2-T9 |

#### 交付物

- ✅ 多设备实时同步（<5 秒延迟）
- ✅ 离线操作完整支持
- ✅ 自动冲突解决（LWW）
- ✅ 同步状态 UI 反馈
- ✅ 版本历史可查询

---

### 6.4 Sprint 3: 分享功能

**时间：** 2 周  
**目标：** 实现分享链接生成和 Fork 功能

#### 任务列表

| ID | 任务 | 优先级 | 估算工时 | 依赖 |
|----|------|--------|----------|------|
| S3-T1 | 分享 Token 生成算法 | Must | 8h | S1-T3 |
| S3-T2 | 分享配置数据模型 | Must | 4h | S1-T7 |
| S3-T3 | 分享链接生成 API | Must | 8h | S3-T1 |
| S3-T4 | 分享链接验证逻辑 | Must | 8h | S3-T1 |
| S3-T5 | 公开列表访问接口 | Must | 8h | S3-T4 |
| S3-T6 | 分享 UI（生成/复制链接） | Must | 8h | S3-T3 |
| S3-T7 | 二维码生成 | Should | 4h | S3-T3 |
| S3-T8 | 分享过期时间设置 | Should | 4h | S3-T3 |
| S3-T9 | Fork 功能实现 | Must | 12h | S3-T5 |
| S3-T10 | Fork 关联选项 | Should | 8h | S3-T9 |
| S3-T11 | 分享权限管理 UI | Should | 8h | S3-T3 |
| S3-T12 | 单元测试：分享流程 | Must | 8h | S3-T5 |

#### 交付物

- ✅ 一键生成分享链接
- ✅ 公开/指定人/私有三种可见性
- ✅ 分享链接过期支持
- ✅ Fork 功能（独立副本）
- ✅ 二维码分享

---

### 6.5 Sprint 4: 协作功能

**时间：** 2 周  
**目标：** 实现协作者邀请和权限管理

#### 任务列表

| ID | 任务 | 优先级 | 估算工时 | 依赖 |
|----|------|--------|----------|------|
| S4-T1 | 协作者数据模型 | Must | 4h | S1-T7 |
| S4-T2 | 邀请协作者 API | Must | 12h | S4-T1 |
| S4-T3 | 邀请通知（邮件/推送） | Should | 12h | S4-T2 |
| S4-T4 | 接受/拒绝邀请逻辑 | Must | 8h | S4-T2 |
| S4-T5 | 协作者权限验证 | Must | 8h | S4-T1 |
| S4-T6 | 移除协作者功能 | Must | 4h | S4-T1 |
| S4-T7 | 协作者管理 UI | Must | 12h | S4-T2 |
| S4-T8 | 实时协作同步优化 | Could | 16h | S2-T3 |
| S4-T9 | 协作冲突提示 | Could | 8h | S2-T6 |
| S4-T10 | 单元测试：协作场景 | Must | 8h | S4-T5 |

#### 交付物

- ✅ 通过邮箱邀请协作者
- ✅ 三种权限级别（查看/评论/编辑）
- ✅ 协作者管理界面
- ✅ 权限验证完整

---

### 6.6 Sprint 5: 测试与优化

**时间：** 2-3 周  
**目标：** 全面测试、性能优化、安全审计

#### 任务列表

| ID | 任务 | 优先级 | 估算工时 | 依赖 |
|----|------|--------|----------|------|
| S5-T1 | 多设备同步测试（5+ 设备） | Must | 16h | S2-T13 |
| S5-T2 | 离线场景测试 | Must | 12h | S2-T9 |
| S5-T3 | 冲突场景测试（边界用例） | Must | 12h | S2-T6 |
| S5-T4 | 分享链接安全测试 | Must | 8h | S3-T4 |
| S5-T5 | 权限绕过测试 | Must | 8h | S4-T5 |
| S5-T6 | 性能分析（启动/同步） | Must | 12h | - |
| S5-T7 | 数据库查询优化 | Should | 8h | S5-T6 |
| S5-T8 | 网络请求优化（缓存/重试） | Should | 12h | S5-T6 |
| S5-T9 | 内存泄漏检测 | Must | 8h | - |
| S5-T10 | 电池消耗优化 | Should | 8h | S5-T6 |
| S5-T11 | 安全审计（代码/规则） | Must | 16h | - |
| S5-T12 | Firestore 安全规则审查 | Must | 8h | S5-T11 |
| S5-T13 | Bug 修复 | Must | 24h | S5-T1~T5 |
| S5-T14 | 文档完善 | Should | 8h | - |
| S5-T15 | 验收测试（按用户故事） | Must | 16h | S5-T13 |

#### 交付物

- ✅ 所有 Must Have 用户故事验收通过
- ✅ 多设备同步延迟 < 5 秒
- ✅ 无数据丢失场景
- ✅ 安全测试通过
- ✅ 性能指标达标

---

### 6.7 依赖关系图

```
Sprint 1 (基础架构)
    │
    ├── Firebase 配置 ──────────────────────────────┐
    │                                               │
    └── 认证系统 ───────────────┬───────────────────┤
                                │                   │
Sprint 2 (同步引擎)             │                   │
    │                           │                   │
    ├── 数据模型 ◄──────────────┘                   │
    │                                               │
    ├── 同步逻辑 ───────────────┬───────────────────┤
    │                           │                   │
    └── 冲突处理                │                   │
                                │                   │
Sprint 3 (分享功能)             │                   │
    │                           │                   │
    ├── Token 生成 ◄────────────┘                   │
    │                                               │
    ├── 分享链接 ───────────────┼───────────────────┤
    │                           │                   │
    └── Fork 功能               │                   │
                                │                   │
Sprint 4 (协作功能)             │                   │
    │                           │                   │
    ├── 协作者模型 ◄────────────┘                   │
    │                                               │
    └── 权限验证 ◄──────────────┴───────────────────┘
    
Sprint 5 (测试与优化)
    │
    └── 依赖 Sprint 1-4 全部完成
```

---

### 6.8 风险与缓解

| 风险 | 影响 | 概率 | 缓解方案 |
|------|------|------|----------|
| Firebase 国内访问不稳定 | 高 | 中 | 1. 准备 Supabase 备选方案<br>2. 增加重试和降级逻辑<br>3. 考虑国内 CDN |
| 同步冲突频繁导致体验差 | 中 | 低 | 1. 优化冲突检测算法<br>2. 增加字段级合并<br>3. 提供清晰的用户提示 |
| 分享链接被滥用/爬取 | 中 | 中 | 1. Token 加密 + 签名<br>2. 访问频率限制<br>3. 可选验证码 |
| 匿名用户数据丢失（卸载 App） | 高 | 低 | 1. 强提醒绑定账号<br>2. 提供导出备份功能<br>3. 首次启动明确告知风险 |
| 多设备测试环境不足 | 中 | 高 | 1. 使用 Firebase Test Lab<br>2. 模拟器 + 真机结合<br>3. 招募内测用户 |
| Sprint 延期 | 中 | 中 | 1. 优先级排序（Must/Should/Could）<br>2. 每周进度回顾<br>3. 必要时削减 Could 功能 |

---

## 7. 附录

### 7.1 关键指标（KPI）

| 指标 | 目标值 | 测量方式 |
|------|--------|----------|
| 同步延迟（网络正常） | < 5 秒 | 设备 A 修改 → 设备 B 可见 |
| 同步成功率 | > 99% | 成功同步次数 / 总同步次数 |
| 冲突解决率 | > 95% 自动解决 | 自动解决冲突 / 总冲突数 |
| 离线功能可用性 | 100% | 无网络时核心功能可用 |
| 分享链接打开率 | > 90% | 成功打开 / 总点击 |
| App 启动时间 | < 2 秒 | 冷启动到首页可交互 |

### 7.2 技术决策记录（ADR）

| ADR 编号 | 主题 | 决策 | 日期 |
|----------|------|------|------|
| ADR-001 | 后端方案选择 | Firebase 为主，Supabase 备选 | 2026-03-01 |
| ADR-002 | 同步策略 | 乐观同步 + LWW 冲突解决 | 2026-03-01 |
| ADR-003 | 认证方案 | 匿名登录优先，支持账号升级 | 2026-03-01 |
| ADR-004 | 版本控制 | 版本向量 + 时间戳混合 | 2026-03-01 |

### 7.3 参考资源

- [Firebase Firestore 离线数据](https://firebase.google.com/docs/firestore/manage-data/enable-offline)
- [Firebase Authentication](https://firebase.google.com/docs/auth)
- [Firestore 安全规则](https://firebase.google.com/docs/firestore/security/get-started)
- [Vector Clock 算法](https://en.wikipedia.org/wiki/Vector_clock)
- [CRDT 基础](https://crdt.tech/)（未来优化方向）

---

**文档状态：** ✅ 完成  
**文档位置：** `/home/node/.openclaw/workspace/list-app/docs/phase2-architecture.md`  
**下一步：** 技术评审会议 → 后端搭建 → 同步引擎开发
