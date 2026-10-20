package com.xcy.internproject.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache.redis")
public class RedisCacheProperties {

    private boolean enabled;
    private String keyPrefix = "intern:local";
    private Duration documentTtl = Duration.ofMinutes(10);
    private Duration documentTtlJitter = Duration.ofMinutes(2);
    private Duration missingDocumentTtl = Duration.ofMinutes(1);
    private Duration missingDocumentTtlJitter = Duration.ofSeconds(15);
    private Duration userPermissionTtl = Duration.ofMinutes(5);
    private Duration userPermissionTtlJitter = Duration.ofMinutes(1);
    private Duration documentRebuildLockTtl = Duration.ofSeconds(3);
    private Duration documentRebuildWaitTimeout = Duration.ofMillis(300);
    private Duration documentRebuildPollInterval = Duration.ofMillis(20);
    private Duration ticketDuplicateWindow = Duration.ofSeconds(10);
    private long maxDocumentCacheBytes = 512L * 1024L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getDocumentTtl() {
        return documentTtl;
    }

    public void setDocumentTtl(Duration documentTtl) {
        this.documentTtl = documentTtl;
    }

    public Duration getDocumentTtlJitter() {
        return documentTtlJitter;
    }

    public void setDocumentTtlJitter(Duration documentTtlJitter) {
        this.documentTtlJitter = documentTtlJitter;
    }

    public Duration getMissingDocumentTtl() {
        return missingDocumentTtl;
    }

    public void setMissingDocumentTtl(Duration missingDocumentTtl) {
        this.missingDocumentTtl = missingDocumentTtl;
    }

    public Duration getMissingDocumentTtlJitter() {
        return missingDocumentTtlJitter;
    }

    public void setMissingDocumentTtlJitter(Duration missingDocumentTtlJitter) {
        this.missingDocumentTtlJitter = missingDocumentTtlJitter;
    }

    public Duration getUserPermissionTtl() {
        return userPermissionTtl;
    }

    public void setUserPermissionTtl(Duration userPermissionTtl) {
        this.userPermissionTtl = userPermissionTtl;
    }

    public Duration getUserPermissionTtlJitter() {
        return userPermissionTtlJitter;
    }

    public void setUserPermissionTtlJitter(Duration userPermissionTtlJitter) {
        this.userPermissionTtlJitter = userPermissionTtlJitter;
    }

    public Duration getDocumentRebuildLockTtl() {
        return documentRebuildLockTtl;
    }

    public void setDocumentRebuildLockTtl(Duration documentRebuildLockTtl) {
        this.documentRebuildLockTtl = documentRebuildLockTtl;
    }

    public Duration getDocumentRebuildWaitTimeout() {
        return documentRebuildWaitTimeout;
    }

    public void setDocumentRebuildWaitTimeout(Duration documentRebuildWaitTimeout) {
        this.documentRebuildWaitTimeout = documentRebuildWaitTimeout;
    }

    public Duration getDocumentRebuildPollInterval() {
        return documentRebuildPollInterval;
    }

    public void setDocumentRebuildPollInterval(Duration documentRebuildPollInterval) {
        this.documentRebuildPollInterval = documentRebuildPollInterval;
    }

    public Duration getTicketDuplicateWindow() {
        return ticketDuplicateWindow;
    }

    public void setTicketDuplicateWindow(Duration ticketDuplicateWindow) {
        this.ticketDuplicateWindow = ticketDuplicateWindow;
    }

    public long getMaxDocumentCacheBytes() {
        return maxDocumentCacheBytes;
    }

    public void setMaxDocumentCacheBytes(long maxDocumentCacheBytes) {
        this.maxDocumentCacheBytes = maxDocumentCacheBytes;
    }
}
