package com.xcy.internproject.service;

import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.cache.ticket.TicketRequestFingerprint;
import com.xcy.internproject.exception.DuplicateSubmissionException;
import com.xcy.internproject.model.Ticket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.cache.redis.ticket-duplicate-window=400ms")
@ActiveProfiles({"test", "redis-test"})
@Sql("/db/cleanup.sql")
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
class TicketDuplicateConcurrencyIntegrationTest {

    private final Set<String> exactKeys = new HashSet<>();

    @Autowired
    private TicketService ticketService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheKeyFactory keys;

    @Autowired
    private TicketRequestFingerprint fingerprint;

    @AfterEach
    void cleanExactKeys() {
        exactKeys.forEach(redisTemplate::delete);
    }

    @Test
    void createsOnlyOneTicketForEightConcurrentIdenticalRequests() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String title = "并发重复-" + suffix;
        String description = "八个请求只写一条-" + suffix;
        track(title, description);
        int requestCount = 8;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            Callable<CreationAttempt> create = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发工单创建未在限定时间内开始");
                }
                try {
                    return CreationAttempt.succeeded(
                            ticketService.createTicket(title, description));
                } catch (Throwable throwable) {
                    return CreationAttempt.failed(throwable);
                }
            };
            List<Future<CreationAttempt>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(create));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<CreationAttempt> attempts = new ArrayList<>();
            for (Future<CreationAttempt> future : futures) {
                attempts.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(attempts).filteredOn(CreationAttempt::isSuccess).hasSize(1);
            assertThat(attempts).filteredOn(attempt -> !attempt.isSuccess())
                    .extracting(CreationAttempt::failure)
                    .allSatisfy(failure -> assertThat(failure)
                            .isInstanceOf(DuplicateSubmissionException.class))
                    .hasSize(7);
            assertThat(countTickets(title, description)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void allowsDifferentContentWithoutMutualBlocking() {
        String suffix = UUID.randomUUID().toString();
        String title = "不同内容-" + suffix;
        String firstDescription = "描述一-" + suffix;
        String secondDescription = "描述二-" + suffix;
        track(title, firstDescription);
        track(title, secondDescription);

        Ticket first = ticketService.createTicket(title, firstDescription);
        Ticket second = ticketService.createTicket(title, secondDescription);

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(countTickets(title, firstDescription)).isEqualTo(1);
        assertThat(countTickets(title, secondDescription)).isEqualTo(1);
    }

    @Test
    void allowsSameContentAgainAfterConfiguredWindowExpires() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String title = "窗口到期-" + suffix;
        String description = "到期后允许再次创建-" + suffix;
        String key = track(title, description);
        Ticket first = ticketService.createTicket(title, description);

        assertThatThrownBy(() -> ticketService.createTicket(title, description))
                .isInstanceOf(DuplicateSubmissionException.class);
        awaitKeyAbsent(key, Duration.ofSeconds(3));

        Ticket second = ticketService.createTicket(title, description);
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(countTickets(title, description)).isEqualTo(2);
    }

    private String track(String title, String description) {
        String key = keys.ticketDuplicate(fingerprint.calculate(title, description));
        exactKeys.add(key);
        return key;
    }

    private int countTickets(String title, String description) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE title = ? AND description = ?",
                Integer.class,
                title,
                description);
        return count == null ? 0 : count;
    }

    private void awaitKeyAbsent(String key, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (Boolean.TRUE.equals(redisTemplate.hasKey(key)) && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    private record CreationAttempt(Ticket ticket, Throwable failure) {

        private static CreationAttempt succeeded(Ticket ticket) {
            return new CreationAttempt(ticket, null);
        }

        private static CreationAttempt failed(Throwable failure) {
            return new CreationAttempt(null, failure);
        }

        private boolean isSuccess() {
            return ticket != null;
        }
    }
}
