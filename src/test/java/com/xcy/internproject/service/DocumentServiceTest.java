package com.xcy.internproject.service;

import com.xcy.internproject.cache.document.DocumentCache;
import com.xcy.internproject.cache.document.DocumentCacheReadResult;
import com.xcy.internproject.cache.document.DocumentRebuildLock;
import com.xcy.internproject.cache.document.DocumentRebuildLockAttempt;
import com.xcy.internproject.cache.document.NoOpDocumentRebuildLock;
import com.xcy.internproject.cache.config.RedisCacheProperties;
import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.exception.InvalidPaginationException;
import com.xcy.internproject.dto.PageResponse;
import com.xcy.internproject.dto.DocumentSummaryResponse;
import com.xcy.internproject.model.Document;
import com.xcy.internproject.repository.DocumentPageQuery;
import com.xcy.internproject.repository.DocumentRepository;
import com.xcy.internproject.repository.InMemoryDocumentRepository;
import com.xcy.internproject.repository.PageResult;
import com.xcy.internproject.service.event.DocumentCacheInvalidationRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentServiceTest {

    private DocumentService documentService;
    private CountingDocumentRepository documentRepository;
    private InMemoryDocumentCache documentCache;
    private List<Object> publishedEvents;
    private RedisCacheProperties cacheProperties;

    @BeforeEach
    void setUp() {
        documentRepository = new CountingDocumentRepository(new InMemoryDocumentRepository());
        documentCache = new InMemoryDocumentCache();
        publishedEvents = new ArrayList<>();
        cacheProperties = new RedisCacheProperties();
        documentService = new DocumentService(
                documentRepository,
                documentCache,
                new NoOpDocumentRebuildLock(),
                cacheProperties,
                publishedEvents::add);
    }

    @Test
    void createsDocumentWithGeneratedId() {
        Document document = documentService.createDocument("Spring IOC", "IOC manages object dependencies.");

        assertThat(document.getId()).isPositive();
        assertThat(document.getTitle()).isEqualTo("Spring IOC");
        assertThat(document.getContent()).isEqualTo("IOC manages object dependencies.");
    }

    @Test
    void createsDocumentsWithDifferentIds() {
        Document first = documentService.createDocument("Spring IOC", "IOC content");
        Document second = documentService.createDocument("Java Collections", "Collections content");

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void updatesDocumentAndRequestsExactCacheInvalidation() {
        Document created = documentService.createDocument("更新前", "旧正文");
        publishedEvents.clear();

        Document updated = documentService.updateDocument(created.getId(), "更新后", "新正文");

        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getTitle()).isEqualTo("更新后");
        assertThat(updated.getContent()).isEqualTo("新正文");
        assertThat(publishedEvents).containsExactly(
                new DocumentCacheInvalidationRequested(created.getId()));
    }

    @Test
    void rejectsUpdateForMissingDocumentWithoutPublishingInvalidation() {
        assertThatThrownBy(() -> documentService.updateDocument(999L, "更新后", "新正文"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("文档（ID：999）不存在");

        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void returnsExistingDocumentById() {
        Document created = documentService.createDocument("Spring IOC", "IOC content");

        Document found = documentService.getDocument(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getTitle()).isEqualTo("Spring IOC");
        assertThat(found.getContent()).isEqualTo("IOC content");
    }

    @Test
    void returnsCachedDocumentWithoutAccessingRepository() {
        Document cached = new Document(42L, "Redis 缓存", "缓存正文");
        documentCache.documents.put(cached.getId(), cached);

        Document found = documentService.getDocument(cached.getId());

        assertThat(found).isSameAs(cached);
        assertThat(documentRepository.findByIdCalls.get()).isZero();
    }

    @Test
    void fillsCacheAfterMissAndAvoidsSecondRepositoryLookup() {
        Document created = documentService.createDocument("缓存旁路", "第一次回源，第二次命中");

        Document first = documentService.getDocument(created.getId());
        Document second = documentService.getDocument(created.getId());

        assertThat(first.getContent()).isEqualTo("第一次回源，第二次命中");
        assertThat(second).isSameAs(first);
        assertThat(documentRepository.findByIdCalls.get()).isEqualTo(1);
        assertThat(documentCache.putCalls).isEqualTo(1);
    }

    @Test
    void fallsBackToRepositoryWhenCacheIsUnavailable() {
        Document created = documentService.createDocument("Redis 故障降级", "仍从 MySQL 返回");
        documentCache.unavailable = true;

        Document found = documentService.getDocument(created.getId());

        assertThat(found.getContent()).isEqualTo("仍从 MySQL 返回");
        assertThat(documentRepository.findByIdCalls.get()).isEqualTo(1);
    }

    @Test
    void preservesNotFoundSemanticsWhenCacheIsUnavailable() {
        documentCache.unavailable = true;

        assertThatThrownBy(() -> documentService.getDocument(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("文档（ID：999）不存在");

        assertThat(documentRepository.findByIdCalls.get()).isEqualTo(1);
    }

    @Test
    void listsDocumentsWithoutReadingOrWritingDocumentCache() {
        documentService.createDocument("Spring IOC", "正文");

        documentService.listDocuments(null, 1, 20);

        assertThat(documentCache.getCalls).isZero();
        assertThat(documentCache.putCalls).isZero();
    }

    @Test
    void throwsWhenDocumentDoesNotExist() {
        assertThatThrownBy(() -> documentService.getDocument(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("文档（ID：999）不存在");
    }

    @Test
    void cachesMissingDocumentAndAvoidsSecondRepositoryLookup() {
        assertThatThrownBy(() -> documentService.getDocument(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> documentService.getDocument(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(documentRepository.findByIdCalls.get()).isEqualTo(1);
        assertThat(documentCache.putMissingCalls).isEqualTo(1);
    }

    @Test
    void listsDocumentsWithOneBasedPaginationAndTotalPages() {
        documentService.createDocument("Spring IOC", "正文一");
        documentService.createDocument("Java 集合", "正文二");
        Document newest = documentService.createDocument("Spring MVC", "正文三");

        PageResponse<DocumentSummaryResponse> response =
                documentService.listDocuments("Spring", 1, 1);

        assertThat(response.getItems()).containsExactly(
                new DocumentSummaryResponse(newest.getId(), "Spring MVC",
                        response.getItems().getFirst().createdAt()));
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(1);
        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(2);
    }

    @Test
    void normalizesBlankDocumentPrefixToNoFilter() {
        documentService.createDocument("Spring IOC", "正文一");
        documentService.createDocument("Java 集合", "正文二");

        PageResponse<DocumentSummaryResponse> response =
                documentService.listDocuments("   ", 1, 20);

        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getItems()).hasSize(2);
    }

    @Test
    void rejectsInvalidDocumentPage() {
        assertThatThrownBy(() -> documentService.listDocuments(null, 0, 20))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("页码不能小于1");
    }

    @Test
    void concurrentCacheMissesUseOneRepositoryRebuild() throws Exception {
        Document created = documentRepository.save("热点文档", "只允许一次回源");
        documentRepository.findDelay = Duration.ofMillis(100);
        cacheProperties.setDocumentRebuildWaitTimeout(Duration.ofMillis(500));
        cacheProperties.setDocumentRebuildPollInterval(Duration.ofMillis(10));
        documentService = new DocumentService(
                documentRepository,
                documentCache,
                new InMemoryDocumentRebuildLock(),
                cacheProperties,
                publishedEvents::add);
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Document>> futures = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return documentService.getDocument(created.getId());
                }));
            }
            assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<Document> future : futures) {
                assertThat(future.get(2, TimeUnit.SECONDS).getContent())
                        .isEqualTo("只允许一次回源");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(documentRepository.findByIdCalls.get()).isEqualTo(1);
    }

    @Test
    void contendedRebuildFallsBackToRepositoryAfterBoundedWait() {
        Document created = documentRepository.save("超时降级", "不能无限等待");
        cacheProperties.setDocumentRebuildWaitTimeout(Duration.ofMillis(40));
        cacheProperties.setDocumentRebuildPollInterval(Duration.ofMillis(10));
        DocumentRebuildLock alwaysContended = new DocumentRebuildLock() {
            @Override
            public DocumentRebuildLockAttempt tryAcquire(Long documentId) {
                return DocumentRebuildLockAttempt.contended();
            }

            @Override
            public boolean release(Long documentId, String token) {
                return false;
            }
        };
        documentService = new DocumentService(
                documentRepository,
                documentCache,
                alwaysContended,
                cacheProperties,
                publishedEvents::add);

        long startedAt = System.nanoTime();
        Document found = documentService.getDocument(created.getId());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(found.getContent()).isEqualTo("不能无限等待");
        assertThat(elapsed).isLessThan(Duration.ofMillis(500));
        assertThat(documentRepository.findByIdCalls.get()).isEqualTo(1);
    }

    @Test
    void contendedRebuildDoesNotPollCacheAfterWaitDeadline() {
        Document created = documentRepository.save("截止时间", "到期后直接回源");
        cacheProperties.setDocumentRebuildWaitTimeout(Duration.ofMillis(20));
        cacheProperties.setDocumentRebuildPollInterval(Duration.ofMillis(20));
        DocumentRebuildLock alwaysContended = new DocumentRebuildLock() {
            @Override
            public DocumentRebuildLockAttempt tryAcquire(Long documentId) {
                return DocumentRebuildLockAttempt.contended();
            }

            @Override
            public boolean release(Long documentId, String token) {
                return false;
            }
        };
        documentService = new DocumentService(
                documentRepository,
                documentCache,
                alwaysContended,
                cacheProperties,
                publishedEvents::add);

        Document found = documentService.getDocument(created.getId());

        assertThat(found.getContent()).isEqualTo("到期后直接回源");
        assertThat(documentCache.getCalls).isEqualTo(1);
        assertThat(documentRepository.findByIdCalls.get()).isEqualTo(1);
    }

    private static final class InMemoryDocumentCache implements DocumentCache {

        private final Map<Long, Document> documents = new ConcurrentHashMap<>();
        private final Set<Long> missingDocumentIds = ConcurrentHashMap.newKeySet();
        private int getCalls;
        private int putCalls;
        private int putMissingCalls;
        private boolean unavailable;

        @Override
        public DocumentCacheReadResult get(Long documentId) {
            getCalls++;
            if (unavailable) {
                return DocumentCacheReadResult.unavailable();
            }
            if (missingDocumentIds.contains(documentId)) {
                return DocumentCacheReadResult.negativeHit();
            }
            Document document = documents.get(documentId);
            return document == null
                    ? DocumentCacheReadResult.miss()
                    : DocumentCacheReadResult.hit(document);
        }

        @Override
        public boolean put(Document document) {
            putCalls++;
            documents.put(document.getId(), document);
            return true;
        }

        @Override
        public boolean putMissing(Long documentId) {
            putMissingCalls++;
            missingDocumentIds.add(documentId);
            return true;
        }

        @Override
        public boolean evict(Long documentId) {
            return documents.remove(documentId) != null;
        }
    }

    private static final class CountingDocumentRepository implements DocumentRepository {

        private final DocumentRepository delegate;
        private final AtomicInteger findByIdCalls = new AtomicInteger();
        private Duration findDelay = Duration.ZERO;

        private CountingDocumentRepository(DocumentRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Document save(String title, String content) {
            return delegate.save(title, content);
        }

        @Override
        public Optional<Document> findById(Long id) {
            findByIdCalls.incrementAndGet();
            if (!findDelay.isZero()) {
                try {
                    Thread.sleep(findDelay);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.findById(id);
        }

        @Override
        public Optional<Document> update(Long id, String title, String content) {
            return delegate.update(id, title, content);
        }

        @Override
        public PageResult<com.xcy.internproject.model.DocumentSummary> findPage(
                DocumentPageQuery query) {
            return delegate.findPage(query);
        }
    }

    private static final class InMemoryDocumentRebuildLock implements DocumentRebuildLock {

        private final AtomicReference<String> ownerToken = new AtomicReference<>();

        @Override
        public DocumentRebuildLockAttempt tryAcquire(Long documentId) {
            String token = UUID.randomUUID().toString();
            return ownerToken.compareAndSet(null, token)
                    ? DocumentRebuildLockAttempt.acquired(token)
                    : DocumentRebuildLockAttempt.contended();
        }

        @Override
        public boolean release(Long documentId, String token) {
            return ownerToken.compareAndSet(token, null);
        }
    }
}
