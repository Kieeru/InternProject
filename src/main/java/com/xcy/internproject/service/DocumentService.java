package com.xcy.internproject.service;

import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.model.Document;
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
}
