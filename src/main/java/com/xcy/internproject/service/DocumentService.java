package com.xcy.internproject.service;

import com.xcy.internproject.cache.document.DocumentCache;
import com.xcy.internproject.cache.document.DocumentCacheReadResult;
import com.xcy.internproject.cache.document.DocumentCacheReadState;
import com.xcy.internproject.cache.document.DocumentRebuildLock;
import com.xcy.internproject.cache.document.DocumentRebuildLockAttempt;
import com.xcy.internproject.cache.document.DocumentRebuildLockState;
import com.xcy.internproject.cache.config.RedisCacheProperties;
import com.xcy.internproject.dto.DocumentSummaryResponse;
import com.xcy.internproject.dto.PageResponse;
import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.model.Document;
import com.xcy.internproject.model.PageRequest;
import com.xcy.internproject.repository.DocumentPageQuery;
import com.xcy.internproject.repository.DocumentRepository;
import com.xcy.internproject.service.event.DocumentCacheInvalidationRequested;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentCache documentCache;
    private final DocumentRebuildLock rebuildLock;
    private final RedisCacheProperties cacheProperties;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentCache documentCache,
            DocumentRebuildLock rebuildLock,
            RedisCacheProperties cacheProperties,
            ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.documentCache = documentCache;
        this.rebuildLock = rebuildLock;
        this.cacheProperties = cacheProperties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Document createDocument(String title, String content) {
        Document document = documentRepository.save(title, content);
        eventPublisher.publishEvent(new DocumentCacheInvalidationRequested(document.getId()));
        return document;
    }

    @Transactional
    public Document updateDocument(Long id, String title, String content) {
        Document document = documentRepository.update(id, title, content)
                .orElseThrow(() -> new ResourceNotFoundException("文档", id));
        eventPublisher.publishEvent(new DocumentCacheInvalidationRequested(document.getId()));
        return document;
    }

    public Document getDocument(Long id) {
        DocumentCacheReadResult cached = documentCache.get(id);
        if (cached.state() == DocumentCacheReadState.HIT) {
            return cached.document();
        }
        if (cached.state() == DocumentCacheReadState.NEGATIVE_HIT) {
            throw new ResourceNotFoundException("文档", id);
        }

        if (cached.state() == DocumentCacheReadState.UNAVAILABLE) {
            return loadFromRepository(id);
        }

        DocumentRebuildLockAttempt lockAttempt = rebuildLock.tryAcquire(id);
        if (lockAttempt.state() == DocumentRebuildLockState.UNAVAILABLE) {
            return loadFromRepository(id);
        }
        if (lockAttempt.state() == DocumentRebuildLockState.CONTENDED) {
            return waitForRebuildOrLoad(id);
        }

        try {
            DocumentCacheReadResult rechecked = documentCache.get(id);
            if (rechecked.state() == DocumentCacheReadState.HIT) {
                return rechecked.document();
            }
            if (rechecked.state() == DocumentCacheReadState.NEGATIVE_HIT) {
                throw new ResourceNotFoundException("文档", id);
            }
            return loadFromRepository(id);
        } finally {
            rebuildLock.release(id, lockAttempt.token());
        }
    }

    private Document waitForRebuildOrLoad(Long id) {
        long waitNanos = Math.max(0L, cacheProperties.getDocumentRebuildWaitTimeout().toNanos());
        long pollNanos = Math.max(1L, cacheProperties.getDocumentRebuildPollInterval().toNanos());
        long deadline = System.nanoTime() + waitNanos;
        while (System.nanoTime() < deadline) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0L) {
                break;
            }
            try {
                Thread.sleep(java.time.Duration.ofNanos(Math.min(pollNanos, remainingNanos)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
            DocumentCacheReadResult cached = documentCache.get(id);
            if (cached.state() == DocumentCacheReadState.HIT) {
                return cached.document();
            }
            if (cached.state() == DocumentCacheReadState.NEGATIVE_HIT) {
                throw new ResourceNotFoundException("文档", id);
            }
            if (cached.state() == DocumentCacheReadState.UNAVAILABLE) {
                break;
            }
        }
        return loadFromRepository(id);
    }

    private Document loadFromRepository(Long id) {
        var storedDocument = documentRepository.findById(id);
        if (storedDocument.isEmpty()) {
            documentCache.putMissing(id);
            throw new ResourceNotFoundException("文档", id);
        }
        Document document = storedDocument.get();
        documentCache.put(document);
        return document;
    }

    public PageResponse<DocumentSummaryResponse> listDocuments(
            String titlePrefix, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        String normalizedPrefix = normalizeFilter(titlePrefix);
        var result = documentRepository.findPage(new DocumentPageQuery(
                normalizedPrefix,
                pageRequest.getOffset(),
                pageRequest.getSize()));
        var items = result.getItems().stream()
                .map(summary -> new DocumentSummaryResponse(
                        summary.id(), summary.title(), summary.createdAt()))
                .toList();
        return PageResponse.of(items, pageRequest.getPage(), pageRequest.getSize(), result.getTotal());
    }

    private static String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
