package com.xcy.internproject.exception;

import com.xcy.internproject.model.TicketStatus;

public class InvalidTicketStatusTransitionException extends RuntimeException {

    public InvalidTicketStatusTransitionException(TicketStatus currentStatus, TicketStatus targetStatus) {
        super("不允许工单状态从 " + currentStatus + " 变更为 " + targetStatus);
    }
}
