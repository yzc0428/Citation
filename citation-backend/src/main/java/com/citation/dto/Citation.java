package com.citation.dto;

import lombok.Data;

/**
 * 文献信息 DTO
 */
@Data
public class Citation {

    /**
     * 标题
     */
    private String title;

    /**
     * 作者列表
     */
    private String authors;

    /**
     * 发表年份
     */
    private String year;

    /**
     * 来源（期刊/会议名称）
     */
    private String source;

    /**
     * 摘要
     */
    private String abstractText;

    /**
     * 引用次数
     */
    private Integer citationCount;

    /**
     * 数据来源（google-scholar 或 cnki）
     */
    private String dataSource;

    /**
     * 相关度评分
     */
    private Double relevanceScore;

    /**
     * URL 链接
     */
    private String url;
}
