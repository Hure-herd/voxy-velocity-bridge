# Voxy Velocity Bridge (VVB)

> Voxy Velocity Bridge 是一个用于 Velocity 多子服网络的 Voxy 兼容附属，通过将 Velocity 后端服务器逻辑 ID 注入 Voxy 世界唯一标识，使玩家能够在同一代理连接中使用 `/server` 无缝切换不同后端，同时保证每个子服拥有完全独立且可持久恢复的 Voxy LOD 数据。

## 1. 解决的问题

Voxy 在普通单服环境下，可以根据 Minecraft 世界信息识别并保存对应的 LOD 数据。但在 Velocity 多子服环境中，客户端对外连接的是同一个代理地址，通过 `/server` 切换后端时，Voxy 不一定知道「现在已经不是原来的后端服务器了」。

当两个后端满足类似条件时：

```text
dimension key = minecraft:overworld
biome seed = 相同
proxy address = 相同
```

Voxy 会将它们识别为同一个世界，导致：

- 一个服的远景出现在另一个服（串服）
- LOD 数据串用、幽灵区块
- 切服时打开错误的 Voxy 数据库
- 数据库锁冲突、需要清缓存才能恢复
- 同坐标但完全不同的两个世界相互污染

## 2. 设计目标

让 Voxy 的世界身份从：

```text
Proxy Address + Dimension + Seed
```

扩展为逻辑上的：

```text
Proxy Namespace + Backend Namespace + Dimension + Seed
```

即使两个服务器 seed 完全相同、维度相同、坐标相同、代理地址相同，也始终属于两个完全独立的 Voxy 世界。

**非目标**：不重写 Voxy、不修改渲染/LOD 算法、不修改世界格式、不替代 Velocity、不修改 seed、不重新打包 Voxy。项目是 Voxy 的独立兼容附属。

## 3. 架构

```text
 Velocity
   │
 VVB Velocity Plugin
   │  当前 Backend Server ID
   │  Custom Payload (vvb:backend)
   ▼
 Minecraft Client
   │
 VVB Fabric Mod
   │
 Voxy Adapter
   ▼
 Voxy
```

- **Velocity 插件**（`vvb-velocity`）：获取玩家当前后端，监听切换，通过自定义 payload 通道把后端逻辑 ID 发送给客户端
- **Fabric 模组**（`voxy-velocity-bridge`）：接收 backend ID，维护状态机，在 Voxy 创建 WorldIdentifier 前注入 backend namespace，切服时正确重建渲染器

## 4. 通信协议

自定义 payload channel：`vvb:backend`

双阶段协议：

| 阶段 | 触发时机 | 客户端动作 |
|---|---|---|
| PREPARE (0x01) | 切换前（ServerPreConnectEvent） | 记录 `pendingBackend` |
| CONFIRM (0x02) | 切换成功后（ServerPostConnectEvent） | 确认 `currentBackend` / `worldBackend` |

字节布局：`1 byte phase + UTF-8 backendName`

Backend ID 使用 **Velocity RegisteredServer Name**（如 `survival`、`creative`），而不是 IP/端口——服务器地址可能变化，逻辑服务器名更稳定。

## 5. 世界隔离原理

### 5.1 World ID 设计

参与世界唯一标识的数据：

```text
proxyNamespace + backendNamespace + dimensionKey + dimensionType + biomeSeed
```

例如 `vc|creative|minecraft:overworld|minecraft:overworld|123456789`，经 SHA-256 得到最终稳定 World ID。backend 不同 → Hash 不同 → Voxy 数据库不同。

### 5.2 实现方式（Mixin 注入）

在 `WorldIdentifier.of(Level)` 返回处注入（唯一入口收敛）：有 backend 时 `biomeSeed ^= mixStafford13(backend.hashCode())`，一处注入同时隔离：

- **磁盘身份**：World ID（数据库目录）
- **内存身份**：hashCode/equals（引擎缓存复用判断）

### 5.3 渲染器重建

切服后强制 `shutdownRenderer + setWorld + createRenderer`，让渲染器重新绑定当前 backend 对应的引擎（Voxy 的 `IVoxyRenderSystemHolder` 接口）。

## 6. 切服时序

双阶段协议避免错误时序：

```text
/server creative
  → Velocity 捕获 ServerPreConnectEvent
  → 发送 PREPARE (backend = creative)
  → 客户端记录 pendingBackend = creative
  → 旧 ClientLevel 卸载，新 ClientLevel 创建
  → Voxy 创建新的 WorldIdentifier（读取 backend = creative）
  → Velocity 发送 CONFIRM
  → 客户端确认 currentBackend = creative，重建渲染器
```

客户端状态机：`currentBackend` / `pendingBackend` / `worldBackend`，退出时全部置空。

## 7. 回退策略（Fail-Safe）

没有收到 Velocity 插件 payload 时 `backendNamespace = null`，完全走 Voxy 原始行为。模组可同时用于：普通单服、Velocity 网络、本地世界、LAN、未安装插件的服务器。

Voxy 缺失或版本不兼容时自动静默回退，不影响正常游戏。

## 8. 数据目录

```text
.voxy/
└── saves/
    └── <proxy>/
        ├── <backend A>/   ← 8ac0319...（hash 目录）
        └── <backend B>/   ← d42f617...
```

每个 backend 独立目录，切回原服自动恢复原缓存。第一版不自动迁移旧缓存（旧缓存无法可靠判断来源，迁移可能污染新目录）。

## 9. 构建

要求：JDK 25（Fabric 模组）、Java 17+ 运行环境。

```bash
# Fabric 客户端模组
cd fabric
./gradlew build          # 产物: fabric/build/libs/voxy-velocity-bridge-1.0.0.jar

# Velocity 插件
cd velocity
./gradlew build          # 产物: velocity/build/libs/vvb-velocity-1.0.0.jar
```

## 10. 部署

### Velocity 代理

- 将 `vvb-velocity-1.0.0.jar` 放入 `plugins/` 目录
- 在 `velocity.toml` 中注册后端服务器，注册名即 backend 标识（建议语义化，如 `survival`/`creative`）

### 客户端

`mods/` 目录放入：

- `voxy-velocity-bridge-1.0.0.jar`（本模组）
- `voxy`（需 26.2 兼容版本）
- 依赖：`fabric-api`、`fabric-language-kotlin`

### 后端服务器

无需任何修改。插件只转发后端注册名，不注入后端服务器。

## 11. 验证

1. 客户端通过代理进入 Survival，等待 Voxy 地形生成
2. `/server creative` 切换
3. 日志出现 `[VVB] survival -> creative`（切服确认）与 `[VVB] Voxy renderer rebuilt, backend = creative`
4. 画面应切换到 Creative 的地形；切回 Survival 后恢复原 LOD

验收标准：同 seed / 同维度 / 同坐标不串服；多次快速切服不崩溃；无数据库 LOCK；不需要手动清缓存；无插件时 Voxy 正常工作。

## 12. 兼容性与已知限制

| 维度 | 说明 |
|---|---|
| 后端服务器 | 完全通用（原版/Fabric/Paper 均可，无需改后端） |
| 代理 | 仅 Velocity；换 BungeeCord 需重写插件，payload 协议与客户端不变 |
| Minecraft 版本 | 26.2 专用（StreamCodec/Identifier API + 对应 Voxy 版本） |
| Voxy | 耦合 `WorldIdentifier` / `IVoxyRenderSystemHolder`（非稳定 API），升级 Voxy 需回归测试；耦合点收敛在 `VoxyAdapter` 与 `WorldIdentifierMixin` |

已知限制：

- 每个后端完全独立缓存（同一地图的两个后端不共享地形数据）
- 首次进入后端区域时 Voxy 需现场烘焙网格，加载较慢属正常现象

## License

Copyright (C) 2026 Hureherd

本项目基于 **GNU Lesser General Public License v3.0（LGPL-3.0-only）** 发布，详见 [LICENSE](LICENSE)。
