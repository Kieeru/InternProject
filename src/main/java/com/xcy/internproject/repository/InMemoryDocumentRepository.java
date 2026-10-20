package com.xcy.internproject.repository;

import com.xcy.internproject.model.Document;
import com.xcy.internproject.model.DocumentSummary;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryDocumentRepository implements DocumentRepository {

    private final ConcurrentMap<Long, StoredDocument> documents = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    @Override
    public Document save(String title, String content) {
        long id = idSequence.incrementAndGet();
        Document document = new Document(id, title, content);
        documents.put(id, new StoredDocument(document, LocalDateTime.now()));
        return document;
    }

    @Override
    public Optional<Document> findById(Long id) {
        return Optional.ofNullable(documents.get(id)).map(StoredDocument::document);
    }

    @Override
    public Optional<Document> update(Long id, String title, String content) {
        StoredDocument updated = documents.computeIfPresent(id, (key, stored) ->
                new StoredDocument(new Document(id, title, content), stored.createdAt()));
        return Optional.ofNullable(updated).map(StoredDocument::document);
    }

    @Override
    public PageResult<DocumentSummary> findPage(DocumentPageQuery query) {
        var matchingDocuments = documents.values().stream()
                .filter(stored -> query.titlePrefix() == null
                        || query.titlePrefix().isEmpty()
                        || stored.document().getTitle().startsWith(query.titlePrefix()))
                .sorted(Comparator.comparing(StoredDocument::createdAt)
                        .thenComparing(stored -> stored.document().getId())
                        .reversed())
                .toList();
        var items = matchingDocuments.stream()
                .skip(query.offset())
                .limit(query.limit())
                .map(stored -> new DocumentSummary(
                        stored.document().getId(),
                        stored.document().getTitle(),
                        stored.createdAt()))
                .toList();
        return new PageResult<>(items, matchingDocuments.size());
    }

    private record StoredDocument(Document document, LocalDateTime createdAt) {
    }
}
