package com.xcy.internproject.repository;

import com.xcy.internproject.model.User;

import java.util.Optional;

public interface UserRepository {

    User save(String username, String email);

    Optional<User> findById(Long id);
}
