package com.xcy.internproject.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + "（ID：" + id + "）不存在");
    }
}
