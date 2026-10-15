package com.xcy.internproject.controller;

import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/cleanup.sql")
class TicketControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketService ticketService;

    @Test
    void createsTicketWithPendingStatus() throws Exception {
        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "无法登录系统",
                                  "description": "输入正确密码后仍然提示登录失败"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("无法登录系统"))
                .andExpect(jsonPath("$.data.description").value("输入正确密码后仍然提示登录失败"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void rejectsBlankTicketTitle() throws Exception {
        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "description": "无法登录"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("title: 工单标题不能为空"));
    }

    @Test
    void rejectsMissingTicketDescription() throws Exception {
        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "无法登录系统"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("description: 工单描述不能为空"));
    }

    @Test
    void rejectsTicketTitleLongerThanDatabaseColumn() throws Exception {
        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "标题超过数据库字段限制"
                                }
                                """.formatted("工".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("title: 工单标题长度不能超过200个字符"));
    }

    @Test
    void rejectsTicketDescriptionExceedingTextUtf8ByteLimit() throws Exception {
        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "描述过长",
                                  "description": "%s"
                                }
                                """.formatted("😀".repeat(16_384))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("description: 工单描述不能超过65535个UTF-8字节"));
    }

    @Test
    void returnsExistingTicket() throws Exception {
        Ticket ticket=ticketService.createTicket("工单1","这是工单1的内容");
        mockMvc.perform(get("/tickets/{id}",ticket.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(ticket.getId()))
                .andExpect(jsonPath("$.data.title").value("工单1"))
                .andExpect(jsonPath("$.data.description").value("这是工单1的内容"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void returnsNotFoundForMissingTicket() throws Exception{
        mockMvc.perform(get("/tickets/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("工单（ID：999999）不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void updatesTicketToNextStatus() throws Exception {
        Ticket ticket = ticketService.createTicket("无法登录", "用户无法登录系统");

        mockMvc.perform(put("/tickets/{id}/status", ticket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PROCESSING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.id").value(ticket.getId()))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void rejectsInvalidStatusTransition() throws Exception {
        Ticket ticket = ticketService.createTicket("无法登录", "用户无法登录系统");

        mockMvc.perform(put("/tickets/{id}/status", ticket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "RESOLVED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message")
                        .value("不允许工单状态从 PENDING 变更为 RESOLVED"));
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingTicket() throws Exception {
        mockMvc.perform(put("/tickets/{id}/status", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PROCESSING"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("工单（ID：999999）不存在"));
    }

    @Test
    void rejectsMissingTargetStatus() throws Exception {
        Ticket ticket = ticketService.createTicket("无法登录", "用户无法登录系统");

        mockMvc.perform(put("/tickets/{id}/status", ticket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("status: 目标状态不能为空"));
    }

    @Test
    void rejectsUnsupportedTargetStatus() throws Exception {
        Ticket ticket = ticketService.createTicket("无法登录", "用户无法登录系统");

        mockMvc.perform(put("/tickets/{id}/status", ticket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("请求体格式错误或包含不支持的值"));
    }

    @Test
    void listsTicketsWithoutReturningDescription() throws Exception {
        Ticket first = ticketService.createTicket("登录故障", "不应出现在列表");
        Ticket second = ticketService.createTicket("网络故障", "也不应出现在列表");

        mockMvc.perform(get("/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data.items[1].id").value(first.getId()))
                .andExpect(jsonPath("$.data.items[0].description").doesNotExist());
    }

    @Test
    void filtersTicketsByStatusAndKeyword() throws Exception {
        Ticket matching = ticketService.createTicket("登录故障", "描述一");
        ticketService.updateStatus(matching.getId(), TicketStatus.PROCESSING);
        ticketService.createTicket("登录咨询", "描述二");
        Ticket other = ticketService.createTicket("网络故障", "描述三");
        ticketService.updateStatus(other.getId(), TicketStatus.PROCESSING);

        mockMvc.perform(get("/tickets")
                        .param("status", "PROCESSING")
                        .param("keyword", "登录")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(matching.getId()))
                .andExpect(jsonPath("$.data.items[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void returnsSecondTicketPage() throws Exception {
        ticketService.createTicket("第一页末尾", "描述一");
        Ticket second = ticketService.createTicket("第二页目标", "描述二");
        ticketService.createTicket("排序第一", "描述三");

        mockMvc.perform(get("/tickets")
                        .param("page", "2")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    @Test
    void returnsEmptyTicketPage() throws Exception {
        mockMvc.perform(get("/tickets").param("keyword", "不存在"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void rejectsTicketPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/tickets").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("每页条数必须在1到100之间"));
    }

    @Test
    void rejectsUnknownTicketStatusQueryParameter() throws Exception {
        mockMvc.perform(get("/tickets").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.message").value("查询参数格式错误或包含不支持的值"));
    }
}
