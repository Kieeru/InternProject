package com.xcy.internproject.repository;

import com.xcy.internproject.model.Document;

import java.util.Optional;

public interface DocumentRepository {

    Document save(String title, String content);

    Optional<Document> findById(Long id);
}
