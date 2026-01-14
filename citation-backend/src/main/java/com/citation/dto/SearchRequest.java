package com.citation.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 搜索请求 DTO
 */
@Data
public class SearchRequest {

    /**
     * 用户输入的搜索描述（中文）
     */
    @NotBlank(message = "搜索内容不能为空")
    @Size(max = 500, message = "搜索内容不能超过500个字符")
    private String query;
}
