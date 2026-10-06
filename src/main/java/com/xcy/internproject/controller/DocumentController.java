package com.xcy.internproject.controller;

import com.xcy.internproject.dto.ApiResponse;
import com.xcy.internproject.dto.CreateDocumentRequest;
import com.xcy.internproject.model.Document;
import com.xcy.internproject.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
