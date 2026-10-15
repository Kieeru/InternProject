package com.xcy.internproject.service;

import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.exception.InvalidPaginationException;
import com.xcy.internproject.dto.PageResponse;
import com.xcy.internproject.dto.DocumentSummaryResponse;
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

    @Test
    void listsDocumentsWithOneBasedPaginationAndTotalPages() {
        documentService.createDocument("Spring IOC", "正文一");
        documentService.createDocument("Java 集合", "正文二");
        Document newest = documentService.createDocument("Spring MVC", "正文三");

        PageResponse<DocumentSummaryResponse> response =
                documentService.listDocuments("Spring", 1, 1);

        assertThat(response.getItems()).containsExactly(
                new DocumentSummaryResponse(newest.getId(), "Spring MVC",
                        response.getItems().getFirst().createdAt()));
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(1);
        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(2);
    }

    @Test
    void normalizesBlankDocumentPrefixToNoFilter() {
        documentService.createDocument("Spring IOC", "正文一");
        documentService.createDocument("Java 集合", "正文二");

        PageResponse<DocumentSummaryResponse> response =
                documentService.listDocuments("   ", 1, 20);

        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getItems()).hasSize(2);
    }

    @Test
    void rejectsInvalidDocumentPage() {
        assertThatThrownBy(() -> documentService.listDocuments(null, 0, 20))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("页码不能小于1");
    }
}
