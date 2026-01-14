package com.citation.config;

import com.google.common.util.concurrent.RateLimiter;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 爬虫配置类
 * 用于配置知网爬虫的各项参数
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "crawler.cnki")
public class CrawlerConfig {

    /**
     * 是否启用爬虫模式
     * false: 使用Mock数据
     * true: 使用真实爬虫
     */
    private boolean enabled = false;

    /**
     * 连接超时时间（毫秒）
     * 默认15秒
     */
    private int timeout = 15000;

    /**
     * 限流速率（QPS）
     * 0.4 表示每2.5秒一个请求
     */
    private double rateLimit = 0.4;

    /**
     * User-Agent
     * 用于标识爬虫身份，遵守网站规则
     */
    private String userAgent = "Mozilla/5.0 (Educational Purpose; Citation Research System)";

    /**
     * 创建RateLimiter Bean
     * 用于限制请求频率
     */
    @Bean
    public RateLimiter cnkiRateLimiter() {
        return RateLimiter.create(rateLimit);
    }
}
