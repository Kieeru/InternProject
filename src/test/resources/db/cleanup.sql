-- 仅允许连接 intern_project_test；本脚本只清理测试数据，不删除表。
DELETE FROM role_permissions;
DELETE FROM user_roles;
DELETE FROM tickets;
DELETE FROM documents;
DELETE FROM permissions;
DELETE FROM roles;
DELETE FROM users;
