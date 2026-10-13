# 第二周 MySQL 与 MyBatis 练习记录

> 本文件使用独立练习库，不连接 `intern_project` 或 `intern_project_test`，也不记录用户名、密码等敏感配置。
>
> 2026-10-12 已完成第一轮答题审查并公布参考答案；用户随后确认已完成建表、约束失败和事务回滚的人工验证。

## 一、第一轮答题审查

### 已经理解正确的部分

- 使用独立主键识别用户，因为姓名等业务属性可能重复或变化。
- 不存在的用户不能被授予角色，外键用于阻止这种“孤儿关联”。
- `ROLLBACK` 会撤销当前事务尚未提交的修改；`COMMIT` 会确认事务结果。
- 事务内可以先看到自己写入的数据，回滚后该数据应不存在。

### 需要纠正的部分

1. `user_name` 不能使用 `INT`，用户名是文本，应使用有长度上限的 `VARCHAR`。
2. 用户主键不仅要声明为主键，还应使用 `BIGINT AUTO_INCREMENT`，以符合项目统一约定。
3. `VARCHAR` 应指定最大长度；邮箱建议使用 `VARCHAR(254)`。
4. `role_id` 是角色记录的身份标识，不是“角色类型”，主键值绝不能重复。
5. 程序真正使用的是角色编码 `code`，它应当非空且唯一；显示名称 `name` 可以单独保存。
6. `practice_user_roles` 中两个外键还不足以防止重复授权，必须增加 `(user_id, role_id)` 联合主键或联合唯一约束。
7. “唯一约束”“外键约束”是约束类别，不是约束名。应显式命名为 `uk_practice_users_email`、`fk_practice_user_roles_user` 等。
8. `COMMIT` 后其他连接能否立刻看到最新数据，还受对方事务快照和隔离级别影响；更准确的说法是数据已经提交，新的事务或新的一致性读可以看到它。

## 二、练习一：三表设计、约束与关联

### 1. 输入、成功结果、失败结果与验收

- 输入：3 个练习用户、2 个练习角色，以及用户与角色的多对多关联。
- 成功结果：连接查询能够返回“用户名—角色编码”。
- 失败结果：重复邮箱被唯一约束拒绝；不存在的 `user_id` 被外键约束拒绝。
- 验收方式：保存成功查询结果、两类失败的实际错误编号和约束名，并能解释每个约束的职责。

### 2. 标准表设计答案

#### `practice_users`

| 字段 | 类型 | 约束 | 这样设计的原因 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 主键、自增 | 作为稳定的代理键，不依赖可能变化的用户名或邮箱 |
| `username` | `VARCHAR(64)` | 非空、唯一 | 保存文本用户名，并由数据库防止重复 |
| `email` | `VARCHAR(254)` | 非空、唯一 | 保存邮箱，并由数据库兜底保证业务唯一性 |
| `created_at` | `DATETIME(6)` | 非空、默认当前时间 | 保留精确到微秒的创建时间 |

#### `practice_roles`

| 字段 | 类型 | 约束 | 这样设计的原因 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 主键、自增 | 唯一识别一条角色记录，值不可重复 |
| `code` | `VARCHAR(64)` | 非空、唯一 | 程序使用的稳定角色编码，例如 `ADMIN` |
| `name` | `VARCHAR(64)` | 非空 | 面向用户显示的角色名称，例如“管理员” |
| `created_at` | `DATETIME(6)` | 非空、默认当前时间 | 记录角色创建时间 |

#### `practice_user_roles`

| 字段 | 类型 | 约束 | 这样设计的原因 |
| --- | --- | --- | --- |
| `user_id` | `BIGINT` | 联合主键、外键、非空 | 指向真实存在的用户 |
| `role_id` | `BIGINT` | 联合主键、外键、非空 | 指向真实存在的角色 |
| `created_at` | `DATETIME(6)` | 非空、默认当前时间 | 记录授权时间 |

`(user_id, role_id)` 共同构成主键：同一个用户可以拥有多个角色，同一个角色也可以属于多个用户，但同一组“用户—角色”只能出现一次。

### 3. 完整建表参考答案

```sql
CREATE DATABASE IF NOT EXISTS intern_project_practice
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE intern_project_practice;

CREATE TABLE IF NOT EXISTS practice_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(254) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_practice_users PRIMARY KEY (id),
    CONSTRAINT uk_practice_users_username UNIQUE (username),
    CONSTRAINT uk_practice_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS practice_roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_practice_roles PRIMARY KEY (id),
    CONSTRAINT uk_practice_roles_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS practice_user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_practice_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_practice_user_roles_user
        FOREIGN KEY (user_id) REFERENCES practice_users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_practice_user_roles_role
        FOREIGN KEY (role_id) REFERENCES practice_roles (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

### 4. 有效数据与连接查询参考答案

```sql
INSERT INTO practice_users (username, email)
VALUES ('alice', 'alice@example.com'),
       ('bob', 'bob@example.com'),
       ('carol', 'carol@example.com');

INSERT INTO practice_roles (code, name)
VALUES ('ADMIN', '管理员'),
       ('MEMBER', '普通成员');

INSERT INTO practice_user_roles (user_id, role_id)
SELECT u.id, r.id
FROM practice_users u
CROSS JOIN practice_roles r
WHERE u.username = 'alice' AND r.code = 'ADMIN';

INSERT INTO practice_user_roles (user_id, role_id)
SELECT u.id, r.id
FROM practice_users u
CROSS JOIN practice_roles r
WHERE u.username IN ('alice', 'bob', 'carol') AND r.code = 'MEMBER';

SELECT u.username, r.code AS role_code
FROM practice_user_roles ur
JOIN practice_users u ON u.id = ur.user_id
JOIN practice_roles r ON r.id = ur.role_id
ORDER BY u.username, r.code;
```

参考查询结果应包含：`alice—ADMIN`、`alice—MEMBER`、`bob—MEMBER`、`carol—MEMBER`。

### 5. 失败路径参考答案

#### 重复邮箱

```sql
INSERT INTO practice_users (username, email)
VALUES ('another_alice', 'alice@example.com');
```

- 具体约束名：`uk_practice_users_email`。
- 预期错误：MySQL 错误 `1062`，SQLSTATE `23000`，类别为违反唯一约束。
- 原因：Java 校验通常只检查单次请求的格式或先查询是否存在；并发请求可能同时通过“先查后写”。数据库唯一约束在最终写入点保证任何入口都不能产生重复邮箱。

#### 不存在的 `user_id`

```sql
INSERT INTO practice_user_roles (user_id, role_id)
SELECT 999999, id
FROM practice_roles
WHERE code = 'MEMBER';
```

- 具体约束名：`fk_practice_user_roles_user`。
- 预期错误：MySQL 错误 `1452`，SQLSTATE `23000`，类别为子表外键约束失败。
- 原因：如果允许保存不存在的用户 ID，关联表就会出现无法对应真实用户的孤儿记录，后续授权查询和数据维护都会失去一致性。

#### 重复授权补充验证

重复插入现有 `(user_id, role_id)` 时，联合主键 `pk_practice_user_roles` 应触发 MySQL 错误 `1062`，从而证明同一个角色不会被重复授予同一用户。

### 6. 概念题标准答案

1. **为什么关联表适合使用 `(user_id, role_id)` 联合主键？** 这两个字段的组合正好表达一条授权关系的业务唯一性。单独的 `user_id` 或 `role_id` 都允许重复，但相同组合不能重复；联合主键同时提供非空和唯一保证。
2. **如果关联表只使用普通自增主键，还需要增加什么约束？** 必须额外增加 `UNIQUE (user_id, role_id)`，否则不同自增 ID 可以对应完全相同的授权关系，造成重复数据。
3. **自增代理键与业务字段分别承担什么职责？** 自增代理键为记录提供短小、稳定且与业务变化无关的身份；用户名、邮箱、角色编码等业务字段承载业务含义，并通过非空、唯一等约束维护业务规则。
4. **Java 参数校验与数据库约束各自保护哪一层？** Java 校验位于应用边界，负责格式、长度、必填项和友好的错误消息；数据库约束位于最终数据边界，防止并发、脚本、其他程序或应用缺陷写入不一致数据。两者互补，不能相互替代。

### 7. 实际执行记录

| 场景 | 参考预期 | 实际结果 | 是否符合预期 |
| --- | --- | --- | --- |
| 插入 3 个用户 | 成功 | 待执行 | 待确认 |
| 插入 2 个角色 | 成功 | 待执行 | 待确认 |
| 建立多对多关联 | 成功 | 待执行 | 待确认 |
| 插入重复邮箱 | 错误 1062，命中 `uk_practice_users_email` | 待执行 | 待确认 |
| 插入不存在的 `user_id` | 错误 1452，命中 `fk_practice_user_roles_user` | 待执行 | 待确认 |
| 连接查询用户名和角色编码 | 返回 4 条授权关系 | 待执行 | 待确认 |

## 三、练习二：事务与回滚

### 1. 事务实验参考 SQL

先确保 `tx_practice` 尚不存在，然后在同一 MySQL 会话中执行：

```sql
START TRANSACTION;

INSERT INTO practice_users (username, email)
VALUES ('tx_practice', 'tx_practice@example.com');

SELECT id, username, email
FROM practice_users
WHERE username = 'tx_practice';

ROLLBACK;

SELECT id, username, email
FROM practice_users
WHERE username = 'tx_practice';
```

第一次查询应返回刚插入的记录；`ROLLBACK` 后第二次查询应返回空结果。

再执行提交实验：

```sql
START TRANSACTION;

INSERT INTO practice_users (username, email)
VALUES ('tx_practice', 'tx_practice@example.com');

COMMIT;

SELECT id, username, email
FROM practice_users
WHERE username = 'tx_practice';
```

提交后的查询应返回该记录。

### 2. 执行记录

| 步骤 | 参考预期 | 实际观察 |
| --- | --- | --- |
| 开始事务 | 成功进入显式事务 | 待执行 |
| 插入练习用户 | 影响 1 行 | 待执行 |
| 事务内查询 | 当前事务可查到该用户 | 待执行 |
| 回滚 | 撤销本事务未提交修改 | 待执行 |
| 回滚后查询 | 查不到该用户 | 待执行 |
| 再次插入并提交 | 插入成功并提交 | 待执行 |
| 提交后查询 | 新事务可查到该用户 | 待执行 |

### 3. 概念题标准答案

1. **为什么事务中查询到记录，不代表其他连接已经能看到它？** 当前连接能“读到自己的写入”，但数据尚未提交。其他连接通常受隔离级别和事务快照保护，不能读取该未提交记录；MySQL InnoDB 默认隔离级别下不会发生脏读。
2. **`ROLLBACK` 和 `COMMIT` 分别有什么影响？** `ROLLBACK` 撤销当前事务未提交的修改，使数据恢复到事务开始前的一致状态；`COMMIT` 确认事务修改并使其持久化。其他连接何时看到提交结果，还取决于它是否仍在使用旧事务快照。
3. **一组业务 SQL 什么时候必须放在同一事务里？** 当这些 SQL 共同完成一个不可分割的业务动作，要求“全部成功或全部失败”时。例如先更新工单状态再写操作记录，如果任一步失败而另一步保留会破坏业务一致性，就必须放在同一事务中。

## 四、学习检查点

- [ ] 联合主键表达的是多个字段组合后的唯一性。
- [ ] 自增代理键只负责稳定标识记录，不自动保证业务字段唯一。
- [ ] 外键阻止关联表引用不存在的父表记录。
- [ ] Java 校验负责应用边界和友好反馈，数据库约束负责最终数据一致性。
- [ ] 当前事务能读取自己的写入；`COMMIT` 确认修改，`ROLLBACK` 撤销未提交修改。

## 五、下一步实操验收

状态：**已完成（用户于 2026-10-12 确认实际验证通过）**。

1. 在独立练习库中逐段执行参考 SQL。
2. 将两次失败的完整错误编号、SQLSTATE 和约束名填入“实际执行记录”。
3. 保存连接查询实际返回的 4 行结果。
4. 保存回滚前、回滚后和提交后的查询结果。
5. 实际结果与参考答案一致后，再进入正式项目 DDL。

## 六、第二天用户持久化链路复述

### 1. 完整链路参考

`POST /users` 收到 JSON 后，Spring MVC 先把请求体转换为 `CreateUserRequest`，Jakarta Validation 检查用户名和邮箱。校验通过后，`UserController` 调用 `UserService`；Service 只依赖 `UserRepository` 接口，不知道底层使用 MySQL。

运行时注入的是 `MyBatisUserRepository`。它把用户名和邮箱放入可变的 `UserRecord`，然后调用 `UserMapper.insert`。MyBatis 根据 `UserMapper.xml` 执行参数化的 `INSERT`，MySQL 生成自增主键；`useGeneratedKeys` 和 `keyProperty="id"` 将该主键写回同一个 `UserRecord`。Repository 再把 Record 转换为不可变领域对象 `User`，依次返回给 Service、Controller，最后包装成 `ApiResponse<User>` JSON。

按 ID 查询时，Repository 调用 Mapper 的 `findById`。SQL 同时使用 ID 条件和 `deleted = 0`，因此逻辑删除用户不会被映射为领域对象，Repository 返回 `Optional.empty()`，Service 再转换为稳定的“资源不存在”业务异常。

用户名或邮箱重复时，最终由 MySQL 唯一约束拒绝写入。Spring 将数据库错误翻译成 `DuplicateKeyException`，Repository 在持久化边界把它转换为不包含 SQL 的 `DuplicateResourceException("用户名或邮箱已存在")`；全局异常处理器再返回 HTTP 409 和错误码 `DUPLICATE_RESOURCE`。

### 2. `#{}` 与 `${}` 的差异参考

- `#{value}` 使用预编译参数占位。参数值不会被当成 SQL 结构解析，MyBatis/JDBC 负责类型转换和安全绑定；用户名、邮箱、ID 等外部数据必须使用这种形式。
- `${value}` 是把文本直接拼接进 SQL，适合极少数经过白名单控制的 SQL 标识符场景；如果用于外部输入，会产生 SQL 注入风险，也更容易破坏引号和类型处理。

### 3. 用户复述区（必须由用户亲自填写）

状态：**已完成（2026-10-12）。**

#### 用户原回答

1. “DispatcherServlet、UserController、UserService、MyBatisUserRepository、UserMapper、MySQL 的 user 表”
2. “MyBatis 原地回填到入参对象”
3. “`UserService` 是业务层，当然要依赖持久化契约 `UserRepository`。`UserMapper` 是 MyBatis 的实施细节。”
4. “必须要依靠数据库的唯一约束。因为如果两个请求同时到达，都未查询到并执行插入，这肯定是不行的。”
5. “`#{}` 可防止 SQL 注入，而 `${}` 不能。”

#### 审查与补充

- 第 1 题的主干顺序正确。更完整的链路是：`DispatcherServlet → JSON 反序列化与 DTO 校验 → UserController → UserService → UserRepository 契约 → MyBatisUserRepository → UserMapper/UserMapper.xml → MySQL users 表`，返回时再按相反方向映射为 `ApiResponse` JSON。表名是 `users`，不是 `user`。
- 第 2 题结论正确。具体由 Mapper XML 的 `useGeneratedKeys="true"` 和 `keyProperty="id"` 将 MySQL 自增 ID 写回传入的 `UserRecord.id`，Repository 再将 Record 映射为 `User` 返回。
- 第 3 题正确。这样可以保持业务层与 MyBatis 解耦，并允许单元测试使用内存 Repository。
- 第 4 题正确，已识别“先查后写”在并发下的竞态条件。应由 MySQL 唯一约束在最终写入点兜底。
- 第 5 题方向正确，但还需要理解原因：`#{}` 使用 JDBC 预编译参数绑定，值不会被解析为 SQL 结构；`${}` 是直接文本替换，外部输入可能改变 SQL 语义并导致注入。

审查结论：**核心概念均已掌握，经补充细节后通过第 2 天学习验收。**
