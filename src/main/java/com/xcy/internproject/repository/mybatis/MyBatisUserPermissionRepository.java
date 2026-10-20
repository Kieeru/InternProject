package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.repository.UserPermissionRepository;
import com.xcy.internproject.repository.mybatis.mapper.UserPermissionMapper;
import com.xcy.internproject.repository.mybatis.record.UserPermissionRecord;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@Repository
public class MyBatisUserPermissionRepository implements UserPermissionRepository {

    private final UserPermissionMapper userPermissionMapper;

    public MyBatisUserPermissionRepository(UserPermissionMapper userPermissionMapper) {
        this.userPermissionMapper = userPermissionMapper;
    }

    @Override
    public Optional<List<String>> findPermissionCodesByUserId(Long userId) {
        List<UserPermissionRecord> rows = userPermissionMapper.findByUserId(userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        TreeSet<String> permissionCodes = new TreeSet<>();
        rows.stream()
                .map(UserPermissionRecord::getPermissionCode)
                .filter(code -> code != null)
                .forEach(permissionCodes::add);
        return Optional.of(List.copyOf(permissionCodes));
    }
}
