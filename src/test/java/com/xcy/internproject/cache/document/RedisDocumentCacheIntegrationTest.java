package com.xcy.internproject.cache.document;

import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.cache.config.RedisCacheProperties;
import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.model.Document;
import com.xcy.internproject.model.DocumentSummary;
import com.xcy.internproject.repository.DocumentPageQuery;
import com.xcy.internproject.repository.DocumentRepository;
import com.xcy.internproject.repository.PageResult;
import com.xcy.internproject.service.DocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"test", "redis-test"})
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
@Sql("/db/cleanup.sql")
class RedisDocumentCacheIntegrationTest {

    private final long documentId = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);

    @Autowired
    private DocumentCache documentCache;

    @Autowired
    private CacheKeyFactory keys;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DocumentRebuildLock rebuildLock;

    @Autowired
    private RedisCacheProperties cacheProperties;

    private Long createdDocumentId;

    @BeforeEach
    void removePossibleStaleDocumentKey() {
        redisTemplate.delete(keys.document(documentId));
        redisTemplate.delete(keys.documentRebuildLock(documentId));
    }

    @AfterEach
    void cleanExactDocumentKey() {
        redisTemplate.delete(keys.document(documentId));
        if (createdDocumentId != null) {
            redisTemplate.delete(keys.document(createdDocumentId));
        }
    }

    @Test
    void roundTripsDocumentJsonWithConfiguredTtlRange() {
        Document original = new Document(documentId, "真实 Redis", "中文正文\n第二行");

        boolean stored = documentCache.put(original);
        DocumentCacheReadResult result = documentCache.get(documentId);
        Long ttlSeconds = redisTemplate.getExpire(keys.document(documentId));

        assertThat(stored).isTrue();
        assertThat(result.state()).isEqualTo(DocumentCacheReadState.HIT);
        assertThat(result.document().getTitle()).isEqualTo("真实 Redis");
        assertThat(result.document().getContent()).isEqualTo("中文正文\n第二行");
        assertThat(ttlSeconds).isBetween(599L, 720L);
    }

    @Test
    void secondServiceReadUsesRedisWithoutCallingRepositoryAgain() {
        CountingDocumentRepository repository = new CountingDocumentRepository(
                new Document(documentId, "缓存旁路", "只回源一次"));
        DocumentService service = new DocumentService(
                repository,
                documentCache,
                new NoOpDocumentRebuildLock(),
                new RedisCacheProperties(),
                event -> { });

        Document first = service.getDocument(documentId);
        Document second = service.getDocument(documentId);

        assertThat(first.getContent()).isEqualTo("只回源一次");
        assertThat(second.getContent()).isEqualTo("只回源一次");
        assertThat(repository.findByIdCalls.get()).isEqualTo(1);
    }

    @Test
    void roundTripsMissingMarkerWithShortTtl() {
        boolean stored = documentCache.putMissing(documentId);
        DocumentCacheReadResult result = documentCache.get(documentId);
        Long ttlSeconds = redisTemplate.getExpire(keys.document(documentId));

        assertThat(stored).isTrue();
        assertThat(result.state()).isEqualTo(DocumentCacheReadState.NEGATIVE_HIT);
        assertThat(ttlSeconds).isBetween(59L, 75L);
    }

    @Test
    void repeatedMissingServiceReadOnlyUsesRepositoryOnce() {
        CountingDocumentRepository repository = new CountingDocumentRepository(null);
        DocumentService service = new DocumentService(
                repository,
                documentCache,
                new NoOpDocumentRebuildLock(),
                new RedisCacheProperties(),
                event -> { });

        assertThatThrownBy(() -> service.getDocument(documentId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getDocument(documentId))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(repository.findByIdCalls.get()).isEqualTo(1);
    }

    @Test
    void createEvictsPreviouslyCachedMissingMarkerAfterCommit() {
        Long currentAutoIncrement = jdbcTemplate.queryForObject("""
                SELECT AUTO_INCREMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'documents'
                """, Long.class);
        assertThat(currentAutoIncrement).isNotNull();
        long futureDocumentId = currentAutoIncrement + 1_000L;
        jdbcTemplate.execute("ALTER TABLE documents AUTO_INCREMENT = " + futureDocumentId);
        assertThat(documentCache.putMissing(futureDocumentId)).isTrue();

        Document created = documentService.createDocument("负缓存后创建", "创建成功后必须清除 MISSING");
        createdDocumentId = created.getId();

        assertThat(created.getId()).isEqualTo(futureDocumentId);
        assertThat(documentCache.get(created.getId()).state())
                .isEqualTo(DocumentCacheReadState.MISS);
        assertThat(documentService.getDocument(created.getId()).getContent())
                .isEqualTo("创建成功后必须清除 MISSING");
    }

    @Test
    void updateEvictsCachedDocumentAfterCommitAndNextReadUsesNewValue() {
        Document created = documentService.createDocument("更新前", "旧正文");
        createdDocumentId = created.getId();
        assertThat(documentCache.put(created)).isTrue();

        documentService.updateDocument(created.getId(), "更新后", "新正文");

        assertThat(documentCache.get(created.getId()).state())
                .isEqualTo(DocumentCacheReadState.MISS);
        assertThat(documentService.getDocument(created.getId()).getContent())
                .isEqualTo("新正文");
    }

    @Test
    void rollbackKeepsOldCacheAndOldDatabaseValue() {
        Document created = documentService.createDocument("回滚前", "旧正文");
        createdDocumentId = created.getId();
        assertThat(documentCache.put(created)).isTrue();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        transactions.executeWithoutResult(status -> {
            documentService.updateDocument(created.getId(), "不应提交", "不应提交的新正文");
            status.setRollbackOnly();
        });

        DocumentCacheReadResult cached = documentCache.get(created.getId());
        assertThat(cached.state()).isEqualTo(DocumentCacheReadState.HIT);
        assertThat(cached.document().getContent()).isEqualTo("旧正文");
        assertThat(documentRepository.findById(created.getId()))
                .get()
                .extracting(Document::getTitle, Document::getContent)
                .containsExactly("回滚前", "旧正文");
    }

    @Test
    void concurrentMissesUseOneRebuilderWithRealRedisLock() throws Exception {
        CountingDocumentRepository repository = new CountingDocumentRepository(
                new Document(documentId, "真实热点 Key", "并发请求只回源一次"),
                Duration.ofMillis(100));
        DocumentService service = new DocumentService(
                repository,
                documentCache,
                rebuildLock,
                cacheProperties,
                event -> { });
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
                    return service.getDocument(documentId);
                }));
            }
            assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<Document> future : futures) {
                assertThat(future.get(2, TimeUnit.SECONDS).getContent())
                        .isEqualTo("并发请求只回源一次");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(repository.findByIdCalls.get()).isEqualTo(1);
    }

    private static final class CountingDocumentRepository implements DocumentRepository {

        private final Document document;
        private final Duration findDelay;
        private final AtomicInteger findByIdCalls = new AtomicInteger();

        private CountingDocumentRepository(Document document) {
            this(document, Duration.ZERO);
        }

        private CountingDocumentRepository(Document document, Duration findDelay) {
            this.document = document;
            this.findDelay = findDelay;
        }

        @Override
        public Document save(String title, String content) {
            throw new UnsupportedOperationException("该测试不执行保存");
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
            return Optional.ofNullable(document)
                    .filter(stored -> stored.getId().equals(id));
        }

        @Override
        public Optional<Document> update(Long id, String title, String content) {
            throw new UnsupportedOperationException("该测试不执行更新");
        }

        @Override
        public PageResult<DocumentSummary> findPage(DocumentPageQuery query) {
            return new PageResult<>(List.of(), 0L);
        }
    }
}
