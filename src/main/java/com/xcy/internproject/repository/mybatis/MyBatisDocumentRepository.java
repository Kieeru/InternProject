package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.model.Document;
import com.xcy.internproject.repository.DocumentRepository;
import com.xcy.internproject.repository.mybatis.mapper.DocumentMapper;
import com.xcy.internproject.repository.mybatis.record.DocumentRecord;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisDocumentRepository implements DocumentRepository {

    private final DocumentMapper documentMapper;

    public MyBatisDocumentRepository(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Override
    public Document save(String title, String content) {
        DocumentRecord record = new DocumentRecord();
        record.setTitle(title);
        record.setContent(content);

        documentMapper.insert(record);

        return toDocument(record);
    }

    @Override
    public Optional<Document> findById(Long id) {
        return Optional.ofNullable(documentMapper.findById(id))
                .map(MyBatisDocumentRepository::toDocument);
    }

    private static Document toDocument(DocumentRecord record) {
        return new Document(record.getId(), record.getTitle(), record.getContent());
    }
}
