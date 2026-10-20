package com.xcy.internproject.repository;

import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository {

    Optional<List<String>> findPermissionCodesByUserId(Long userId);
}
