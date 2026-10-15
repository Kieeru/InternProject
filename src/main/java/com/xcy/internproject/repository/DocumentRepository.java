package com.xcy.internproject.repository;

import com.xcy.internproject.model.Document;
import com.xcy.internproject.model.DocumentSummary;

import java.util.Optional;

public interface DocumentRepository {

    Document save(String title, String content);

    Optional<Document> findById(Long id);

    PageResult<DocumentSummary> findPage(DocumentPageQuery query);
}
