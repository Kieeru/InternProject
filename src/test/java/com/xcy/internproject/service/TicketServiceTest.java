package com.xcy.internproject.service;

import com.xcy.internproject.exception.InvalidTicketStatusTransitionException;
import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.exception.InvalidPaginationException;
import com.xcy.internproject.dto.PageResponse;
import com.xcy.internproject.dto.TicketSummaryResponse;
import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.repository.InMemoryTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketServiceTest {

    private InMemoryTicketRepository ticketRepository;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketRepository = new InMemoryTicketRepository();
        ticketService = new TicketService(ticketRepository);
    }

    @Test
    void createsTicketWithGeneratedIdAndPendingStatus() {
        Ticket ticket = ticketService.createTicket("工单1", "工单内容");

        assertThat(ticket.getId()).isPositive();
        assertThat(ticket.getTitle()).isEqualTo("工单1");
        assertThat(ticket.getDescription()).isEqualTo("工单内容");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING);
    }

    @Test
    void createsTicketsWithDifferentIds() {
        Ticket first = ticketService.createTicket("工单1", "第一个工单");
        Ticket second = ticketService.createTicket("工单2", "第二个工单");

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void returnsExistingTicketById() {
        Ticket created = ticketService.createTicket("工单1", "工单内容");

        Ticket found = ticketService.getTicket(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getTitle()).isEqualTo("工单1");
        assertThat(found.getDescription()).isEqualTo("工单内容");
        assertThat(found.getStatus()).isEqualTo(TicketStatus.PENDING);
    }

    @Test
    void throwsWhenGettingMissingTicket() {
        assertThatThrownBy(() -> ticketService.getTicket(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("工单（ID：999）不存在");
    }

    @Test
    void movesTicketThroughAllowedStatusSequence() {
        Ticket ticket = ticketService.createTicket("无法登录", "用户无法登录系统");

        Ticket processing = ticketService.updateStatus(ticket.getId(), TicketStatus.PROCESSING);
        Ticket resolved = ticketService.updateStatus(ticket.getId(), TicketStatus.RESOLVED);
        Ticket closed = ticketService.updateStatus(ticket.getId(), TicketStatus.CLOSED);

        assertThat(processing.getStatus()).isEqualTo(TicketStatus.PROCESSING);
        assertThat(resolved.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(closed.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticketRepository.findById(ticket.getId())).contains(closed);
    }

    @Test
    void rejectsSkippingAStatus() {
        Ticket ticket = ticketService.createTicket("无法登录", "用户无法登录系统");

        assertThatThrownBy(() -> ticketService.updateStatus(ticket.getId(), TicketStatus.RESOLVED))
                .isInstanceOf(InvalidTicketStatusTransitionException.class)
                .hasMessage("不允许工单状态从 PENDING 变更为 RESOLVED");

        assertThat(ticketRepository.findById(ticket.getId()))
                .get()
                .extracting(Ticket::getStatus)
                .isEqualTo(TicketStatus.PENDING);
    }

    @Test
    void rejectsKeepingTheSameStatus() {
        Ticket ticket = ticketService.createTicket("无法登录", "用户无法登录系统");

        assertThatThrownBy(() -> ticketService.updateStatus(ticket.getId(), TicketStatus.PENDING))
                .isInstanceOf(InvalidTicketStatusTransitionException.class)
                .hasMessage("不允许工单状态从 PENDING 变更为 PENDING");
    }

    @Test
    void rejectsMovingStatusBackward() {
        Ticket ticket = ticketService.createTicket("无法登录", "用户无法登录系统");
        ticketService.updateStatus(ticket.getId(), TicketStatus.PROCESSING);
        ticketService.updateStatus(ticket.getId(), TicketStatus.RESOLVED);

        assertThatThrownBy(() -> ticketService.updateStatus(ticket.getId(), TicketStatus.PROCESSING))
                .isInstanceOf(InvalidTicketStatusTransitionException.class)
                .hasMessage("不允许工单状态从 RESOLVED 变更为 PROCESSING");
    }

    @Test
    void rejectsUpdatingMissingTicket() {
        assertThatThrownBy(() -> ticketService.updateStatus(999L, TicketStatus.PROCESSING))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("工单（ID：999）不存在");
    }

    @Test
    void listsTicketsWithCombinedFiltersAndPaginationMetadata() {
        Ticket first = ticketService.createTicket("登录故障一", "描述一");
        ticketService.updateStatus(first.getId(), TicketStatus.PROCESSING);
        ticketService.createTicket("登录故障二", "描述二");
        Ticket newest = ticketService.createTicket("登录故障三", "描述三");
        ticketService.updateStatus(newest.getId(), TicketStatus.PROCESSING);

        PageResponse<TicketSummaryResponse> response =
                ticketService.listTickets(TicketStatus.PROCESSING, "登录", 1, 1);

        assertThat(response.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(newest.getId());
                    assertThat(item.title()).isEqualTo("登录故障三");
                    assertThat(item.status()).isEqualTo(TicketStatus.PROCESSING);
                    assertThat(item.createdAt()).isNotNull();
                });
        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(2);
    }

    @Test
    void normalizesBlankTicketKeywordToNoFilter() {
        ticketService.createTicket("登录故障", "描述一");
        ticketService.createTicket("网络故障", "描述二");

        PageResponse<TicketSummaryResponse> response =
                ticketService.listTickets(null, "  ", 1, 20);

        assertThat(response.getTotal()).isEqualTo(2);
    }

    @Test
    void rejectsTicketPageSizeAboveMaximum() {
        assertThatThrownBy(() -> ticketService.listTickets(null, null, 1, 101))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("每页条数必须在1到100之间");
    }
}
