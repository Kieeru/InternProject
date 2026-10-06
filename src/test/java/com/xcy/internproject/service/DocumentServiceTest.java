package com.xcy.internproject.service;

import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.model.Document;
import com.xcy.internproject.repository.InMemoryDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentServiceTest {

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(new InMemoryDocumentRepository());
    }

    @Test
    void createsDocumentWithGeneratedId() {
        Document document = documentService.createDocument("Spring IOC", "IOC manages object dependencies.");

        assertThat(document.getId()).isPositive();
        assertThat(document.getTitle()).isEqualTo("Spring IOC");
        assertThat(document.getContent()).isEqualTo("IOC manages object dependencies.");
    }

    @Test
    void createsDocumentsWithDifferentIds() {
        Document first = documentService.createDocument("Spring IOC", "IOC content");
        Document second = documentService.createDocument("Java Collections", "Collections content");

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void returnsExistingDocumentById() {
        Document created = documentService.createDocument("Spring IOC", "IOC content");

        Document found = documentService.getDocument(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getTitle()).isEqualTo("Spring IOC");
        assertThat(found.getContent()).isEqualTo("IOC content");
    }

    @Test
    void throwsWhenDocumentDoesNotExist() {
        assertThatThrownBy(() -> documentService.getDocument(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("文档（ID：999）不存在");
    }
}
