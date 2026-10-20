package com.xcy.internproject.service;

import com.xcy.internproject.cache.ticket.TicketDuplicateGuard;
import com.xcy.internproject.cache.ticket.TicketDuplicateGuardAttempt;
import com.xcy.internproject.exception.DuplicateSubmissionException;
import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.repository.InMemoryTicketRepository;
import com.xcy.internproject.repository.TicketPageQuery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketServiceDuplicateGuardTest {

    @Test
    void rejectsDuplicateBeforeWritingRepository() {
        InMemoryTicketRepository repository = new InMemoryTicketRepository();
        RecordingGuard guard = new RecordingGuard(TicketDuplicateGuardAttempt.duplicate());
        TicketService service = new TicketService(repository, guard);

        assertThatThrownBy(() -> service.createTicket("重复工单", "重复描述"))
                .isInstanceOf(DuplicateSubmissionException.class)
                .hasMessage("相同工单已在短时间内提交，请勿重复提交");
        assertThat(repository.findPage(new TicketPageQuery(null, null, 0, 20)).getTotal())
                .isZero();
    }

    @Test
    void createsTicketWhenRedisGuardIsUnavailable() {
        InMemoryTicketRepository repository = new InMemoryTicketRepository();
        RecordingGuard guard = new RecordingGuard(TicketDuplicateGuardAttempt.unavailable());
        TicketService service = new TicketService(repository, guard);

        Ticket ticket = service.createTicket("Redis 故障", "核心创建仍然可用");

        assertThat(ticket.getId()).isPositive();
        assertThat(repository.findById(ticket.getId())).contains(ticket);
    }

    @Test
    void keepsGuardKeyAfterSuccessfulSave() {
        InMemoryTicketRepository repository = new InMemoryTicketRepository();
        RecordingGuard guard = new RecordingGuard(
                TicketDuplicateGuardAttempt.acquired("fingerprint", "owner-token"));
        TicketService service = new TicketService(repository, guard);

        service.createTicket("创建成功", "防重 Key 保留到窗口结束");

        assertThat(guard.releases).isEmpty();
    }

    @Test
    void releasesOnlyAcquiredFingerprintAndTokenWhenRepositorySaveFails() {
        RecordingGuard guard = new RecordingGuard(
                TicketDuplicateGuardAttempt.acquired("fingerprint", "owner-token"));
        TicketService service = new TicketService(new FailingTicketRepository(), guard);

        assertThatThrownBy(() -> service.createTicket("保存失败", "允许调用方重试"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据库写入失败");
        assertThat(guard.releases)
                .containsExactly(new ReleasedOwner("fingerprint", "owner-token"));
    }

    private static final class RecordingGuard implements TicketDuplicateGuard {

        private final TicketDuplicateGuardAttempt attempt;
        private final List<ReleasedOwner> releases = new ArrayList<>();

        private RecordingGuard(TicketDuplicateGuardAttempt attempt) {
            this.attempt = attempt;
        }

        @Override
        public TicketDuplicateGuardAttempt tryAcquire(String title, String description) {
            return attempt;
        }

        @Override
        public boolean release(String fingerprint, String token) {
            releases.add(new ReleasedOwner(fingerprint, token));
            return true;
        }
    }

    private static final class FailingTicketRepository extends InMemoryTicketRepository {

        @Override
        public Ticket save(String title, String description) {
            throw new IllegalStateException("数据库写入失败");
        }
    }

    private record ReleasedOwner(String fingerprint, String token) {
    }
}
