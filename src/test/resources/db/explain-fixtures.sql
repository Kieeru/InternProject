-- 仅允许在 intern_project_test 执行；本脚本会清理并重建执行计划测试数据。
-- 每条写语句都受数据库名条件保护，即使客户端遇错后继续执行也不会修改其他数据库。
SET @is_explain_test_database = (DATABASE() = 'intern_project_test');
START TRANSACTION;

DELETE FROM role_permissions WHERE @is_explain_test_database;
DELETE FROM user_roles WHERE @is_explain_test_database;
DELETE FROM permissions WHERE @is_explain_test_database;
DELETE FROM roles WHERE @is_explain_test_database;
DELETE FROM tickets WHERE @is_explain_test_database;
DELETE FROM documents WHERE @is_explain_test_database;

SET SESSION cte_max_recursion_depth = 2000;

INSERT INTO documents (title, content, created_at, deleted)
WITH RECURSIVE sequence_numbers AS (
    SELECT 1 AS sequence_number
    UNION ALL
    SELECT sequence_number + 1
    FROM sequence_numbers
    WHERE sequence_number < 1200
)
SELECT IF(MOD(sequence_number, 5) = 0,
          CONCAT('Spring-', LPAD(sequence_number, 4, '0')),
          CONCAT('Java-', LPAD(sequence_number, 4, '0'))),
       CONCAT('执行计划测试正文-', sequence_number),
       TIMESTAMP('2026-10-14 12:00:00')
           - INTERVAL (1200 - sequence_number) SECOND,
       IF(MOD(sequence_number, 20) = 0, 1, 0)
FROM sequence_numbers
WHERE @is_explain_test_database;

INSERT INTO tickets (title, description, status, created_at, deleted)
WITH RECURSIVE sequence_numbers AS (
    SELECT 1 AS sequence_number
    UNION ALL
    SELECT sequence_number + 1
    FROM sequence_numbers
    WHERE sequence_number < 1200
)
SELECT CONCAT(
           IF(MOD(sequence_number, 6) = 0, 'Spring登录故障-', '普通工单-'),
           LPAD(sequence_number, 4, '0')
       ),
       CONCAT('执行计划测试描述-', sequence_number),
       ELT(MOD(sequence_number - 1, 4) + 1,
           'PENDING', 'PROCESSING', 'RESOLVED', 'CLOSED'),
       TIMESTAMP('2026-10-14 12:00:00')
           - INTERVAL (1200 - sequence_number) SECOND,
       IF(MOD(sequence_number, 25) = 0, 1, 0)
FROM sequence_numbers
WHERE @is_explain_test_database;

INSERT INTO roles (code, name)
SELECT 'EXPLAIN_ROLE', '执行计划测试角色'
WHERE @is_explain_test_database;
SET @explain_fixture_role_id = IF(
    @is_explain_test_database,
    LAST_INSERT_ID(),
    NULL
);

INSERT INTO permissions (code, name)
WITH RECURSIVE sequence_numbers AS (
    SELECT 1 AS sequence_number
    UNION ALL
    SELECT sequence_number + 1
    FROM sequence_numbers
    WHERE sequence_number < 100
)
SELECT CONCAT('explain:permission:', LPAD(sequence_number, 3, '0')),
       CONCAT('执行计划测试权限', sequence_number)
FROM sequence_numbers
WHERE @is_explain_test_database;

INSERT INTO role_permissions (role_id, permission_id)
SELECT @explain_fixture_role_id, id
FROM permissions
WHERE @is_explain_test_database
  AND code LIKE 'explain:permission:%';

COMMIT;

SELECT IF(
    @is_explain_test_database,
    '执行计划测试数据已重建',
    '已跳过：当前数据库不是 intern_project_test'
) AS fixture_result;
