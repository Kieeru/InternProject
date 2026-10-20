package com.xcy.internproject.service;

import com.xcy.internproject.cache.permission.UserPermissionCache;
import com.xcy.internproject.cache.permission.UserPermissionCacheReadResult;
import com.xcy.internproject.cache.permission.UserPermissionCacheReadState;
import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.repository.UserPermissionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

@Service
public class UserPermissionService {

    private final UserPermissionRepository userPermissionRepository;
    private final UserPermissionCache userPermissionCache;

    public UserPermissionService(
            UserPermissionRepository userPermissionRepository,
            UserPermissionCache userPermissionCache) {
        this.userPermissionRepository = userPermissionRepository;
        this.userPermissionCache = userPermissionCache;
    }

    public List<String> getPermissionCodes(Long userId) {
        UserPermissionCacheReadResult cached = userPermissionCache.get(userId);
        if (cached.state() == UserPermissionCacheReadState.HIT) {
            return cached.permissionCodes();
        }

        List<String> permissionCodes = userPermissionRepository
                .findPermissionCodesByUserId(userId)
                .map(UserPermissionService::normalize)
                .orElseThrow(() -> new ResourceNotFoundException("用户", userId));
        userPermissionCache.put(userId, permissionCodes);
        return permissionCodes;
    }

    public boolean evict(Long userId) {
        return userPermissionCache.evict(userId);
    }

    private static List<String> normalize(List<String> permissionCodes) {
        return List.copyOf(new TreeSet<>(permissionCodes));
    }
}
