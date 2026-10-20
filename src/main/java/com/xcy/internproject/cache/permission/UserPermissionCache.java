package com.xcy.internproject.cache.permission;

import java.util.List;

public interface UserPermissionCache {

    UserPermissionCacheReadResult get(Long userId);

    boolean put(Long userId, List<String> permissionCodes);

    boolean evict(Long userId);
}
