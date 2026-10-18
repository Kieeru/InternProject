# 第三周 Redis 与缓存总体设计和分日实施计划

> **执行要求：** 本计划获确认后，实施时逐日采用测试先行；每个可观察行为都先看到预期失败，再写最小生产代码，最后运行目标测试和完整 `mvn test`。未经用户明确授权，不提交、推送、合并或删除分支。

**目标：** 在不改变 MySQL 事实数据源地位、不破坏现有 Controller → Service → Repository 分层的前提下，为热门知识文档和用户权限增加可降级的 Redis 缓存，为工单增加短时间窗口的重复提交抑制，并留下可验证的命中率和故障证据。

**架构：** Service 保持业务编排者身份，同时依赖 MySQL Repository 和小而明确的缓存端口；Redis 实现放在独立 `cache` 边界，Controller 不直接接触 Redis。读链路采用缓存旁路，写链路以数据库提交成功为准，再在事务提交后删除缓存；Redis 失败只降低性能或短窗口防重能力，不把缓存提升为事实数据源。

**技术栈：** Java 21、Spring Boot 4.1.1、Spring Data Redis（Lettuce）、MyBatis 4.0.0、MySQL 8.4、JUnit 5、MockMvc。

**设计依据：** `AGENTS.md`、`README.md`、`docs/myplan.md`、`2026-10-15_handoff.md` 和 2026-10-15 实际代码/环境检查结果。本文件同时承担本周设计说明和实施计划，不另建重复规格文档。

**配套学习资料：** [第三周第 1～7 天自测题与参考答案](第三周_第1-7天自测题与参考答案.md)。第 1～4 天已完成开发、验证与学习问答；第 5～7 天先作为预习题，只有对应实现和验证完成后才能标记为已完成。

## 一、开始实施前的已验证基线

### 1. Git

- 当前分支：`codex/feat-redis-cache`。
- `HEAD`、本地 `main`、`origin/main` 和 `origin/HEAD` 均指向 `5b82e82 docs: 同步第三周分支状态`。
- 当前分支没有远端跟踪分支，`git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` 返回 `no upstream configured`。
- 检查时工作区干净；本计划文件是第三周首次新增内容。
- 本轮不提交、不推送、不合并、不创建或删除分支。

### 2. 代码和数据库

- 用户、文档、工单 Controller 只调用 Service；没有 Controller 直连 Repository 或数据库。
- Service 依赖 Repository 接口；MyBatis Repository、Mapper 接口和 XML 隐藏持久化细节。
- MySQL 仍是用户、文档、工单的持久化事实来源。
- 文档分页支持标题前缀、1 基页码、`size` 1～100、`created_at DESC, id DESC` 稳定排序；列表不返回正文。
- 工单分页支持状态与标题关键字组合筛选，保留稳定排序和摘要返回。
- 工单状态更新已有 `@Transactional` 和 `SELECT ... FOR UPDATE`，并发回归测试证明同一状态流转只能成功一次。
- `roles`、`permissions`、`user_roles`、`role_permissions` 表已经存在，但目前没有权限查询 Service/Repository，也没有登录鉴权；本周只能构建可复用的权限读取与缓存能力，不能提前实现第 6 周 RBAC。

### 3. 测试

- 2026-10-15 15:28（Asia/Shanghai）实际执行：`mvn test`。
- 结果：101 个测试，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
- MySQL 测试库连接和 schema 初始化实际可用。
- 仅出现 Mockito/Byte Buddy 动态加载 Java Agent 警告，不影响结果。

### 4. Redis 与本机启动条件

- 初次检查时 PATH 中没有 `redis-server` 或 `redis-cli`，也没有 Redis/Memurai Windows 服务、相关进程或 6379 监听；WSL 枚举返回 `E_ACCESSDENIED`。
- 用户随后启动 Docker Desktop，已验证 Docker Desktop 4.93.0、Engine 29.8.1 和 Docker Compose 5.5.1 可用。
- `compose.yaml` 已新增项目独立的 Redis 服务，镜像固定为 `redis:7.4-alpine@sha256:858f009f9709ce576febc734aa78b8f6d624b82571f9ddb6bda4377c833b3499`，宿主机只绑定 `127.0.0.1:6379`，并使用 `internproject_redis-data` 命名卷。
- 实际启动的 Redis 版本为 7.4.11，容器 `internproject-redis-1` 状态为 `healthy`，`PING` 返回 `PONG`。
- 实测持久化配置为默认 RDB 快照、AOF 关闭；淘汰策略为 `noeviction`。这些是当前开发实例的实际值，不代表后续生产配置。
- 结论：Docker Redis 环境门禁已通过；Spring Boot Docker Compose 集成仍关闭，应用不会自动管理容器。

## 二、总体取舍

### 1. 推荐方案

采用“显式缓存端口 + `StringRedisTemplate` + 缓存旁路”方案，而不是直接给 Service 方法加 `@Cacheable`。

原因：

- 显式端口可以表达命中、未命中、空值命中和 Redis 不可用四种状态。
- 空值缓存、TTL 随机抖动、热点 Key 重建锁、命中率和故障降级都需要比注解缓存更细的控制。
- 单元测试可用内存 Fake 或故障 Stub，不必为普通 Service 测试启动 Redis。
- Redis 细节不会泄漏到 Controller 或 MyBatis Repository。

### 2. 不采用的方案

- 不把 Redis 包装成 Repository 的主实现：这会模糊“缓存”和“事实数据源”的区别。
- 不缓存文档分页结果：查询组合和页码会产生高基数 Key，任意新增或更新都需要大范围失效，本周收益低于复杂度。
- 不使用布隆过滤器：当前数据量小，删除和重建布隆过滤器会增加维护成本；本周使用短 TTL 空值缓存即可证明穿透治理。
- 不引入 Redisson：当前只需掌握 `SET key token NX PX` 和安全释放的基本原理，Spring Data Redis 已足够。
- 不引入 Actuator、Prometheus、Spring Cloud、Kafka、认证或 MinIO；这些属于后续阶段。
- 不使用 Redis 全局 `keyspace_hits/keyspace_misses` 代替业务指标：全局统计会混入权限、锁和其他客户端操作，不能准确表示各缓存区域的效果。
- 不使用非官方、停止维护的“嵌入式 Redis”测试库；真实 Redis 集成测试依赖经确认的本机服务。

### 3. 本周需要用户确认后才实施的公开行为

计划确认即代表确认以下两项最小公开行为：

1. 新增 `PUT /documents/{id}`，用于真实证明“先更新数据库，提交后删除缓存”；成功返回 HTTP 200，不存在返回 HTTP 404，继续使用中文消息和 `ApiResponse<T>`。
2. 工单重复提交采用“相同标题 + 相同描述在 10 秒窗口内视为重复”的教学版规则；重复请求返回 HTTP 409 和稳定错误码 `DUPLICATE_SUBMISSION`。正式客户端幂等键仍按路线留到第 4 周，不在本周扩展。

如果不接受文档更新接口，本周只能通过内部 Service 测试模拟失效，无法完成完整 HTTP 运行验收，因此不推荐删除该接口。

## 三、边界与建议文件结构

```text
Controller
   │
   ▼
Service ───────────────► Repository 接口 ─► MyBatis ─► MySQL（事实来源）
   │
   ├───────────────────► Cache 端口 ──────► Redis（可丢失、可降级）
   │
   └───────────────────► CacheMetricsRecorder（进程内业务统计）
```

计划新增或修改的职责如下；最终文件名可在不改变职责的前提下微调：

- `pom.xml`：只增加 Spring Data Redis starter。
- `src/main/java/com/xcy/internproject/cache/DocumentCache.java`：文档缓存端口，返回 `HIT`、`NEGATIVE_HIT`、`MISS` 或 `UNAVAILABLE`。
- `src/main/java/com/xcy/internproject/cache/UserPermissionCache.java`：权限码集合缓存端口。
- `src/main/java/com/xcy/internproject/cache/TicketDuplicateGuard.java`：短窗口工单重复提交协调端口。
- `src/main/java/com/xcy/internproject/cache/redis/*`：以上端口的 Redis 实现、Key 生成、JSON 编解码和安全释放脚本。
- `src/main/java/com/xcy/internproject/cache/NoOp*`：Redis 功能关闭时的明确降级实现，不能让 Bean 缺失导致应用无法启动。
- `src/main/java/com/xcy/internproject/cache/CacheMetricsRecorder.java`：按缓存区域记录命中、空值命中、未命中、错误和绕过次数。
- `src/main/java/com/xcy/internproject/controller/CacheStatisticsController.java`：只返回聚合计数和比率，不返回 Key、缓存值或连接信息。
- `src/main/java/com/xcy/internproject/service/DocumentService.java`：编排文档缓存旁路和更新后的失效事件。
- `src/main/java/com/xcy/internproject/service/UserPermissionService.java`：编排权限读取和缓存，不承担鉴权。
- `src/main/java/com/xcy/internproject/service/TicketService.java`：创建工单前调用重复提交保护，原有状态流转不改写。
- `src/main/java/com/xcy/internproject/repository/UserPermissionRepository.java`：从 MySQL 查询用户权限码集合。
- `src/main/java/com/xcy/internproject/repository/mybatis/*` 与 Mapper XML：只补权限查询、文档更新所需的最小 SQL。
- `src/main/resources/application.properties` 与本地示例配置：提供 Redis 开关、连接超时、Key 前缀和 TTL；真实密码不入库。
- `src/test/**`：普通单元测试使用 Fake/Stub；真实 Redis 测试使用独立测试 Key 前缀和测试数据库编号。

## 四、缓存对象、Key 和 TTL 设计

| 用途 | Key | 值 | TTL | 说明 |
| --- | --- | --- | --- | --- |
| 文档详情 | `${prefix}:document:v1:{documentId}` | 带类型标记的 JSON：`FOUND + Document` 或 `MISSING` | 命中：10 分钟 + 0～120 秒；空值：60 秒 + 0～15 秒 | `v1` 便于结构升级；单值同时表达不存在，避免双 Key 竞态 |
| 文档重建锁 | `${prefix}:document-rebuild-lock:v1:{documentId}` | 每次获取生成的随机 token | 3 秒 | 仅协调热点 Key 重建，必须比较 token 后再删除 |
| 用户权限 | `${prefix}:user-permissions:v1:{userId}` | 排序后的权限码 JSON 数组，可为空数组 | 5 分钟 + 0～60 秒 | 不缓存用户名、邮箱或 Token；空数组和缓存未命中可区分 |
| 工单防重 | `${prefix}:ticket-dedupe:v1:{sha256}` | 每次获取生成的随机 token | 10 秒固定窗口 | SHA-256 输入为标题、分隔符和描述；Key 不暴露工单正文 |

补充约束：

- `prefix` 由配置提供，例如 `intern:local`、`intern:test:<runId>`；测试和开发 Key 不得混用。
- 所有业务缓存 Key 都带版本号；结构不兼容时升级版本，不在启动时执行危险的全库清理。
- 文档 JSON 按 UTF-8 计算，超过 512 KiB 时不写缓存并记录 `BYPASS`，防止单个 LONGTEXT 形成大 Key。
- TTL 抖动使用可注入的随机数/`Duration` 策略，单元测试验证上下界，测试不能依赖随机碰巧命中某值。
- 工单防重 TTL 是业务窗口而非雪崩 TTL，因此固定 10 秒，不加抖动。
- Redis 专用于缓存时可以评估内存上限和 `allkeys-lfu`；在启动方式和实例用途确认前不修改本机全局配置。
- Redis 的 RDB/AOF 不是业务正确性的前提：文档和权限可从 MySQL 重建，工单最终仍由 MySQL 落库。第 7 天只观察并解释持久化配置，不把 Redis 持久化当数据库使用。

## 五、关键链路

### 1. 文档缓存旁路读取

```text
GET /documents/{id}
→ DocumentService 查询 DocumentCache
  → HIT：返回缓存文档，记录 hit，不访问 Repository
  → NEGATIVE_HIT：记录 negativeHit，直接返回文档不存在
  → MISS：尝试获取该文档的重建锁
      → 获锁：查 MySQL；存在则回填正值，不存在则回填 MISSING
      → 未获锁：短等待后再读缓存；仍无值时直接查 MySQL，限制等待时间
  → UNAVAILABLE/缓存值损坏：记录 error，直接查 MySQL
→ MySQL 的结果决定最终 HTTP 响应
```

降级规则：

- Redis 连接失败、超时或明确的序列化失败不得转化为 HTTP 500；文档查询回退 MySQL。
- 只捕获预期的 Redis/数据访问/序列化异常，不能吞掉所有 `RuntimeException` 来隐藏程序缺陷。
- 损坏的缓存值尝试删除后按 MISS 处理；删除失败仍回退 MySQL。
- 缓存回填失败不改变已经从 MySQL 得到的成功结果或 404 结果。

### 2. 更新后的缓存失效顺序

推荐固定顺序：

```text
开启数据库事务
→ 更新 MySQL
→ 提交事务
→ AFTER_COMMIT 监听器删除文档缓存
→ 下一次读取从 MySQL 重建缓存
```

不采用“先删缓存，再更新数据库”，因为并发读可能在数据库提交前读到旧值并把旧值重新写回缓存。也不在事务提交前删除，因为数据库最终回滚时会产生不必要的缓存抖动。

边界说明：

- 删除缓存失败时数据库更新仍成功；记录缓存错误并依靠 10～12 分钟 TTL 最终收敛。
- 缓存旁路无法做到严格强一致：提交到删除之间仍可能有极短旧读窗口；更早开始的并发查询还可能先读到旧 MySQL 值，再在删除完成后把旧值回填缓存。后者即使删除命令成功，也要依靠 10～12 分钟正值 TTL 最终收敛。本周不引入消息队列、延迟双删或分布式事务。
- 新建文档提交后也删除对应 Key，防止该自增 ID 曾被空值缓存。

### 3. 缓存穿透、击穿和雪崩

- 穿透：不存在的文档写入 `MISSING`，60～75 秒内重复查询不再访问 MySQL。
- 击穿：热门文档缓存过期时以 3 秒短锁限制一个请求重建；等待者最多短暂重试，超时后回退 MySQL，不无限阻塞。
- 雪崩：正值、空值和权限缓存使用不同基础 TTL，并对同类 Key 加随机抖动；Redis 故障时回退 MySQL，但真实验收必须观察数据库压力风险并说明限流属于后续阶段。
- 热点 Key：只缓存按 ID 查询的完整文档，不做本地二级缓存；本周重点证明单 Key 重建锁和 TTL，而不是提前做分片。

### 4. 用户权限缓存

```text
UserPermissionService.getPermissionCodes(userId)
→ 查 UserPermissionCache
  → HIT：返回排序后的不可变权限集合
  → MISS/UNAVAILABLE：Repository 通过四张用户角色权限表查询 MySQL
→ MISS 时回填 Redis；回填失败仍返回 MySQL 结果
```

- 本周不新增未鉴权的“查询任意用户权限”HTTP 接口。
- 数据库没有对应用户时沿用用户不存在语义；存在用户但没有权限时缓存空数组。
- 暂无角色分配接口，因此只提供并测试 `evict(userId)` 边界；第 6 周真正修改角色关系时必须在事务提交后调用它。

### 5. 工单重复提交

本周实现短窗口防重，不冒充完整幂等：

```text
POST /tickets
→ 对 title + 分隔符 + description 计算 SHA-256
→ Redis SET dedupeKey randomToken NX PX 10000
  → 成功：写入 MySQL；成功后保留 Key 到 TTL 到期
  → 已存在：返回 HTTP 409 / DUPLICATE_SUBMISSION
  → Redis 不可用：记录降级，继续写 MySQL（fail-open）
→ MySQL 写入失败：用“比较 token 后删除”的 Lua 脚本释放自己的 Key
```

选择 fail-open 是为了 Redis 故障时工单核心创建仍可用；代价是故障期间不能保证短窗口防重，必须在日志和验收结论中明确。第 4 周再引入客户端幂等键和持久化幂等记录，解决不同实例、长时间重试和结果重放问题。

### 6. 命中率统计

按 `document`、`user-permissions` 两个缓存区域记录：

- `hit`：正值命中；
- `negativeHit`：空值命中；
- `miss`：Redis 正常但 Key 不存在；
- `error`：Redis 连接、超时或缓存内容错误；
- `bypass`：值过大或功能关闭而跳过。

计算：

```text
lookup = hit + negativeHit + miss
hitRate = (hit + negativeHit) / lookup
```

`error` 和 `bypass` 不放入命中率分母，单独展示，避免 Redis 故障把“缓存效果”和“缓存可用性”混成一个指标。`lookup` 的计量单位是缓存读取操作，而不是 HTTP 请求；获锁后的二次检查和竞争等待者的每轮轮询都会计数，因此不能用当前 `hitRate` 直接推导“避免数据库访问的请求比例”。计数使用进程内线程安全累加器，应用重启会清零，多实例也不会聚合；这是第 3 周教学指标，第 9 周再迁移到 Actuator/Prometheus。

`GET /cache-statistics` 只返回上述聚合值，不返回具体 Key、文档内容、权限码或 Redis 地址。

## 六、测试策略与真实验收

### 1. 单元测试

- Service 使用可控 Fake Cache 和计数 Repository，证明命中时不访问 MySQL、未命中只访问一次、Redis 故障仍返回 MySQL 结果。
- 空值命中时不访问 Repository；首次不存在查询写入 `MISSING`。
- TTL 策略验证正值、空值、权限三组上下界。
- 512 KiB 大文档绕过缓存但查询成功。
- 损坏 JSON 删除失败时仍回退 MySQL。
- 更新成功后只在事务提交后失效；数据库回滚不失效。
- 权限空集合可以命中，不能与 MISS 混淆。
- 命中率公式、零查询分母和并发累加正确。
- 防重成功、重复冲突、Redis 故障 fail-open、MySQL 失败安全释放分别有测试。

### 2. MySQL 集成测试

- 文档 `PUT` 更新成功、逻辑删除或不存在时返回空结果。
- 权限 JOIN 只返回未删除用户、角色和权限的去重权限码。
- 现有分页、索引、事务和并发状态测试全部保留，不重写第二周成果。

### 3. 真实 Redis 集成测试

- 使用独立 Redis database（建议 15）和唯一测试前缀；只清理该前缀，禁止 `FLUSHALL`。
- 验证 JSON 往返、正/负 TTL 范围、权限空数组、`SET NX` 互斥和 Lua token 安全释放。
- 验证第一次文档查询访问 MySQL、第二次命中 Redis；可以用测试 Repository 计数或 Redis 断言，不用主观判断“明显更快”。
- 验证 Redis 停止或指向不可连接端口时，文档/权限查询回退 MySQL且 HTTP 语义不变。
- 真实 Redis 测试只有在 `TEST_REDIS_ENABLED=true` 时启用；本周最终完整验证必须在 Redis 可用环境中启用它并报告 0 跳过。Redis 尚不可用时只能报告普通测试结果，不能声称 Redis 集成验收完成。

### 4. 真实运行验收

环境确认后执行：

1. 验证 Redis 进程或容器版本、`PING`、启动命令和停止命令。
2. 使用 local profile 启动应用，创建文档并连续查询两次。
3. 检查第二次查询的缓存命中计数、Redis Key 类型和剩余 TTL。
4. 查询不存在 ID 两次，证明第二次是 `negativeHit` 且仍返回相同 404。
5. 更新文档，证明事务提交后旧 Key 被删除，下一次 GET 返回新内容。
6. 并发查询一个刚过期的文档，证明只有一个重建者，其他请求不会无限等待。
7. 相同工单请求在 10 秒内并发提交，证明只创建一条；等待窗口结束后允许再次创建。
8. 停止 Redis，再调用已有文档、权限 Service 和创建工单：查询继续由 MySQL 返回；工单创建继续成功并记录防重降级。
9. 恢复 Redis，证明无需重启应用即可重新建立缓存（若客户端恢复行为与实际 Lettuce 配置一致）。
10. 最后执行完整 `mvn test`，记录测试总数、失败、错误、跳过和警告。

## 七、分日实施计划

每一天都是一个可独立验收的小增量。当天目标测试通过后才允许重构；每天结束运行相关目标测试和完整 `mvn test`。以下“建议提交信息”仅供用户以后授权提交时使用，本周实施过程中不得自行提交。

### 第 1 天：Redis 基础、环境门禁与最小连接边界

> **执行状态（2026-10-15）：已完成。** 已按 TDD 增加 Spring Data Redis、显式缓存开关、版本化 Key 工厂、TTL 抖动策略、关闭缓存时的 NoOp 降级边界和独立 `redis-test` 配置；未修改业务 Controller/Service/Repository。目标测试 7 个和完整测试 108 个全部通过，Redis 停止时应用测试上下文仍可启动，开启缓存配置时也已完成真实 Spring Boot 启停验证。

> **学习状态（2026-10-15）：已通过。** 用户已完成五道学习检查题；参考答案和对 String 大值、RDB/AOF、过期删除及淘汰策略的修正已归档到第三周统一自测文档。

**学习基础：** String/Hash/List/Set/ZSet 的适用场景；单线程命令执行与 I/O 多路复用的基本概念；TTL、惰性删除和定期删除；RDB 与 AOF；缓存与数据库的职责差异。

**环境门禁：**

- Docker 方案已经选定并通过门禁：Redis 7.4.11、`PING=PONG`、`healthy`、`127.0.0.1:6379`、默认 RDB/AOF 关闭和启动/停止命令均有实测证据。
- 不采用来源不明的 Windows Redis 二进制，也不再把修复 WSL 作为本周主线。
- 后续每次真实 Redis 集成测试前仍需用 `docker compose ps` 和 `redis-cli PING` 验证环境，而不是依赖历史状态。

**TDD 增量：**

1. 先写配置绑定、Key 前缀和 TTL 上下界测试并确认失败。
2. 增加 Spring Data Redis 依赖、缓存开关和最小配置类，使测试通过。
3. 先写“Redis 关闭时应用上下文仍可启动”的测试，再实现 NoOp/降级 Bean。
4. 添加独立 Redis 测试配置，但不把真实地址、密码或本地配置提交。
5. 运行目标测试和完整 `mvn test`。

**当天验收：** Redis 开/关两种配置都能启动；还没有业务缓存行为；生产 Controller/Service 业务不改。

**学习检查题：**

1. String、Hash、Set、ZSet 各适合什么业务？为什么本计划的文档值选 String JSON？
2. Redis Key 到期后一定会在到期瞬间物理删除吗？
3. RDB 和 AOF 的主要差别是什么？为什么本项目不能靠它们保证业务事实？
4. `allkeys-lfu` 与 `volatile-ttl` 的选择依据是什么？
5. 为什么不能把“Docker CLI 已安装”等同于“Redis 可运行”？

### 第 2 天：热门知识文档缓存旁路与 TTL 抖动

> **执行状态（2026-10-16）：已完成。** 已按 TDD 实现 `DocumentCache` 四态契约、Redis String/JSON 适配器、缓存关闭时的 NoOp 实现，以及 `DocumentService.getDocument` 的正值缓存旁路；文档列表保持直接查询 MySQL。超过 512 KiB 的序列化值跳过写入但仍返回 MySQL 结果。目标测试 21 个、完整测试 118 个全部通过，均为 0 跳过；真实 Redis DB 15 已验证 JSON 往返、10～12 分钟 TTL 和第二次读取 Repository 调用数不增加。

> **学习状态（2026-10-17）：已完成。** 用户已不看参考答案完成五道检查题，并完成 cache-aside/read-through、分页缓存、Key 版本和大值绕过行为的纠正与复盘。

**学习基础：** cache-aside；序列化与 Key 版本；缓存命中/未命中；大 Key；TTL 抖动防雪崩。

**TDD 增量：**

1. 为 `DocumentCache` 契约和 `DocumentService` 写命中、未命中、回填、超大值绕过测试，先确认失败。
2. 实现文档缓存端口、Redis JSON 实现和 NoOp 实现。
3. 修改 `DocumentService.getDocument` 编排旁路读取，不改 Controller 响应。
4. 写真实 Redis 往返与 TTL 范围集成测试。
5. 连续查询同一文档，断言第二次 Repository 调用数为 0。
6. 运行目标测试和完整 `mvn test`。

**当天验收：** 文档第一次从 MySQL 读取并回填，第二次从 Redis 返回；文档列表仍直接查 MySQL。

**学习检查题：**

1. cache-aside 与 read-through 有什么区别？当前方案属于哪一种？
2. 为什么分页查询暂不缓存？
3. TTL 为什么要有随机抖动，为什么不能给所有文档同一个精确过期时间？
4. 为什么 Key 要包含 `v1`？
5. 512 KiB 限制解决什么风险，超过限制后的正确行为是什么？

### 第 3 天：空值缓存、故障降级与命中率

> **执行状态（2026-10-18）：已完成。** 已按 TDD 实现 60～75 秒 `MISSING` 空值缓存、Redis 数据访问异常和损坏 JSON 的 MySQL 降级、线程安全进程内指标及 `GET /cache-statistics`。目标测试 42 个、完整测试 139 个全部通过；真实停启 Redis 验证已有文档保持 HTTP 200、不存在文档保持 HTTP 404。用户已完成五道学习检查题，并完成穿透/击穿/雪崩、空值缓存/布隆过滤器和异常降级边界的纠正与复盘。

**学习基础：** 缓存穿透；空值缓存与布隆过滤器对比；超时和故障隔离；指标分母设计。

**TDD 增量：**

1. 写不存在文档首次 MISS、第二次 `NEGATIVE_HIT` 且 Repository 只调用一次的失败测试。
2. 实现带短 TTL 的 `MISSING` 缓存条目。
3. 写 Redis 连接失败、超时、损坏 JSON、删除损坏值失败的降级测试并确认失败。
4. 仅捕获预期 Redis/序列化异常，实现 MySQL 回退。
5. 写 `CacheMetricsRecorder` 并发计数和命中率测试，再实现统计。
6. 写 `/cache-statistics` MockMvc 测试，确认不泄露 Key、值和 Redis 地址，再实现最小只读接口。
7. 运行目标测试和完整 `mvn test`。

**当天验收：** 重复查询不存在 ID 不反复打 MySQL；Redis 不可用时文档成功/404语义不变；可读取聚合统计。

**学习检查题：**

1. 缓存穿透、击穿、雪崩分别指什么？
2. 空值缓存相比布隆过滤器有什么优缺点？
3. 为什么 `error` 不直接计入命中率分母？
4. 哪些异常可以降级，为什么不能 `catch (RuntimeException)` 后全部忽略？
5. 空值 TTL 为什么应明显短于正值 TTL？

### 第 4 天：热点 Key 重建与数据库提交后失效

**学习基础：** `SET NX PX`；锁 token；Lua 比较并删除；锁超时；缓存一致性；事务提交阶段。

**TDD 增量：**

1. 写并发缓存 MISS 只有一个重建者、等待者有最大等待时长的失败测试。
2. 实现 3 秒文档重建锁和 token 安全释放；不实现可重入、续期或公平锁。
3. 先写文档 Repository 更新、Service 更新和 `PUT /documents/{id}` 的成功/404/校验测试。
4. 实现最小文档更新 SQL 和 API。
5. 写“提交后删除、回滚不删除、删除失败不回滚数据库”的测试。
6. 用 `AFTER_COMMIT` 事件监听器实现文档缓存失效。
7. 写真实 Redis 并发和更新失效测试。
8. 运行目标测试和完整 `mvn test`。

**当天验收：** 热点过期时，在锁有效且重建能在等待预算内完成的正常路径中不会让所有请求同时回源；在没有并发旧查询稍后回填的顺序更新流程中，缓存删除后的下一次读取以 MySQL 新值重建缓存。

**执行状态（2026-10-18）：** 开发、验证与学习问答均已完成。8 个并发 MISS 在单元测试和真实 Redis 集成测试中均只产生 1 次 Repository 回源；锁使用 3 秒 TTL、UUID token 和 Lua 比较删除，竞争者按 20 毫秒间隔使用 300 毫秒轮询预算，预算耗尽后不再发起轮询 GET 并直接回源，单次 Redis I/O 另受 500 毫秒命令超时约束。文档创建和更新只在 MySQL 事务提交后精确删除缓存，回滚不删除；`PUT /documents/{id}` 的成功、404 和校验路径已验证。完整启用真实 Redis 的 `mvn test` 为 156 个测试全部通过、0 跳过。用户能够独立说明锁 TTL 原子设置和 token 安全释放，复盘后已掌握提交后失效、最终一致性窗口以及有限等待与 MySQL 降级的完整理由。

**学习检查题：**

1. `SETNX` 后为什么必须有过期时间？
2. 为什么释放锁前要比较 token？直接 `DEL` 有什么竞态？
3. 为什么采用“更新数据库，提交后删除缓存”？
4. 该方案为什么仍不是强一致？最坏陈旧时间由什么决定？
5. 锁等待者为什么必须有超时和数据库降级路径？

### 第 5 天：用户权限读取与缓存

**学习基础：** RBAC 的用户—角色—权限关系；Set 去重；缓存空集合；权限变更后的失效边界。

**TDD 增量：**

1. 先写 MyBatis 权限 JOIN 集成测试，覆盖多角色重复权限、逻辑删除、无权限和不存在用户。
2. 实现 `UserPermissionRepository.findPermissionCodesByUserId` 的最小动态无关 SQL。
3. 写 `UserPermissionService` 的命中、未命中、空集合命中、Redis 故障降级测试。
4. 实现权限缓存，值为排序后的 JSON 数组，TTL 5～6 分钟。
5. 写并实现显式 `evict(userId)` 契约，供第 6 周角色关系更新复用；本周不新增角色管理 API。
6. 运行目标测试和完整 `mvn test`。

**当天验收：** 相同用户第二次权限读取不访问 MySQL；没有权限的用户也不会反复查询；未提前实现鉴权。

**学习检查题：**

1. 为什么权限码天然需要去重和稳定排序？
2. “用户不存在”和“用户存在但权限为空”为什么不能混为一谈？
3. 权限缓存 TTL 为什么比普通静态文档短？
4. 第 6 周修改用户角色关系后，应在事务的哪个阶段清理权限缓存？
5. 为什么本周不开放“查询任意用户权限”的公开接口？

### 第 6 天：工单短窗口重复提交保护

**学习基础：** 分布式锁与幂等的区别；请求指纹；fail-open/fail-closed；锁误删和业务窗口。

**TDD 增量：**

1. 写相同标题和描述并发提交只创建一条、不同内容互不阻塞、10 秒后允许再创建的失败测试。
2. 写 Redis 不可用时仍能创建工单并记录降级的测试。
3. 写 MySQL 保存失败时只释放自己 token 的测试。
4. 实现 SHA-256 请求指纹和 `TicketDuplicateGuard`。
5. 在 `TicketService.createTicket` 中编排防重，不改状态流转、分页和行锁代码。
6. 增加 `DuplicateSubmissionException` 和 HTTP 409 稳定错误响应，所有面向用户消息使用中文。
7. 写真实 Redis 并发集成测试和 MockMvc 回归测试。
8. 运行目标测试和完整 `mvn test`。

**当天验收：** 正常 Redis 下，10 秒内的相同工单只写入一次；Redis 故障时核心创建可用但明确记录防重能力降级。

**学习检查题：**

1. 分布式锁为什么不等于完整幂等？
2. 为什么 Key 中存 SHA-256 而不是标题和描述原文？
3. fail-open 与 fail-closed 各自保护什么，本项目为什么选前者？
4. 保存成功后为什么不立即删除防重 Key？
5. 第 4 周客户端幂等键需要解决本方案的哪些限制？

### 第 7 天：完整集成、持久化/过期观察、故障演练与文档

**学习基础：** Redis 内存淘汰策略；RDB/AOF 恢复边界；客户端超时；缓存监控；如何用证据而不是主观延迟判断命中。

**TDD 与验收增量：**

1. 在真实 Redis 环境启用所有 Redis 集成测试，确保没有因环境条件跳过。
2. 按“真实运行验收”10 个步骤启动应用并调用 HTTP 流程。
3. 分别在 Redis 正常、停止、恢复三种状态记录 API 行为和缓存统计。
4. 检查 Redis `INFO server`、`INFO persistence`、Key TTL 和实际淘汰配置，只记录实测值。
5. 运行目标测试、完整 `mvn test`、`git diff --check` 和敏感信息/生成物检查。
6. 更新 README、本周学习检查答案和当天 handoff；未验证项不得写成完成。

**当天验收：** 计划中的功能、成功路径、失败路径和故障降级都有自动化或可重复运行证据；完整测试通过；仓库没有本地 Redis 配置、密码、`.idea` 或 `target` 进入变更。

**学习检查题：**

1. Redis 重启后缓存全丢，为什么业务仍应正确？
2. `maxmemory-policy` 如何影响只有部分 Key 带 TTL 的实例？
3. 如何客观证明第二次查询命中缓存，而不是“感觉更快”？
4. Redis 恢复后客户端是否自动恢复，应该用什么实验验证而不是猜测？
5. 本周方案中哪些是最终设计，哪些只是单体学习阶段的过渡方案？

## 八、每日统一执行模板

每个行为都严格执行：

1. 明确输入、成功结果、失败结果和验收方式。
2. 写最小失败测试。
3. 运行精确测试类/方法，确认因缺少目标行为而失败，不是环境或语法误报。
4. 写最小生产实现。
5. 再跑目标测试并通过。
6. 必要时重构，再跑目标测试。
7. 执行完整 `mvn test`。
8. 检查 `git status --short` 和 `git diff --check`。
9. 更新当天 handoff；未经授权不执行 Git 提交相关命令。

## 九、周验收标准

- 文档第二次按 ID 查询有可验证的缓存命中，Repository 未被再次调用。
- 不存在文档的重复查询命中短 TTL 空值，不持续打 MySQL。
- 文档更新先提交数据库，随后失效缓存；下一次读取返回新值。
- 同一热点文档过期时只有一个主要重建者，请求不会无限等待。
- 用户权限第二次读取命中缓存；空权限集合可缓存；Redis 故障回退 MySQL。
- 10 秒内相同工单重复提交在 Redis 正常时只产生一条记录；Redis 故障时 fail-open 限制被明确记录。
- 命中率按业务区域统计，错误和绕过单列；统计接口不泄露敏感数据。
- Redis 断开时核心文档查询、权限查询和工单创建不会因缓存异常直接崩溃。
- 真实 Redis 集成测试、真实 HTTP 流程和完整 `mvn test` 都有最新证据。
- README、学习检查答案和 handoff 与实际验证一致。

## 十、Review Focus（实施和审查优先关注）

1. Redis 超时/断线：必须快速回退 MySQL，不能把缓存故障放大为接口 500 或长时间阻塞。
2. 缓存值损坏或版本不兼容：必须删除/绕过并回源，不能把解析异常返回给用户。
3. 负缓存后创建、更新：事务提交后必须失效对应 Key，不能让 `MISSING` 遮住新数据。
4. 热点 Key 并发过期：锁必须带 TTL、token 和最大等待，不能误删他人锁或无限等待。
5. 工单防重边界：相同内容、不同内容、MySQL 失败、Redis 失败和窗口到期都要有明确测试；不能把 10 秒锁宣称为完整幂等。

## 十一、当前执行状态与下一起点

- 用户已经接受本计划，包括 `PUT /documents/{id}` 和 10 秒内容指纹防重规则。
- 第 1～4 天开发增量、自动化测试、真实运行验收和学习问答均已完成。
- Docker Redis 已恢复为 `healthy`、`PING=PONG`。无并发干扰的顺序更新已证明提交后 Key 立即删除、下一次 GET 返回 MySQL 新值；Redis 停机时 PUT/GET 仍为 200，Lettuce 恢复后自动重连。
- 已实测删除失败的一致性边界：Redis 停机前存在的旧 Key 会在恢复后继续陈旧；此外，删除成功后仍可能发生并发旧查询结果回填。两者最长都由 10～12 分钟正值 TTL 决定；本周按既定设计不引入消息队列、延迟双删或分布式事务。
- 下一步可以进入第 5 天用户权限缓存，从“第二次读取不再访问 Repository”的失败测试开始。
