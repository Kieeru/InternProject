package com.xcy.internproject.cache.config;

public final class CacheKeyFactory {

    private final String prefix;

    public CacheKeyFactory(RedisCacheProperties properties) {
        this.prefix = properties.getKeyPrefix();
    }

    public String document(Long documentId) {
        return prefix + ":document:v1:" + documentId;
    }

    public String documentRebuildLock(Long documentId) {
        return prefix + ":document-rebuild-lock:v1:" + documentId;
    }

    public String userPermissions(Long userId) {
        return prefix + ":user-permissions:v1:" + userId;
    }

    public String ticketDuplicate(String fingerprint) {
        return prefix + ":ticket-dedupe:v1:" + fingerprint;
    }
}
