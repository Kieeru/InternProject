package com.xcy.internproject.dto;

import com.xcy.internproject.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateTicketRequest {

    @NotBlank(message = "工单标题不能为空")
    @Size(max = 200, message = "工单标题长度不能超过200个字符")
    private String title;

    @NotBlank(message = "工单描述不能为空")
    @Utf8ByteLength(max = 65_535, message = "工单描述不能超过65535个UTF-8字节")
    private String description;


    public CreateTicketRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
