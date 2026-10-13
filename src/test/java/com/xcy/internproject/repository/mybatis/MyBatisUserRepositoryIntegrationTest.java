package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.exception.DuplicateResourceException;
import com.xcy.internproject.model.User;
import com.xcy.internproject.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Sql("/db/cleanup.sql")
class MyBatisUserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesUserWithGeneratedId() {
        User saved = userRepository.save("alice", "alice@example.com");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findsActiveUserById() {
        User saved = userRepository.save("bob", "bob@example.com");

        Optional<User> found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUsername()).isEqualTo("bob");
        assertThat(found.get().getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void doesNotFindLogicallyDeletedUser() {
        User saved = userRepository.save("carol", "carol@example.com");

        int updatedRows = jdbcTemplate.update(
                "UPDATE users SET deleted = 1 WHERE id = ?", saved.getId());
        assertThat(updatedRows).isEqualTo(1);

        Optional<User> found = userRepository.findById(saved.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void rejectsDuplicateUsername() {
        userRepository.save("dave", "dave@example.com");

        assertThatThrownBy(() -> userRepository.save("dave", "dave2@example.com"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("用户名或邮箱已存在");
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.save("erin", "erin@example.com");

        assertThatThrownBy(() -> userRepository.save("erin2", "erin@example.com"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("用户名或邮箱已存在");
    }

    @Test
    void doesNotReuseDeletedIdentity() {
        User saved = userRepository.save("frank", "frank@example.com");

        int updatedRows = jdbcTemplate.update(
                "UPDATE users SET deleted = 1 WHERE id = ?", saved.getId());
        assertThat(updatedRows).isEqualTo(1);

        // 逻辑删除不释放唯一身份：同名（即使换了邮箱）仍被唯一约束拦截
        assertThatThrownBy(() -> userRepository.save("frank", "frank2@example.com"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("用户名或邮箱已存在");
    }
}
