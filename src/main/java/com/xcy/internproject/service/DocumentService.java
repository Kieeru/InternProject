package com.xcy.internproject.service;

import com.xcy.internproject.dto.DocumentSummaryResponse;
import com.xcy.internproject.dto.PageResponse;
import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.model.Document;
import com.xcy.internproject.model.PageRequest;
import com.xcy.internproject.repository.DocumentPageQuery;
import com.xcy.internproject.repository.DocumentRepository;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Document createDocument(String title, String content) {
        return documentRepository.save(title, content);
    }

    public Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("文档", id));
    }

    public PageResponse<DocumentSummaryResponse> listDocuments(
            String titlePrefix, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        String normalizedPrefix = normalizeFilter(titlePrefix);
        var result = documentRepository.findPage(new DocumentPageQuery(
                normalizedPrefix,
                pageRequest.getOffset(),
                pageRequest.getSize()));
        var items = result.getItems().stream()
                .map(summary -> new DocumentSummaryResponse(
                        summary.id(), summary.title(), summary.createdAt()))
                .toList();
        return PageResponse.of(items, pageRequest.getPage(), pageRequest.getSize(), result.getTotal());
    }

    private static String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
