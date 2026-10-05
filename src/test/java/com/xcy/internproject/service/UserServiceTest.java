package com.xcy.internproject.service;

import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.model.User;
import com.xcy.internproject.repository.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(new InMemoryUserRepository());
    }

    @Test
    void createsUserWithGeneratedId() {
        User user = userService.createUser("alice", "alice@example.com");

        assertThat(user.getId()).isPositive();
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void createsUsersWithDifferentIds() {
        User first = userService.createUser("alice", "alice@example.com");
        User second = userService.createUser("bob", "bob@example.com");

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void returnsExistingUserById() {
        User created = userService.createUser("alice", "alice@example.com");

        User found = userService.getUser(created.getId());

        assertThat(found).isEqualTo(created);
    }

    @Test
    void throwsWhenUserDoesNotExist() {
        assertThatThrownBy(() -> userService.getUser(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User with id 999 was not found");
    }
}
