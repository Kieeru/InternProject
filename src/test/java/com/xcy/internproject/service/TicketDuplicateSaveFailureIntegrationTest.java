package com.xcy.internproject.service;

import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.cache.config.RedisCacheProperties;
import com.xcy.internproject.cache.ticket.TicketRequestFingerprint;
import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.model.TicketSummary;
import com.xcy.internproject.repository.PageResult;
import com.xcy.internproject.repository.TicketPageQuery;
import com.xcy.internproject.repository.TicketRepository;
import com.xcy.internproject.repository.mybatis.MyBatisTicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"test", "redis-test"})
@Import(TicketDuplicateSaveFailureIntegrationTest.FailureTestConfiguration.class)
@Sql("/db/cleanup.sql")
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
class TicketDuplicateSaveFailureIntegrationTest {

    private static final String REPLACEMENT_TOKEN = "replacement-owner-token";

    private final Set<String> exactKeys = new HashSet<>();

    @Autowired
    private TicketService ticketService;

    @Autowired
    private FailingTicketRepository failingRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheKeyFactory keys;

    @Autowired
    private TicketRequestFingerprint fingerprint;

    @AfterEach
    void cleanExactKeys() {
        exactKeys.forEach(redisTemplate::delete);
        failingRepository.replaceTokenBeforeFailure = false;
    }

    @Test
    void removesOwnedKeyWhenMysqlSaveFails() {
        String suffix = UUID.randomUUID().toString();
        String title = "保存失败释放-" + suffix;
        String description = "owner token 应删除-" + suffix;
        String key = track(title, description);

        assertThatThrownBy(() -> ticketService.createTicket(title, description))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟 MySQL 保存失败");
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    void doesNotDeleteKeyReplacedByAnotherOwnerAndKeepsMysqlFailure() {
        String suffix = UUID.randomUUID().toString();
        String title = "保存失败换主-" + suffix;
        String description = "旧 token 不得误删-" + suffix;
        String key = track(title, description);
        failingRepository.replaceTokenBeforeFailure = true;

        assertThatThrownBy(() -> ticketService.createTicket(title, description))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟 MySQL 保存失败");
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(REPLACEMENT_TOKEN);
    }

    private String track(String title, String description) {
        String key = keys.ticketDuplicate(fingerprint.calculate(title, description));
        exactKeys.add(key);
        return key;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureTestConfiguration {

        @Bean
        @Primary
        FailingTicketRepository failingTicketRepository(
                MyBatisTicketRepository delegate,
                StringRedisTemplate redisTemplate,
                CacheKeyFactory keys,
                RedisCacheProperties properties,
                TicketRequestFingerprint fingerprint) {
            return new FailingTicketRepository(
                    delegate, redisTemplate, keys, properties, fingerprint);
        }
    }

    static final class FailingTicketRepository implements TicketRepository {

        private final MyBatisTicketRepository delegate;
        private final StringRedisTemplate redisTemplate;
        private final CacheKeyFactory keys;
        private final RedisCacheProperties properties;
        private final TicketRequestFingerprint fingerprint;
        private boolean replaceTokenBeforeFailure;

        private FailingTicketRepository(
                MyBatisTicketRepository delegate,
                StringRedisTemplate redisTemplate,
                CacheKeyFactory keys,
                RedisCacheProperties properties,
                TicketRequestFingerprint fingerprint) {
            this.delegate = delegate;
            this.redisTemplate = redisTemplate;
            this.keys = keys;
            this.properties = properties;
            this.fingerprint = fingerprint;
        }

        @Override
        public Ticket save(String title, String description) {
            if (replaceTokenBeforeFailure) {
                redisTemplate.opsForValue().set(
                        keys.ticketDuplicate(fingerprint.calculate(title, description)),
                        REPLACEMENT_TOKEN,
                        properties.getTicketDuplicateWindow());
            }
            throw new IllegalStateException("模拟 MySQL 保存失败");
        }

        @Override
        public Optional<Ticket> findById(Long id) {
            return delegate.findById(id);
        }

        @Override
        public Optional<Ticket> findByIdForUpdate(Long id) {
            return delegate.findByIdForUpdate(id);
        }

        @Override
        public Ticket updateStatus(Long id, TicketStatus status) {
            return delegate.updateStatus(id, status);
        }

        @Override
        public PageResult<TicketSummary> findPage(TicketPageQuery query) {
            return delegate.findPage(query);
        }
    }
}
