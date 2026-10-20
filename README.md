# InternProject

一个按阶段迭代的企业知识库与智能工单平台练习项目。当前已完成第 2 周 MySQL/MyBatis 开发和第 3 周 Redis 功能开发、最终综合验收及全部学习问答，准备进入第 4 周 Java 并发、JVM 与第一轮整理。用户、知识文档和工单已使用 MyBatis/MySQL 持久化，文档按 ID 查询与内部用户权限读取已接入可开关、可降级的 Redis 缓存旁路，工单创建已接入 10 秒短窗口防重。MySQL 始终是事实数据源。

## 当前已实现功能

- 用户：创建用户、按 ID 查询用户。
- 知识文档：创建、按 ID 查询、按标题前缀分页查询。
- 工单：创建、按 ID 查询、按状态和标题关键字分页查询、按规则更新状态。
- 工单状态更新使用 Spring 事务和 MySQL `SELECT ... FOR UPDATE` 防止并发竞态。
- 列表固定按 `created_at DESC, id DESC` 稳定排序，只返回摘要字段。
- 统一响应结构 `ApiResponse<T>`。
- Jakarta Validation 请求参数校验。
- 全局异常处理和稳定错误码。
- 基于 JUnit 和 MockMvc 的 Service 测试与接口集成测试。
- Redis 缓存开关、版本化 Key 工厂、TTL 随机抖动策略和关闭缓存时的 NoOp 降级边界。
- 热门文档按 ID 的 String/JSON 缓存旁路：首次未命中回源 MySQL 并回填，后续命中不再访问 Repository；超过 512 KiB 的值只返回、不缓存。
- 不存在文档使用 60～75 秒 `MISSING` 空值缓存，降低重复无效查询造成的缓存穿透。
- Redis 连接失败、命令超时或缓存 JSON 损坏时回退 MySQL；损坏值会尝试精确删除。
- 热点文档缓存 MISS 使用带 3 秒 TTL 和唯一 token 的 Redis 短锁协调重建；竞争者最多等待 300 毫秒，超时或 Redis 不可用时回源 MySQL。
- 文档创建或更新成功后发布精确 ID 事件，只在数据库事务提交后删除正值或空值缓存；回滚不删除缓存。
- `PUT /documents/{id}` 更新未删除文档；成功返回 200，不存在或逻辑删除返回 404。
- `GET /cache-statistics` 返回文档与用户权限缓存区域的聚合计数和命中率，不返回 Key、缓存内容或 Redis 地址。
- 用户权限通过 `users → user_roles → roles → role_permissions → permissions` 查询；多个角色的重复权限会去重并按权限码稳定排序，逻辑删除的用户、角色和权限不会生效。
- 内部 `UserPermissionService` 将权限集合缓存为排序后的 JSON 数组，TTL 为 5～6 分钟；空权限数组也可命中，Redis 连接失败、超时或损坏载荷时回退 MySQL，并提供精确 `evict(userId)` 边界。本周未开放权限查询 Controller。
- 工单创建根据标题和描述的长度前缀 UTF-8 编码计算 SHA-256 指纹并执行 Redis `SET NX`，使用随机 token 和固定 10 秒 TTL；窗口内相同内容返回 HTTP 409 / `DUPLICATE_SUBMISSION`，不同内容互不阻塞。长度前缀保证字段本身包含 NUL 字符时仍不会产生拼接歧义。
- 工单保存成功后保留防重 Key 到期；MySQL 保存失败时用 Lua 比较 token 后只删除自己的 Key。Redis 不可用时采用 fail-open，继续写入 MySQL并记录防重降级。

## 技术栈

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Jakarta Validation
- MySQL 8.4 LTS（本周实测 8.4.10）
- Redis 7.4.11（通过 Docker Compose 提供）
- Spring Data Redis 4.1.1、Lettuce 7.5.2.RELEASE（文档详情缓存已接入）
- MyBatis Spring Boot Starter 4.0.0、MyBatis XML 动态 SQL
- Spring JDBC 事务管理
- Maven
- JUnit 5、AssertJ、MockMvc
- `ConcurrentHashMap`、`AtomicLong` 内存实现仅用于轻量 Service 单元测试

## 分层结构

```text
HTTP 请求
    ↓
Controller：接收请求、校验 DTO、确定 HTTP 状态
    ↓
Service：执行业务规则和状态判断
    ├→ DocumentCache / DocumentRebuildLock：Redis 可选缓存与热点重建协调；关闭时使用 NoOp
    ├→ UserPermissionCache：内部权限集合缓存；关闭或故障时回源 MySQL
    ├→ TicketDuplicateGuard：工单创建的 Redis 短窗口防重；故障时 fail-open
    └→ Repository 接口：隔离业务层与持久化细节
        ↓
    MyBatis Repository → Mapper/XML → MySQL（事实来源）
```

主要代码目录：

```text
src/main/java/com/xcy/internproject/
├── controller/   REST 接口
├── service/      业务逻辑
├── repository/   数据访问契约、MyBatis 实现与测试用内存实现
├── model/        用户、文档、工单等领域数据
├── dto/          请求与统一响应结构
└── exception/    业务异常与全局异常处理
```

Controller 不直接访问 Repository，依赖通过构造器注入。

## 运行项目

### 环境要求

- JDK 21
- Maven 3.9 或兼容版本
- MySQL 8.4 LTS，并已创建相互隔离的本地开发库 `intern_project` 和测试库 `intern_project_test`
- 第三周 Redis 练习需要 Docker Desktop 和 Docker Compose；MySQL 目前仍由本机服务提供

确认环境：

```powershell
java -version
mvn -version
```

首次使用时创建数据库，并初始化开发库表结构：

```sql
CREATE DATABASE IF NOT EXISTS intern_project
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS intern_project_test
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

```powershell
mysql --user="your_mysql_user" --password --database=intern_project --execute="source src/main/resources/db/schema.sql"
```

初始化脚本可以使用具有建表权限的本地 DDL 账户；日常启动应用时应改用仅拥有这些业务表查询和写入权限的运行账户。第二周本地练习可以暂时复用现有账户，但部署环境应遵循最小权限原则。

复制 `src/main/resources/application-local.properties.example` 为不提交的 `application-local.properties`。真实连接信息只放在环境变量中：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/intern_project?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="your_mysql_user"
$env:DB_PASSWORD="your_mysql_password"
```

在项目根目录使用 local profile 启动：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

日志出现以下内容表示启动成功：

```text
Tomcat started on port 8080
Started InternProjectApplication
```

默认访问地址为 `http://localhost:8080`。修改 Java 代码后需要停止并重新启动应用，当前项目未启用自动热重载。

### 启动第三周 Redis 环境

仓库中的 `compose.yaml` 只管理第三周使用的 Redis，不会启动或修改本机 MySQL。Redis 镜像已使用摘要固定，宿主机端口只绑定到 `127.0.0.1:6379`。

```powershell
docker compose up -d redis
docker compose ps
docker compose exec -T redis redis-cli PING
```

预期 `PING` 返回 `PONG`，`docker compose ps` 显示 Redis 为 `healthy`。停止和再次启动：

```powershell
docker compose stop redis
docker compose start redis
```

`docker compose down` 会删除容器和项目网络，但默认保留命名数据卷；不要在需要保留本地 Redis 数据时执行 `docker compose down -v`。当前 Spring Boot Docker Compose 集成仍保持关闭，应用不会自动启动容器。

默认 `app.cache.redis.enabled=false`，应用使用 NoOp 缓存并直接查询 MySQL。确认 Redis `PING=PONG` 后，可在 local profile 启动前设置：

```powershell
$env:REDIS_CACHE_ENABLED="true"
$env:REDIS_KEY_PREFIX="intern:local"
```

第 4 天已经接入热点 Key 重建锁、文档更新 API 和事务提交后精确失效。竞争者的 300 毫秒是轮询调度预算，预算内单次 Redis I/O 仍分别受 500 毫秒命令超时约束；预算耗尽后直接回源 MySQL。Redis 不可用时，文档查询和更新仍以 MySQL 结果为准；若数据库提交后的缓存删除恰好失败，旧 Key 最长可能保留到 10～12 分钟正值 TTL 到期，本周不引入消息队列或分布式事务消除该最终一致性窗口。

第 5 天已经接入内部用户权限读取与缓存。权限首次读取从 MySQL 的用户—角色—权限关系表加载并回填 `${prefix}:user-permissions:v1:{userId}`，第二次读取命中 Redis；空权限集合保存为 `[]` 并按负命中统计。当前没有公开权限查询接口，也没有角色写接口；未来修改用户角色关系时，应在数据库事务提交后调用精确失效边界。

第 6 天已经接入工单短窗口防重。开启 Redis 后，`POST /tickets` 会对标题和描述计算 SHA-256，并尝试写入 `${prefix}:ticket-dedupe:v1:{sha256}`；10 秒内相同内容重复提交返回 HTTP 409。该机制只防短窗口重复，不保存首次结果，也不能替代完整幂等。Redis 故障时工单仍写入 MySQL，因此故障期间可能产生重复工单。

2026-10-20 的第三周综合验收使用真实 Redis 7.4.11 和独立测试库完成：正值/空值命中、更新提交后失效、并发热点重建、权限 Service 故障回源、工单并发防重、Redis 停止和恢复均有自动化或真实 HTTP 证据。运行中的应用在 Redis 停止时仍能从 MySQL 返回文档并创建工单，恢复后无需重启即可重新使用 Redis；工单防重降级会按故障期记录一次不含业务载荷的 WARN。最终审查还补充了损坏文档 JSON 的严格字段类型校验。最终测试证据以当天 handoff 为准。

本地 Compose Redis 的实测配置为 `maxmemory=0`、`maxmemory-policy=noeviction`、AOF 关闭、RDB 规则启用，并挂载 `redis-data` 命名卷。该配置用于本地学习和故障演练，不代表生产环境的内存上限、淘汰和持久化方案；业务正确性不能依赖缓存数据在重启后仍然存在。

## 使用 Apifox 调用接口

POST 和 PUT 请求需要设置：

```http
Content-Type: application/json
```

GET 请求不需要填写 Body。以下示例中的资源 ID 必须替换为实际创建接口返回的 `data.id`。

### 1. 创建用户

```http
POST http://localhost:8080/users
Content-Type: application/json
```

```json
{
  "username": "alice",
  "email": "alice@example.com"
}
```

成功返回 HTTP 201：

```json
{
  "code": "OK",
  "data": {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com"
  },
  "message": "成功"
}
```

### 2. 查询用户

```http
GET http://localhost:8080/users/1
```

### 3. 创建知识文档

```http
POST http://localhost:8080/documents
Content-Type: application/json
```

```json
{
  "title": "Spring IOC",
  "content": "IOC manages object dependencies."
}
```

成功返回 HTTP 201，文档标题不能为空且长度不能超过 200 个字符。

### 4. 查询知识文档

```http
GET http://localhost:8080/documents/1
```

开启 Redis 缓存后，该详情查询采用 cache-aside：先读 `intern:local:document:v1:{id}`，未命中时查询 MySQL 并以 10～12 分钟随机 TTL 回填；分页查询仍直接访问 MySQL。

分页查询：

```http
GET http://localhost:8080/documents?titlePrefix=Spring&page=1&size=20
```

列表响应只包含摘要字段，不返回正文：

```json
{
  "code": "OK",
  "data": {
    "items": [
      {
        "id": 1,
        "title": "Spring IOC",
        "createdAt": "2026-10-14T16:00:00"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "totalPages": 1
  },
  "message": "成功"
}
```

### 5. 更新知识文档

```http
PUT http://localhost:8080/documents/1
Content-Type: application/json
```

```json
{
  "title": "Redis 缓存一致性",
  "content": "数据库事务提交后删除对应缓存 Key。"
}
```

更新成功返回 HTTP 200。应用先更新 MySQL 并提交事务，再删除 `intern:local:document:v1:{id}`；在没有并发旧查询稍后回填的顺序流程中，下一次详情查询从 MySQL 读取新值并重建缓存。不存在或已逻辑删除的文档返回 HTTP 404。

### 6. 创建工单

```http
POST http://localhost:8080/tickets
Content-Type: application/json
```

```json
{
  "title": "无法登录系统",
  "description": "输入正确密码后仍然提示登录失败"
}
```

成功返回 HTTP 201，新工单的初始状态固定为 `PENDING`：

```json
{
  "code": "OK",
  "data": {
    "id": 1,
    "title": "无法登录系统",
    "description": "输入正确密码后仍然提示登录失败",
    "status": "PENDING"
  },
  "message": "成功"
}
```

### 7. 查询工单

```http
GET http://localhost:8080/tickets/1
```

分页和组合筛选：

```http
GET http://localhost:8080/tickets?status=PROCESSING&keyword=登录&page=1&size=20
```

`page` 从 1 开始，`size` 取值 1～100；两个列表接口的默认值分别为 1 和 20。
工单列表只返回 `id`、`title`、`status`、`createdAt`，不返回 `description`。

### 8. 更新工单状态

```http
PUT http://localhost:8080/tickets/1/status
Content-Type: application/json
```

```json
{
  "status": "PROCESSING"
}
```

工单只允许按以下顺序流转：

```text
PENDING → PROCESSING → RESOLVED → CLOSED
```

跳级、回退、重复设置当前状态，以及从 `CLOSED` 继续流转都会被拒绝。例如从 `PENDING` 直接改为 `RESOLVED` 将返回 HTTP 400：

```json
{
  "code": "INVALID_STATUS_TRANSITION",
  "data": null,
  "message": "不允许工单状态从 PENDING 变更为 RESOLVED"
}
```

### 9. 查询缓存统计

```http
GET http://localhost:8080/cache-statistics
```

响应按缓存区域返回 `hit`、`negativeHit`、`miss`、`error`、`bypass`、`lookup` 和 `hitRate`。其中：

```text
lookup = hit + negativeHit + miss
hitRate = (hit + negativeHit) / lookup
```

`error` 和 `bypass` 单独展示，不进入命中率分母。这里的 `lookup` 按缓存读取操作计数，不是 HTTP 请求数；一次冷查询的获锁后二次检查、竞争者的每轮轮询都会分别计入，因此该命中率不能直接解释为“多少请求避免了数据库访问”。`error` 按失败的缓存操作计数；一次请求的读取和回填都失败时会增加两次。当前计数保存在单个应用进程内，重启会清零，多实例之间不会自动聚合。

## 统一响应与错误码

所有应用接口使用以下响应结构：

```json
{
  "code": "OK",
  "data": {},
  "message": "成功"
}
```

| HTTP 状态 | 错误码 | 含义 |
| --- | --- | --- |
| 200 | `OK` | 查询或更新成功 |
| 201 | `OK` | 创建成功 |
| 400 | `VALIDATION_ERROR` | 请求字段为空、格式错误或超过长度限制 |
| 400 | `INVALID_REQUEST_BODY` | JSON 无法解析或包含不支持的枚举值 |
| 400 | `INVALID_REQUEST_PARAMETER` | 查询参数格式错误或包含不支持的枚举值 |
| 400 | `INVALID_STATUS_TRANSITION` | 工单状态流转不合法 |
| 404 | `RESOURCE_NOT_FOUND` | 用户、文档或工单不存在 |
| 409 | `DUPLICATE_RESOURCE` | 用户名或邮箱已存在 |
| 409 | `DUPLICATE_SUBMISSION` | 10 秒窗口内重复提交相同标题和描述的工单 |

不存在的工单响应示例：

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "data": null,
  "message": "工单（ID：999999）不存在"
}
```

重复提交相同用户名或邮箱时返回 HTTP 409，例如：

```json
{
  "code": "DUPLICATE_RESOURCE",
  "data": null,
  "message": "用户名或邮箱已存在"
}
```

## 运行测试

执行完整测试套件：

```powershell
$env:TEST_DB_URL="jdbc:mysql://localhost:3306/intern_project_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:TEST_DB_USERNAME="your_test_mysql_user"
$env:TEST_DB_PASSWORD="your_test_mysql_password"
mvn test
```

测试 profile 会在独立测试库中执行 `schema.sql`。不要让 `TEST_DB_URL` 指向开发库或其他包含真实数据的数据库；测试会清理并重建自己的测试数据。

需要使用测试数据库和 Redis DB 15 启动应用并执行 HTTP 验收时，使用专门的测试运行 goal：

```powershell
mvn spring-boot:test-run `
  "-Dspring-boot.run.profiles=test,redis-test" `
  "-Dspring-boot.run.arguments=--server.port=18080"
```

Spring Boot Maven Plugin 4.1.1 的 `spring-boot:run -Dspring-boot.run.useTestClasspath=true` 只加入 test scope 依赖，不会加入项目的 `target/test-classes`，因此不能用它加载 `application-test.properties` 或 `application-redis-test.properties`。日常开发库运行仍使用前文的 `spring-boot:run` 与 `local` profile；测试 profile 运行固定使用 `spring-boot:test-run`，避免再次因 DataSource URL 缺失而误判为应用故障。

自动化测试覆盖：

- Spring 应用上下文启动。
- 用户、文档和工单的成功请求。
- 必填字段和格式校验。
- 资源不存在。
- 工单 ID 生成与查询。
- 合法、跳级、重复和回退状态流转。
- 无法识别的工单状态值。
- 文档与工单的分页、条件组合、稳定排序、逻辑删除和非法边界。
- 工单并发状态更新时的事务与行锁行为。
- Redis 缓存配置绑定、版本化 Key、TTL 抖动上下界和关闭缓存时的 NoOp 降级。
- 文档缓存 JSON 往返、正值/空值命中、回填、超大值绕过、分页不使用缓存，以及真实 Redis DB 15 的两组 TTL 和第二次读取不再访问 Repository。
- 文档更新成功/404/校验、MyBatis 更新边界、创建和更新提交后失效、事务回滚不失效，以及缓存删除失败不反向破坏数据库结果。
- 热点 Key 的 `SET NX`、3 秒 TTL、唯一 token、Lua 比较删除、8 请求并发单回源、300 毫秒有限等待和超时降级。
- Redis 连接失败、命令超时、语法或语义损坏 JSON、损坏值删除失败时的 MySQL 降级，以及并发指标累加和统计接口脱敏。
- 用户权限 JOIN 的多角色、重复权限、稳定排序、逻辑删除、空权限和不存在用户语义；权限缓存的非空/空数组往返、5～6 分钟 TTL、第二次读取不再访问 Repository、损坏值清理、精确失效和 Redis 连接失败回源 MySQL。
- 工单内容指纹的稳定性和字段边界、真实 Redis `SET NX`、固定 10 秒 TTL、随机 token、Lua 安全释放、相同内容 8 请求并发单写入、不同内容互不阻塞、窗口到期后允许再次创建，以及 Redis 故障 fail-open。

测试过程中可能出现 Mockito 动态加载 Java Agent 的兼容性警告，不影响当前测试结果。

## 当前限制

- 当前仍需自行管理本机 MySQL，且尚未引入数据库迁移工具；Docker Compose 目前只提供第三周 Redis 环境，Spring Boot 不会自动管理它。
- 文档详情正值/空值缓存、Redis 故障降级、热点 Key 重建锁、事务提交后失效、用户权限读取缓存、工单短窗口防重和进程内命中率统计已经完成，第三周最终综合演练与第 1～7 天学习问答也已全部完成。
- 工单防重只按完全相同的标题和描述识别 10 秒窗口内的重复提交；Redis 故障时为保证创建可用会接受重复风险，也不会保存首次响应供客户端重放。第 4 周仍需用客户端幂等键和持久化记录升级为完整幂等。
- 缓存旁路不是强一致方案：若 MySQL 已提交但 Redis 删除失败，恢复后的 Redis 仍可能返回旧正值；即使删除成功，也可能有更早开始的并发查询在删除后回填旧 MySQL 结果。两种情况都依靠 10～12 分钟正值 TTL 最终收敛，本周明确接受该一致性窗口。
- 深页仍使用 offset 分页，数据规模继续增长后可评估游标分页。
- 当前未实现登录认证和权限控制。
- 文档暂不支持文件上传、版本和访问权限。
- 工单暂不支持分派、优先级、操作历史和重新打开。
- 当前是学习阶段的单体应用，不是最终微服务架构。

使用 Apifox 测试查询或更新接口时，应先通过创建接口取得实际 `data.id`；数据写入 MySQL 后，应用重启不会改变该资源 ID。

## 后续路线

项目按照 [完整学习计划](docs/myplan.md) 分阶段演进：

1. 第 2～4 周：MySQL、MyBatis/MyBatis-Plus、Redis、并发与单体项目整理。
2. 第 5～9 周：Spring Cloud、Gateway、Nacos、认证、Kafka 和可观测性。
3. 第 10～14 周：Python FastAPI、LightRAG、RAG 评测和 MCP。
4. 第 15～17 周：集成测试、部署、演示、文档和求职准备。

阶段进度记录位于 `docs/` 下对应目录。当前交接记录见 [2026-10-20 handoff](docs/01_SpringBoot单体后端_第1-4周/2026-10-20_handoff.md)，第三周配套复习资料见[第三周第 1～7 天自测题与参考答案](docs/01_SpringBoot单体后端_第1-4周/第三周_第1-7天自测题与参考答案.md)。
