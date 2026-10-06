package com.xcy.internproject.model;

public class Ticket {

    private final Long id;
    private final String title;
    private final String description;
    private final TicketStatus status;

    public Ticket(Long id, String title, String description, TicketStatus status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketStatus getStatus() {
        return status;
    }
}
