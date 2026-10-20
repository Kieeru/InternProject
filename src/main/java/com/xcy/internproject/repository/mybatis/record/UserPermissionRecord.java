package com.xcy.internproject.repository.mybatis.record;

public class UserPermissionRecord {

    private Long userId;
    private String permissionCode;

    public UserPermissionRecord() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }
}
