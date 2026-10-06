package com.xcy.internproject.model;

public class Document {

    private final Long id;
    private final String title;
    private final String content;

    public Document(Long id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }
}
