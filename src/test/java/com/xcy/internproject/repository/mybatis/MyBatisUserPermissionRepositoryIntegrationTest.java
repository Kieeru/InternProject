package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.repository.UserPermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql("/db/cleanup.sql")
class MyBatisUserPermissionRepositoryIntegrationTest {

    @Autowired
    private UserPermissionRepository userPermissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsDistinctPermissionCodesInStableOrderAcrossMultipleRoles() {
        Long userId = insertUser("permission-user", "permission-user@example.com", false);
        Long editorRoleId = insertRole("EDITOR", "编辑者", false);
        Long reviewerRoleId = insertRole("REVIEWER", "审核者", false);
        Long readPermissionId = insertPermission("document:read", "读取文档", false);
        Long writePermissionId = insertPermission("document:write", "编辑文档", false);
        Long auditPermissionId = insertPermission("ticket:audit", "审核工单", false);
        assignRole(userId, editorRoleId);
        assignRole(userId, reviewerRoleId);
        grantPermission(editorRoleId, writePermissionId);
        grantPermission(editorRoleId, readPermissionId);
        grantPermission(reviewerRoleId, auditPermissionId);
        grantPermission(reviewerRoleId, readPermissionId);

        assertThat(userPermissionRepository.findPermissionCodesByUserId(userId))
                .hasValue(List.of("document:read", "document:write", "ticket:audit"));
    }

    @Test
    void ignoresLogicallyDeletedRolesAndPermissions() {
        Long userId = insertUser("active-user", "active-user@example.com", false);
        Long activeRoleId = insertRole("ACTIVE_ROLE", "有效角色", false);
        Long deletedRoleId = insertRole("DELETED_ROLE", "已删除角色", true);
        Long activePermissionId = insertPermission("document:read", "读取文档", false);
        Long deletedPermissionId = insertPermission("document:delete", "删除文档", true);
        Long hiddenPermissionId = insertPermission("ticket:hidden", "隐藏权限", false);
        assignRole(userId, activeRoleId);
        assignRole(userId, deletedRoleId);
        grantPermission(activeRoleId, activePermissionId);
        grantPermission(activeRoleId, deletedPermissionId);
        grantPermission(deletedRoleId, hiddenPermissionId);

        assertThat(userPermissionRepository.findPermissionCodesByUserId(userId))
                .hasValue(List.of("document:read"));
    }

    @Test
    void returnsEmptyPermissionListWhenUserOnlyHasLogicallyDeletedRole() {
        Long userId = insertUser("deleted-role-user", "deleted-role-user@example.com", false);
        Long deletedRoleId = insertRole("ONLY_DELETED_ROLE", "唯一角色已删除", true);
        Long permissionId = insertPermission("document:hidden", "不应返回", false);
        assignRole(userId, deletedRoleId);
        grantPermission(deletedRoleId, permissionId);

        assertThat(userPermissionRepository.findPermissionCodesByUserId(userId))
                .hasValue(List.of());
    }

    @Test
    void returnsEmptyPermissionListWhenRoleOnlyHasLogicallyDeletedPermission() {
        Long userId = insertUser(
                "deleted-permission-user", "deleted-permission-user@example.com", false);
        Long roleId = insertRole("DELETED_PERMISSION_ROLE", "仅有已删除权限", false);
        Long deletedPermissionId = insertPermission(
                "document:deleted", "唯一权限已删除", true);
        assignRole(userId, roleId);
        grantPermission(roleId, deletedPermissionId);

        assertThat(userPermissionRepository.findPermissionCodesByUserId(userId))
                .hasValue(List.of());
    }

    @Test
    void returnsEmptyPermissionListForExistingUserWithoutPermissions() {
        Long userId = insertUser("no-permission-user", "no-permission-user@example.com", false);

        assertThat(userPermissionRepository.findPermissionCodesByUserId(userId))
                .hasValue(List.of());
    }

    @Test
    void returnsEmptyOptionalForMissingOrLogicallyDeletedUser() {
        Long deletedUserId = insertUser("deleted-user", "deleted-user@example.com", true);

        assertThat(userPermissionRepository.findPermissionCodesByUserId(999999L)).isEmpty();
        assertThat(userPermissionRepository.findPermissionCodesByUserId(deletedUserId)).isEmpty();
    }

    private Long insertUser(String username, String email, boolean deleted) {
        jdbcTemplate.update(
                "INSERT INTO users (username, email, deleted) VALUES (?, ?, ?)",
                username, email, deleted ? 1 : 0);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private Long insertRole(String code, String name, boolean deleted) {
        jdbcTemplate.update(
                "INSERT INTO roles (code, name, deleted) VALUES (?, ?, ?)",
                code, name, deleted ? 1 : 0);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE code = ?", Long.class, code);
    }

    private Long insertPermission(String code, String name, boolean deleted) {
        jdbcTemplate.update(
                "INSERT INTO permissions (code, name, deleted) VALUES (?, ?, ?)",
                code, name, deleted ? 1 : 0);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM permissions WHERE code = ?", Long.class, code);
    }

    private void assignRole(Long userId, Long roleId) {
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private void grantPermission(Long roleId, Long permissionId) {
        jdbcTemplate.update(
                "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)",
                roleId, permissionId);
    }
}
