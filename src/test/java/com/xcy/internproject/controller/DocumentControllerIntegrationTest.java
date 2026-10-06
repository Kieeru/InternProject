package com.xcy.internproject.controller;

import com.xcy.internproject.model.Document;
import com.xcy.internproject.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentService documentService;

    @Test
    void createsDocumentAndReturnsCreatedResponse() throws Exception {
        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Spring IOC",
                                  "content": "IOC manages object dependencies."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("Spring IOC"))
                .andExpect(jsonPath("$.data.content").value("IOC manages object dependencies."));
    }

    @Test
    void returnsExistingDocument() throws Exception {
        Document document = documentService.createDocument("Java Collections", "Collections content");

        mockMvc.perform(get("/documents/{id}", document.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(document.getId()))
                .andExpect(jsonPath("$.data.title").value("Java Collections"))
                .andExpect(jsonPath("$.data.content").value("Collections content"));
    }

    @Test
    void returnsNotFoundForMissingDocument() throws Exception {
        mockMvc.perform(get("/documents/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("文档（ID：999999）不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "content": "Document content"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("title: 标题不能为空"));
    }

    @Test
    void rejectsMissingContent() throws Exception {
        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Spring IOC"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("content: 正文不能为空"));
    }

    @Test
    void rejectsTitleLongerThanTwoHundredCharacters() throws Exception {
        String longTitle = "a".repeat(201);

        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "content": "Document content"
                                }
                                """.formatted(longTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("title: 标题长度不能超过200个字符"));
    }
}
