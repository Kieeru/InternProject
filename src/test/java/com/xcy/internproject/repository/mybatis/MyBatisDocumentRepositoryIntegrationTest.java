package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.model.Document;
import com.xcy.internproject.model.DocumentSummary;
import com.xcy.internproject.repository.DocumentPageQuery;
import com.xcy.internproject.repository.DocumentRepository;
import com.xcy.internproject.repository.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql("/db/cleanup.sql")
class MyBatisDocumentRepositoryIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesDocumentWithGeneratedId() {
        Document saved = documentRepository.save("Spring IOC", "IOC 管理对象依赖。");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getTitle()).isEqualTo("Spring IOC");
        assertThat(saved.getContent()).isEqualTo("IOC 管理对象依赖。");
    }

    @Test
    void findsActiveDocumentById() {
        Document saved = documentRepository.save("集合框架", "List、Set 和 Map 是常用集合接口。");

        Optional<Document> found = documentRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getTitle()).isEqualTo("集合框架");
        assertThat(found.get().getContent()).isEqualTo("List、Set 和 Map 是常用集合接口。");
    }

    @Test
    void roundTripsChineseLongText() {
        String content = "文档正文包含中文、English 和标点符号。".repeat(60)
                + "\n第二行仍然需要完整保留。";
        assertThat(content.length()).isGreaterThan(1_000);

        Document saved = documentRepository.save("长文本往返测试", content);

        Optional<Document> found = documentRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo(content);
    }

    @Test
    void doesNotFindLogicallyDeletedDocument() {
        Document saved = documentRepository.save("逻辑删除", "这篇文档将被标记删除。");

        int updatedRows = jdbcTemplate.update(
                "UPDATE documents SET deleted = 1 WHERE id = ?", saved.getId());
        assertThat(updatedRows).isEqualTo(1);

        Optional<Document> found = documentRepository.findById(saved.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void listsDocumentsByCreatedTimeAndIdDescending() {
        LocalDateTime older = LocalDateTime.of(2026, 10, 13, 9, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long olderId = insertDocument("较早文档", older, false);
        Long firstNewerId = insertDocument("同时间文档一", newer, false);
        Long secondNewerId = insertDocument("同时间文档二", newer, false);

        PageResult<DocumentSummary> result = documentRepository.findPage(
                new DocumentPageQuery(null, 0, 20));

        assertThat(result.getItems())
                .extracting(DocumentSummary::id)
                .containsExactly(secondNewerId, firstNewerId, olderId);
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    void filtersDocumentsByTitlePrefixAndKeepsCountConsistent() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long firstMatch = insertDocument("Spring Boot 入门", createdAt, false);
        Long secondMatch = insertDocument("Spring MVC 指南", createdAt.plusMinutes(1), false);
        insertDocument("MyBatis 指南", createdAt.plusMinutes(2), false);
        insertDocument("Spring 已删除", createdAt.plusMinutes(3), true);

        PageResult<DocumentSummary> result = documentRepository.findPage(
                new DocumentPageQuery("Spring", 0, 20));

        assertThat(result.getItems())
                .extracting(DocumentSummary::id)
                .containsExactly(secondMatch, firstMatch);
        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    void treatsLikeWildcardsInDocumentPrefixAsLiteralText() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long literalMatch = insertDocument("Spring_%=专题", createdAt, false);
        insertDocument("SpringABC=专题", createdAt.plusMinutes(1), false);

        PageResult<DocumentSummary> result = documentRepository.findPage(
                new DocumentPageQuery("Spring_%=", 0, 20));

        assertThat(result.getItems())
                .extracting(DocumentSummary::id)
                .containsExactly(literalMatch);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void treatsEmptyDocumentPrefixAsNoFilter() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        insertDocument("Spring", createdAt, false);
        insertDocument("MyBatis", createdAt.plusMinutes(1), false);

        PageResult<DocumentSummary> result = documentRepository.findPage(
                new DocumentPageQuery("", 0, 20));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    void returnsRequestedDocumentPage() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long first = insertDocument("第一页末尾", createdAt, false);
        Long second = insertDocument("第二页目标", createdAt.plusMinutes(1), false);
        Long third = insertDocument("排序第一", createdAt.plusMinutes(2), false);

        PageResult<DocumentSummary> result = documentRepository.findPage(
                new DocumentPageQuery(null, 1, 1));

        assertThat(result.getItems())
                .extracting(DocumentSummary::id)
                .containsExactly(second);
        assertThat(result.getItems())
                .extracting(DocumentSummary::id)
                .doesNotContain(first, third);
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    void excludesLogicallyDeletedDocumentsFromItemsAndTotal() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long activeId = insertDocument("有效文档", createdAt, false);
        insertDocument("已删除文档", createdAt.plusMinutes(1), true);

        PageResult<DocumentSummary> result = documentRepository.findPage(
                new DocumentPageQuery(null, 0, 20));

        assertThat(result.getItems())
                .extracting(DocumentSummary::id)
                .containsExactly(activeId);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    private Long insertDocument(String title, LocalDateTime createdAt, boolean deleted) {
        jdbcTemplate.update(
                "INSERT INTO documents (title, content, created_at, deleted) VALUES (?, ?, ?, ?)",
                title, "分页测试正文", createdAt, deleted ? 1 : 0);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM documents WHERE title = ? ORDER BY id DESC LIMIT 1",
                Long.class, title);
    }
}
