package com.xcy.internproject.repository;

import com.xcy.internproject.model.Document;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryDocumentRepository implements DocumentRepository {

    private final ConcurrentMap<Long, Document> documents = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    @Override
    public Document save(String title, String content) {
        long id = idSequence.incrementAndGet();
        Document document = new Document(id, title, content);
        documents.put(id, document);
        return document;
    }

    @Override
    public Optional<Document> findById(Long id) {
        return Optional.ofNullable(documents.get(id));
    }
}
