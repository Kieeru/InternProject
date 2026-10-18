package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.model.Document;
import com.xcy.internproject.model.DocumentSummary;
import com.xcy.internproject.repository.DocumentPageQuery;
import com.xcy.internproject.repository.DocumentRepository;
import com.xcy.internproject.repository.PageResult;
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

    @Override
    public Optional<Document> update(Long id, String title, String content) {
        int updatedRows = documentMapper.update(id, title, content);
        if (updatedRows == 0) {
            return Optional.empty();
        }
        if (updatedRows != 1) {
            throw new IllegalStateException("文档更新失败，受影响行数：" + updatedRows);
        }
        return findById(id);
    }

    @Override
    public PageResult<DocumentSummary> findPage(DocumentPageQuery query) {
        DocumentPageQuery escapedQuery = new DocumentPageQuery(
                escapeLikeLiteral(query.titlePrefix()),
                query.offset(),
                query.limit());
        var items = documentMapper.findPage(escapedQuery).stream()
                .map(record -> new DocumentSummary(
                        record.getId(),
                        record.getTitle(),
                        record.getCreatedAt()))
                .toList();
        return new PageResult<>(items, documentMapper.count(escapedQuery));
    }

    private static String escapeLikeLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("=", "==")
                .replace("%", "=%")
                .replace("_", "=_");
    }

    private static Document toDocument(DocumentRecord record) {
        return new Document(record.getId(), record.getTitle(), record.getContent());
    }
}
