package com.xcy.internproject.repository.mybatis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAllSevenTables() {
        List<String> tableNames = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                    'users', 'roles', 'permissions', 'user_roles',
                    'role_permissions', 'documents', 'tickets'
                  )
                """, String.class);

        assertThat(tableNames).containsExactlyInAnyOrder(
                "users",
                "roles",
                "permissions",
                "user_roles",
                "role_permissions",
                "documents",
                "tickets"
        );

        List<String> checkConstraints = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'tickets'
                  AND constraint_type = 'CHECK'
                """, String.class);

        assertThat(checkConstraints).contains("chk_tickets_status");
    }

    @Test
    void createsUserUniqueConstraints() {
        List<String> constraintNames = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'users'
                  AND constraint_type = 'UNIQUE'
                """, String.class);

        assertThat(constraintNames).contains(
                "uk_users_username",
                "uk_users_email"
        );
    }

    @Test
    void createsTicketPagingIndexes() {
        List<String> indexNames = indexNamesFor("tickets");

        assertThat(indexNames).contains(
                "idx_tickets_status_deleted_created_id",
                "idx_tickets_deleted_created_id"
        );
    }

    @Test
    void createsDocumentTitleIndex() {
        assertThat(indexNamesFor("documents"))
                .contains("idx_documents_deleted_title_id");
    }

    @Test
    void createsRoleAssociationForeignKeys() {
        List<String> constraintNames = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name IN ('user_roles', 'role_permissions')
                """, String.class);

        assertThat(constraintNames).containsExactlyInAnyOrder(
                "fk_user_roles_user",
                "fk_user_roles_role",
                "fk_role_permissions_role",
                "fk_role_permissions_permission"
        );
    }

    private List<String> indexNamesFor(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, String.class, tableName);
    }
}
