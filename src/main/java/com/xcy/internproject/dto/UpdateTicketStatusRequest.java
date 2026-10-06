package com.xcy.internproject.dto;

import com.xcy.internproject.model.TicketStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateTicketStatusRequest {

    @NotNull(message = "目标状态不能为空")
    private TicketStatus status;

    public UpdateTicketStatusRequest() {
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }
}
