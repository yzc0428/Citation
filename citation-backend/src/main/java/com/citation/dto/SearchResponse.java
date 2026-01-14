package com.citation.dto;

import lombok.Data;
import java.util.List;

/**
 * 搜索响应 DTO
 */
@Data
public class SearchResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 提取的关键词
     */
    private List<String> keywords;

    /**
     * 文献列表
     */
    private List<Citation> citations;

    /**
     * 搜索耗时（毫秒）
     */
    private long duration;
}
