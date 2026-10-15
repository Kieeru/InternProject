package com.xcy.internproject.service;

import com.xcy.internproject.exception.InvalidTicketStatusTransitionException;
import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.model.TicketSummary;
import com.xcy.internproject.repository.PageResult;
import com.xcy.internproject.repository.TicketPageQuery;
import com.xcy.internproject.repository.TicketRepository;
import com.xcy.internproject.repository.mybatis.MyBatisTicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TicketStatusConcurrencyIntegrationTest.ConcurrencyTestConfiguration.class)
@Sql("/db/cleanup.sql")
class TicketStatusConcurrencyIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private SynchronizingTicketRepository synchronizingTicketRepository;

    @Test
    void allowsOnlyOneConcurrentPendingToProcessingTransition() throws Exception {
        Ticket ticket = ticketService.createTicket("并发更新", "两个请求同时更新同一工单");
        synchronizingTicketRepository.armForTwoReaders();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<UpdateAttempt> update = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发更新未在限定时间内开始");
                }
                try {
                    return UpdateAttempt.succeeded(
                            ticketService.updateStatus(ticket.getId(), TicketStatus.PROCESSING));
                } catch (Throwable throwable) {
                    return UpdateAttempt.failed(throwable);
                }
            };

            Future<UpdateAttempt> firstFuture = executor.submit(update);
            Future<UpdateAttempt> secondFuture = executor.submit(update);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<UpdateAttempt> attempts = List.of(
                    firstFuture.get(10, TimeUnit.SECONDS),
                    secondFuture.get(10, TimeUnit.SECONDS));
            List<UpdateAttempt> failedAttempts = attempts.stream()
                    .filter(attempt -> !attempt.isSuccess())
                    .toList();

            assertThat(attempts).filteredOn(UpdateAttempt::isSuccess).hasSize(1);
            assertThat(failedAttempts).hasSize(1);
            assertThat(failedAttempts.getFirst().failure())
                    .isInstanceOf(InvalidTicketStatusTransitionException.class)
                    .hasMessage("不允许工单状态从 PROCESSING 变更为 PROCESSING");
            assertThat(ticketService.getTicket(ticket.getId()).getStatus())
                    .isEqualTo(TicketStatus.PROCESSING);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    record UpdateAttempt(Ticket ticket, Throwable failure) {

        static UpdateAttempt succeeded(Ticket ticket) {
            return new UpdateAttempt(ticket, null);
        }

        static UpdateAttempt failed(Throwable failure) {
            return new UpdateAttempt(null, failure);
        }

        boolean isSuccess() {
            return ticket != null;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyTestConfiguration {

        @Bean
        @Primary
        SynchronizingTicketRepository synchronizingTicketRepository(
                MyBatisTicketRepository delegate) {
            return new SynchronizingTicketRepository(delegate);
        }
    }

    static class SynchronizingTicketRepository implements TicketRepository {

        private final MyBatisTicketRepository delegate;
        private final AtomicReference<CyclicBarrier> readBarrier = new AtomicReference<>();

        SynchronizingTicketRepository(MyBatisTicketRepository delegate) {
            this.delegate = delegate;
        }

        void armForTwoReaders() {
            readBarrier.set(new CyclicBarrier(2));
        }

        @Override
        public Ticket save(String title, String description) {
            return delegate.save(title, description);
        }

        @Override
        public Optional<Ticket> findById(Long id) {
            Optional<Ticket> ticket = delegate.findById(id);
            awaitAfterSnapshotRead();
            return ticket;
        }

        @Override
        public Optional<Ticket> findByIdForUpdate(Long id) {
            awaitBeforeLockingRead();
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

        private void awaitAfterSnapshotRead() {
            awaitAndDisarm();
        }

        private void awaitBeforeLockingRead() {
            awaitAndDisarm();
        }

        private void awaitAndDisarm() {
            CyclicBarrier barrier = readBarrier.get();
            if (barrier == null) {
                return;
            }
            try {
                barrier.await(5, TimeUnit.SECONDS);
                readBarrier.compareAndSet(barrier, null);
            } catch (Exception exception) {
                throw new IllegalStateException("无法同步两个并发工单读取", exception);
            }
        }
    }
}
