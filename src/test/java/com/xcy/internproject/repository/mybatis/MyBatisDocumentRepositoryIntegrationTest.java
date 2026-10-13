package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.model.Document;
import com.xcy.internproject.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

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
}
