# Voxy Velocity Bridge (VVB)

通过 Velocity 代理,让客户端 Voxy LOD 渲染在不同后端服务器之间自动切换世界缓存。

## 解决的问题

用 Velocity 做多服代理时,客户端连接同一个入口(25577)在多个后端服务器之间切换
(`/server`)。Voxy 按 `WorldIdentifier.of(Level)` 计算世界 ID 并缓存地形渲染数据。
默认情况下两个后端即使 seed 相同,客户端也复用同一份 Voxy 缓存——切换后端时会
串世界(显示上一个服的地形)。

VVB 把"后端服务器身份"混入 Voxy 的世界 ID 计算:每个后端独立一套缓存目录,
切服时渲染器自动重建并加载对应后端的缓存。

## 架构

```
                    ┌─────────────────────────────┐
  玩家 (Fabric 客户端) │  vvb:backend payload 通道   │
  ── 25577 ────────► │  Velocity 代理 (VVB 插件)    │
                    │  PREPARE (切换前)             │
                    │  CONFIRM (切换后)             │
                    └──────────────┬──────────────┘
                         ┌─────────┴─────────┐
                    server1 (25565)    server2 (25566)
                         └───────────────────┘
```

### 组件

| 组件 | 目录 | 职责 |
|---|---|---|
| Velocity 插件 | `velocity/` | 监听 ServerPreConnect/ServerPostConnect,向玩家发送 PREPARE/CONFIRM payload |
| Fabric 客户端模组 | `fabric/` | 接收 payload 更新 backend 状态;Mixin 注入 Voxy 世界 ID;切服后重建渲染器 |

### 隔离原理

- Voxy 磁盘缓存路径 = `.voxy/<worldId>/`,`worldId = SHA-256(biomeSeed + key)`
- 内存引擎缓存(WorldIdentifier.hashCode/equals)= `key + biomeSeed + dimension`
- VVB 在 `WorldIdentifier.of(Level)` 返回处注入:有 backend 时 `biomeSeed ^= mixStafford13(backend.hashCode())`
  - 一处注入,同时隔离**磁盘目录 + 内存引擎 + 配置存储**
  - backend 为 null(直连无代理)时走 Voxy 原生逻辑,完全 fail-safe

## 构建

要求:JDK 25(客户端 mod)、Java 17+ 运行环境。

```bash
# Fabric 客户端模组
cd fabric
./gradlew build          # 产物: fabric/build/libs/voxy-velocity-bridge-1.0.0.jar

# Velocity 插件
cd velocity
./gradlew build          # 产物: velocity/build/libs/vvb-velocity-1.0.0.jar
```

## 部署

### 1. Velocity 代理(25577)

- 把 `vvb-velocity-1.0.0.jar` 放入 `plugins/` 目录
- `velocity.toml` 中注册后端服务器,名字即 backend 标识(建议语义化,如 `survival`/`creative`)

### 2. 客户端(26.2-Fabric)

`mods/` 目录放入:

- `voxy-velocity-bridge-1.0.0.jar`(本模组)
- `voxy-0.2.18-beta.jar`(Voxy,需 26.2 兼容版)
- 依赖:`fabric-api`、`fabric-language-kotlin`(Voxy 运行需要)

> Voxy 缺失或版本不兼容时,模组自动静默回退,不影响正常游戏。

### 3. 后端服务器

**无需任何修改**。插件只转发后端注册名,不注入后端服务器。

## 验证

1. 客户端通过代理(25577)进入 server1,等待 Voxy 地形生成
2. `/server server2` 切换
3. 观察日志:`[VVB] server1 -> server2`(切服确认)
4. 画面应切换到 server2 的地形(每个后端独立缓存,首次进入需重新生成)

日志关键字:

- `[VVB] channel vvb:backend registered` —— 插件加载成功
- `[VVB] xxx -> survival (PREPARE/CONFIRM)` —— 插件发送阶段
- `[VVB] Voxy renderer rebuilt, backend = survival` —— 客户端渲染器重建

## 兼容性

| 维度 | 说明 |
|---|---|
| 后端服务器 | 完全通用(原版/Fabric/Paper 均可,无需改后端) |
| 代理 | 仅 Velocity;换 BungeeCord 需重写插件,payload 协议与客户端不变 |
| Minecraft 版本 | 26.2 专用(StreamCodec/Identifier API + voxy 0.2.18-beta) |
| Voxy | 耦合 `commonImpl.WorldIdentifier` / `IVoxyRenderSystemHolder`(非稳定 API),升级 Voxy 需回归测试;耦合点收敛在 `VoxyAdapter` 与 `WorldIdentifierMixin` |

## 已知限制

- 每个后端完全独立缓存(同一地图的两个后端不共享地形数据,会各自生成一份)
- 首次进入后端区域时 Voxy 需现场烘焙网格,加载较慢属正常现象
- 渲染器重建依赖 Voxy 的 `IVoxyRenderSystemHolder` 接口,该接口为 Voxy mixin 实现,版本差异可能导致重建失效(此时会退化为原版 Voxy 行为)

## License

Copyright (C) 2026 Hureherd

This project is licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0-only)**.
See the [LICENSE](LICENSE) file for details.
