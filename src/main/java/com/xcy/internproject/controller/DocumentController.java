package com.xcy.internproject.controller;

import com.xcy.internproject.dto.ApiResponse;
import com.xcy.internproject.dto.CreateDocumentRequest;
import com.xcy.internproject.dto.DocumentSummaryResponse;
import com.xcy.internproject.dto.PageResponse;
import com.xcy.internproject.dto.UpdateDocumentRequest;
import com.xcy.internproject.model.Document;
import com.xcy.internproject.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Document>> createDocument(
            @Valid @RequestBody CreateDocumentRequest request) {
        Document document = documentService.createDocument(request.getTitle(), request.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(document));
    }

    @GetMapping("/{id}")
    public ApiResponse<Document> getDocument(@PathVariable Long id) {
        return ApiResponse.success(documentService.getDocument(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Document> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request) {
        return ApiResponse.success(documentService.updateDocument(
                id, request.getTitle(), request.getContent()));
    }

    @GetMapping
    public ApiResponse<PageResponse<DocumentSummaryResponse>> listDocuments(
            @RequestParam(required = false) String titlePrefix,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(documentService.listDocuments(titlePrefix, page, size));
    }
}
