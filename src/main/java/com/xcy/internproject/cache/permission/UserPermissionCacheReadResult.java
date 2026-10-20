package com.xcy.internproject.cache.permission;

import java.util.List;

public record UserPermissionCacheReadResult(
        UserPermissionCacheReadState state,
        List<String> permissionCodes) {

    public static UserPermissionCacheReadResult hit(List<String> permissionCodes) {
        return new UserPermissionCacheReadResult(
                UserPermissionCacheReadState.HIT,
                List.copyOf(permissionCodes));
    }

    public static UserPermissionCacheReadResult miss() {
        return new UserPermissionCacheReadResult(UserPermissionCacheReadState.MISS, null);
    }

    public static UserPermissionCacheReadResult unavailable() {
        return new UserPermissionCacheReadResult(UserPermissionCacheReadState.UNAVAILABLE, null);
    }
}
