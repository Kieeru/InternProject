package com.xcy.internproject.controller;

import com.jayway.jsonpath.JsonPath;
import com.xcy.internproject.model.Document;
import com.xcy.internproject.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/cleanup.sql")
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
    void createsAndReturnsChineseDocument() throws Exception {
        String content = "第一行中文正文\n第二行保留换行";
        MvcResult createResult = mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MyBatis 中文文档",
                                  "content": "第一行中文正文\\n第二行保留换行"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("MyBatis 中文文档"))
                .andExpect(jsonPath("$.data.content").value(content))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist())
                .andReturn();

        Number documentId = JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/documents/{id}", documentId.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(documentId.longValue()))
                .andExpect(jsonPath("$.data.title").value("MyBatis 中文文档"))
                .andExpect(jsonPath("$.data.content").value(content))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
    }

    @Test
    void acceptsTitleWithExactlyTwoHundredCharacters() throws Exception {
        String title = "文".repeat(200);

        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "content": "标题边界测试"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value(title));
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

    @Test
    void listsDocumentsWithoutReturningContent() throws Exception {
        Document first = documentService.createDocument("Spring IOC", "不应出现在列表");
        Document second = documentService.createDocument("Spring MVC", "也不应出现在列表");

        mockMvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data.items[1].id").value(first.getId()))
                .andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].content").doesNotExist());
    }

    @Test
    void filtersAndReturnsSecondDocumentPage() throws Exception {
        Document first = documentService.createDocument("Spring IOC", "正文一");
        documentService.createDocument("Java 集合", "正文二");
        documentService.createDocument("Spring MVC", "正文三");

        mockMvc.perform(get("/documents")
                        .param("titlePrefix", "Spring")
                        .param("page", "2")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(first.getId()))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void returnsEmptyDocumentPage() throws Exception {
        mockMvc.perform(get("/documents").param("titlePrefix", "不存在"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void rejectsDocumentPageBelowOne() throws Exception {
        mockMvc.perform(get("/documents").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("页码不能小于1"));
    }
}
