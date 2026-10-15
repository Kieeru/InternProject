# InternProject

一个按阶段迭代的企业知识库与智能工单平台练习项目。当前处于第 2 周的 Spring Boot 单体后端阶段，用户、知识文档和工单已使用 MyBatis/MySQL 持久化，并完成文档与工单的条件分页查询。

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

## 技术栈

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Jakarta Validation
- MySQL 8.4 LTS（本周实测 8.4.10）
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
    ↓
Repository 接口：隔离业务层与持久化细节
    ↓
MyBatis Repository → Mapper/XML → MySQL
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

### 5. 创建工单

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

### 6. 查询工单

```http
GET http://localhost:8080/tickets/1
```

分页和组合筛选：

```http
GET http://localhost:8080/tickets?status=PROCESSING&keyword=登录&page=1&size=20
```

`page` 从 1 开始，`size` 取值 1～100；两个列表接口的默认值分别为 1 和 20。
工单列表只返回 `id`、`title`、`status`、`createdAt`，不返回 `description`。

### 7. 更新工单状态

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

测试过程中可能出现 Mockito 动态加载 Java Agent 的兼容性警告，不影响当前测试结果。

## 当前限制

- 当前需要自行管理本地 MySQL；尚未引入数据库迁移工具和 Docker Compose 自动编排。
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

阶段进度记录位于 `docs/` 下对应目录。当前交接记录见 [2026-10-14 handoff](docs/01_SpringBoot单体后端_第1-4周/2026-10-14_handoff.md)。
